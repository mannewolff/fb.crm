package org.mwolff.fbcrm.angebot;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.angebot.domain.DokumentSpeicher;
import org.mwolff.fbcrm.angebot.web.AngebotResponse;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.PasswordHasher;
import org.mwolff.fbcrm.auth.domain.Role;
import org.mwolff.fbcrm.common.Geschaeftszone;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Das Versenden und das Wiederlesen des Belegs ueber HTTP, gegen echtes PostgreSQL und echtes MinIO
 * (Kriterien 10 bis 16).
 *
 * <p>Drei Zusagen sind nur hier pruefbar, weil sie an der echten Transaktion, am echten Schema und
 * am echten Objektspeicher haengen.
 *
 * <ul>
 *   <li>Kriterium 14: Ausgeliefert wird <b>byteweise</b> das beim Versenden abgelegte Objekt. Der
 *       Test holt den Schluessel aus der Spalte und vergleicht den Rumpf mit dem, was im Speicher
 *       liegt — eine Neuberechnung aus heutigen Daten faellt damit auf.
 *   <li>Kriterium 15: Jeder Pflichtinhalt ist aus dem ausgelieferten PDF wieder herauszulesen,
 *       gelesen mit demselben Werkzeug, mit dem ein Aussenstehender nachsehen wuerde.
 *   <li>Kriterium 13: Das versendete Angebot laesst sich nicht mehr fortschreiben.
 * </ul>
 *
 * <p>Das Jahr der Nummer kommt aus der Geschaeftszone (E12) und damit aus der laufenden Uhr — der
 * Test rechnet es selbst aus, statt eine Jahreszahl festzuschreiben, die im naechsten Januar falsch
 * waere.
 */
class AngebotPdfIT extends AbstractIntegrationTest {

  private static final String MAIL = "manne@example.org";
  private static final String PASSWORT = "richtiges-passwort";
  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");

  private static final String EIGENER_NAME = "Manfred Wolff";
  private static final String BANKVERBINDUNG = "Sparkasse, IBAN DE02 1203 0000 0000 2020 51";
  private static final String BEDINGUNGEN = "Zahlbar innerhalb von 14 Tagen ohne Abzug.";
  private static final String BESCHREIBUNG = "Neugestaltung der Website";
  private static final String NETTO_HINWEIS = "Alle Beträge netto, zzgl. gesetzlicher Umsatzsteuer";

  private final TestRestTemplate rest;
  private final AccountRepository accounts;
  private final PasswordHasher hasher;
  private final DokumentSpeicher speicher;
  private final JdbcTemplate jdbc;
  private final Clock clock;

  private HttpHeaders sitzung = new HttpHeaders();
  private long firmaId;
  private long ansprechpartnerId;

  @Autowired
  AngebotPdfIT(
      final TestRestTemplate rest,
      final AccountRepository accounts,
      final PasswordHasher hasher,
      final DokumentSpeicher speicher,
      final JdbcTemplate jdbc,
      final Clock clock) {
    this.rest = rest;
    this.accounts = accounts;
    this.hasher = hasher;
    this.speicher = speicher;
    this.jdbc = jdbc;
    this.clock = clock;
  }

  @BeforeEach
  void legeEineVersandfaehigeFirmaAn() {
    jdbc.execute(
        "TRUNCATE angebot_position, angebot, ansprechpartner, firma RESTART IDENTITY CASCADE");
    jdbc.execute("DELETE FROM angebot_nummernkreis");
    jdbc.update(
        "UPDATE eigene_angaben SET name = ?, strasse = ?, plz = ?, ort = ?, land = ?, email = ?,"
            + " telefon = ?, steuernummer = ?, umsatzsteuer_id = ?, bankverbindung = ?,"
            + " zahlungsbedingungen = ?",
        EIGENER_NAME,
        "Am Deich 2",
        "28199",
        "Hansestadt",
        "Deutschland",
        "manne@example.org",
        "0421 123456",
        "12/345/67890",
        "DE123456789",
        BANKVERBINDUNG,
        BEDINGUNGEN);
    jdbc.update(
        "INSERT INTO firma (name, strasse, plz, ort, land) VALUES (?, ?, ?, ?, ?)",
        "Adler AG",
        "Hauptstrasse 1",
        "28195",
        "Bremen",
        "Deutschland");
    firmaId = einzigeId("SELECT id FROM firma");
    jdbc.update(
        "INSERT INTO ansprechpartner (firma_id, vorname, nachname) VALUES (?, ?, ?)",
        Long.valueOf(firmaId),
        "Eva",
        "Adler");
    ansprechpartnerId = einzigeId("SELECT id FROM ansprechpartner");
    jdbc.execute("TRUNCATE outbox_message, password_reset_token, account RESTART IDENTITY CASCADE");
    accounts.save(
        new Account(null, MAIL, "Manne", hasher.hash(PASSWORT), Role.ADMIN, 0, ANGELEGT, ANGELEGT));
    sitzung = angemeldeterKopf();
  }

  private long einzigeId(final String abfrage) {
    return Objects.requireNonNull(jdbc.queryForObject(abfrage, Long.class)).longValue();
  }

  private HttpHeaders angemeldeterKopf() {
    final ResponseEntity<String> anmeldung =
        rest.postForEntity(
            "/api/auth/login", Map.of("email", MAIL, "password", PASSWORT), String.class);
    final String gesetzt = String.valueOf(anmeldung.getHeaders().getFirst(HttpHeaders.SET_COOKIE));
    final HttpHeaders kopf = new HttpHeaders();
    kopf.add(HttpHeaders.COOKIE, gesetzt.substring(0, gesetzt.indexOf(';')));
    return kopf;
  }

  private <T> ResponseEntity<T> ruf(
      final String pfad, final HttpMethod methode, final Object rumpf, final Class<T> typ) {
    return rest.exchange(pfad, methode, new HttpEntity<>(rumpf, sitzung), typ);
  }

  private static Map<String, Object> entwurfsrumpf(final LocalDate gueltigBis) {
    final Map<String, Object> position = new LinkedHashMap<>();
    position.put("bezeichnung", "Konzeption");
    position.put("abrechnungsmodus", "AUFWAND");
    position.put("menge", "2.50");
    position.put("einheit", "PERSONENTAG");
    position.put("einzelpreis", "1000.01");
    final Map<String, Object> felder = new LinkedHashMap<>();
    felder.put("gueltigBis", gueltigBis.toString());
    felder.put("leistungsbeschreibung", BESCHREIBUNG);
    felder.put("zahlungsbedingungen", BEDINGUNGEN);
    felder.put("positionen", List.of(position));
    return felder;
  }

  /** Ein fortgeschriebener Entwurf an Firma und Ansprechpartner, versandfaehig (Kriterium 12). */
  private long versandfaehigerEntwurf() {
    final long angebotId =
        Objects.requireNonNull(
                ruf(
                        "/api/firmen/" + firmaId + "/angebote",
                        HttpMethod.POST,
                        Map.of("ansprechpartnerId", Long.valueOf(ansprechpartnerId)),
                        AngebotResponse.class)
                    .getBody())
            .id();
    ruf(
        "/api/angebote/" + angebotId,
        HttpMethod.PUT,
        entwurfsrumpf(heute().plusDays(30)),
        AngebotResponse.class);
    return angebotId;
  }

  private LocalDate heute() {
    return LocalDate.now(clock.withZone(Geschaeftszone.ZONE));
  }

  private AngebotResponse versende(final long angebotId) {
    return Objects.requireNonNull(
        ruf(
                "/api/angebote/" + angebotId + "/versenden",
                HttpMethod.POST,
                null,
                AngebotResponse.class)
            .getBody());
  }

  private ResponseEntity<byte[]> pdf(final long angebotId) {
    return ruf("/api/angebote/" + angebotId + "/pdf", HttpMethod.GET, null, byte[].class);
  }

  private String pdfSchluessel(final long angebotId) {
    return Objects.requireNonNull(
        jdbc.queryForObject(
            "SELECT pdf_schluessel FROM angebot WHERE id = ?",
            String.class,
            Long.valueOf(angebotId)));
  }

  private String zustand(final long angebotId) {
    return Objects.requireNonNull(
        jdbc.queryForObject(
            "SELECT zustand FROM angebot WHERE id = ?", String.class, Long.valueOf(angebotId)));
  }

  private static String ausgelesen(final byte[] pdf) throws IOException {
    try (PDDocument dokument = Loader.loadPDF(pdf)) {
      return new PDFTextStripper().getText(dokument);
    }
  }

  @Test
  void versenden_thenTheAngebotCarriesTheFirstNumberOfTheYear() {
    // Given — Kriterium 11: die laufende Nummer beginnt in jedem Kalenderjahr wieder bei 001,
    // gezaehlt in der Geschaeftszone (E12).
    final long angebotId = versandfaehigerEntwurf();

    // When
    final AngebotResponse versendet = versende(angebotId);

    // Then
    assertThat(versendet.nummer())
        .isEqualTo("A-%d-001".formatted(Integer.valueOf(heute().getYear())));
    assertThat(versendet.stand().name()).isEqualTo("VERSENDET");
    assertThat(versendet.versendetAm()).isNotNull();
  }

  @Test
  void pdf_thenAnswersWithExactlyTheObjectThatWasStoredWhileSending() {
    // Given — Kriterium 14: keine Neuberechnung aus heutigen Daten.
    final long angebotId = versandfaehigerEntwurf();
    versende(angebotId);
    final byte[] abgelegt = speicher.lies(pdfSchluessel(angebotId));

    // When
    final ResponseEntity<byte[]> antwort = pdf(angebotId);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(antwort.getBody()).isEqualTo(abgelegt);
  }

  @Test
  void pdf_thenOffersTheBelegForViewing() {
    // Given — E17: Kriterium 14 sagt „oeffnen", nicht „herunterladen".
    final long angebotId = versandfaehigerEntwurf();
    final String nummer = Objects.requireNonNull(versende(angebotId).nummer());

    // When
    final ResponseEntity<byte[]> antwort = pdf(angebotId);

    // Then
    assertThat(antwort.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
        .isEqualTo("inline; filename=\"%s.pdf\"".formatted(nummer));
    assertThat(antwort.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
  }

  @Test
  void pdf_thenEveryMandatoryContentOfCriterion15CanBeReadBack() throws IOException {
    // Given — Kriterium 15.
    final long angebotId = versandfaehigerEntwurf();
    final AngebotResponse versendet = versende(angebotId);

    // When
    final String text = ausgelesen(Objects.requireNonNull(pdf(angebotId).getBody()));

    // Then — Absender, Empfaenger, Nummer, Datum, Gueltigkeit, Leistungsbeschreibung, Position mit
    // Menge, Einheit, Einzelpreis und Betrag, Summe und Zahlungsbedingungen.
    assertThat(text)
        .contains(EIGENER_NAME)
        .contains("Adler AG")
        .contains("Eva Adler")
        .contains("Hauptstrasse 1")
        .contains("28195 Bremen")
        .contains("Angebot " + versendet.nummer())
        .contains("Angebotsdatum: " + datum(versendet.angebotDatum()))
        .contains("Gültig bis: " + datum(versendet.gueltigBis()))
        .contains(BESCHREIBUNG)
        .contains("Konzeption")
        .contains("2,5")
        .contains("Personentag")
        .contains("1.000,01 €")
        .contains("2.500,03 €")
        .contains("Angebotssumme")
        .contains("Zahlungsbedingungen")
        .contains(BEDINGUNGEN)
        .contains(BANKVERBINDUNG)
        .contains(NETTO_HINWEIS);
  }

  private static String datum(final LocalDate tag) {
    return "%02d.%02d.%d"
        .formatted(
            Integer.valueOf(tag.getDayOfMonth()),
            Integer.valueOf(tag.getMonthValue()),
            Integer.valueOf(tag.getYear()));
  }

  @Test
  void versenden_aSecondTime_thenLeavesTheFirstAngebotAsItWas() {
    // Given — zwei Angebote an dieselbe Firma sind unabhaengig (Issue #126).
    final long erstes = versandfaehigerEntwurf();
    versende(erstes);
    final long zweites = versandfaehigerEntwurf();

    // When
    versende(zweites);

    // Then
    assertThat(zustand(erstes)).isEqualTo("VERSENDET");
    assertThat(zustand(zweites)).isEqualTo("VERSENDET");
  }

  @Test
  void aendern_afterVersenden_thenAnswers409() {
    // Given — Kriterium 13: ein versendetes Angebot ist unveraenderlich (R7).
    final long angebotId = versandfaehigerEntwurf();
    versende(angebotId);

    // When
    final ResponseEntity<String> antwort =
        ruf(
            "/api/angebote/" + angebotId,
            HttpMethod.PUT,
            entwurfsrumpf(heute().plusDays(60)),
            String.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void versenden_withoutAnyPosition_thenAnswers409AndBurnsNoNumber() {
    // Given — Kriterium 12: ein abgewiesener Versuch verbraucht keine Angebotsnummer.
    final long angebotId =
        Objects.requireNonNull(
                ruf(
                        "/api/firmen/" + firmaId + "/angebote",
                        HttpMethod.POST,
                        Map.of("ansprechpartnerId", Long.valueOf(ansprechpartnerId)),
                        AngebotResponse.class)
                    .getBody())
            .id();

    // When
    final ResponseEntity<String> abgewiesen =
        ruf("/api/angebote/" + angebotId + "/versenden", HttpMethod.POST, null, String.class);
    ruf(
        "/api/angebote/" + angebotId,
        HttpMethod.PUT,
        entwurfsrumpf(heute().plusDays(30)),
        AngebotResponse.class);
    final AngebotResponse versendet = versende(angebotId);

    // Then — der nachgeholte Versand bekommt die erste Nummer des Jahres.
    assertThat(abgewiesen.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(versendet.nummer())
        .isEqualTo("A-%d-001".formatted(Integer.valueOf(heute().getYear())));
  }

  @Test
  void belegwege_withoutASession_thenUnauthorized() {
    // Given — jeder neue Pfad unter /api ist ohne Sitzung verschlossen.
    sitzung = new HttpHeaders();

    // When / Then
    assertThat(
            ruf("/api/angebote/1/versenden", HttpMethod.POST, null, String.class).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(ruf("/api/angebote/1/pdf", HttpMethod.GET, null, String.class).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
