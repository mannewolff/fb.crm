package org.mwolff.fbcrm.rechnung;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.angebot.web.AngebotResponse;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.PasswordHasher;
import org.mwolff.fbcrm.auth.domain.Role;
import org.mwolff.fbcrm.common.Geschaeftszone;
import org.mwolff.fbcrm.rechnung.web.RechnungResponse;
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
 * Das Herunterladen des Dokuments einer gestellten Rechnung ueber HTTP, gegen eine echte
 * PostgreSQL- und MinIO-Instanz (#160, Kriterium 24; Plan #169, E11).
 *
 * <p>Zwei Dinge sind nur hier zu sehen. Erstens die <b>Festschreibung</b>: Das Dokument liegt im
 * Objektspeicher, und zwei Abrufe liefern Byte fuer Byte dasselbe — auch nachdem sich alles
 * geaendert hat, woraus der Beleg entstanden ist. Haette der Weg nachgedruckt statt gelesen, fiele
 * genau hier der zweite Abruf anders aus. Zweitens die <b>Kopfzeilen</b>: {@code attachment},
 * {@code application/pdf}, die Sandbox-Regel und die beiden Vorgaben aus Spring Security kommen
 * zusammen erst in einer echten Antwort an.
 *
 * <p>Der Nachweis der Zugangsregel steht hier und nicht nur in {@code AccessRuleIT}: Jeder neue
 * Pfad unter {@code /api} ist ohne Sitzung verschlossen.
 */
class RechnungDokumentIT extends AbstractIntegrationTest {

  private static final String MAIL = "manne@example.org";
  private static final String PASSWORT = "richtiges-passwort";
  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");

  private static final String FIRMENNAME = "Adler AG";
  private static final String ZEITRAUM = "September 2026";
  private static final String STUNDENSATZ = "100.00";

  private final TestRestTemplate rest;
  private final AccountRepository accounts;
  private final PasswordHasher hasher;
  private final JdbcTemplate jdbc;

  /** Das Rechnungsdatum und damit das Zaehlerjahr der Nummer — siehe {@code RechnungStellenIT}. */
  private final LocalDate heute = LocalDate.now(Geschaeftszone.ZONE);

  private HttpHeaders sitzung = new HttpHeaders();
  private long firmaId;

  @Autowired
  RechnungDokumentIT(
      final TestRestTemplate rest,
      final AccountRepository accounts,
      final PasswordHasher hasher,
      final JdbcTemplate jdbc) {
    this.rest = rest;
    this.accounts = accounts;
    this.hasher = hasher;
    this.jdbc = jdbc;
  }

  @BeforeEach
  void leereDenBestandUndMeldeAn() {
    jdbc.execute(
        "TRUNCATE rechnung_position, rechnung, angebot_position, angebot, ansprechpartner, firma"
            + " RESTART IDENTITY CASCADE");
    jdbc.execute("DELETE FROM rechnung_nummernkreis");
    jdbc.execute("DELETE FROM rechnung_einstellungen");
    jdbc.execute("INSERT INTO rechnung_einstellungen DEFAULT VALUES");
    jdbc.execute("TRUNCATE outbox_message, password_reset_token, account RESTART IDENTITY CASCADE");
    accounts.save(
        new Account(null, MAIL, "Manne", hasher.hash(PASSWORT), Role.ADMIN, 0, ANGELEGT, ANGELEGT));
    sitzung = angemeldeterKopf();
    eigeneAngabenSetzen("75/123/45678", "DE02 1203 0000 0000 2020 51");
    firmaId = firmaAnlegen(FIRMENNAME, "Hauptstrasse 1", "28195", "Bremen");
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
      final String pfad,
      final HttpMethod methode,
      final @Nullable Object rumpf,
      final Class<T> typ) {
    return rest.exchange(pfad, methode, new HttpEntity<>(rumpf, sitzung), typ);
  }

  private void eigeneAngabenSetzen(final String steuernummer, final String bankverbindung) {
    final Map<String, Object> rumpf = new LinkedHashMap<>();
    rumpf.put("name", "Manfred Wolff");
    rumpf.put("berufsbezeichnung", "Softwarearchitekt");
    rumpf.put("strasse", "Am Deich 2");
    rumpf.put("plz", "28199");
    rumpf.put("ort", "Bremen");
    rumpf.put("land", "Deutschland");
    rumpf.put("email", "post@example.org");
    rumpf.put("telefon", "0421 123456");
    rumpf.put("webadresse", "https://example.org");
    rumpf.put("steuernummer", steuernummer);
    rumpf.put("umsatzsteuerId", "DE123456789");
    rumpf.put("bankverbindung", bankverbindung);
    ruf("/api/eigene-angaben", HttpMethod.PUT, rumpf, Void.class);
  }

  private long firmaAnlegen(
      final String name, final String strasse, final String plz, final String ort) {
    ruf("/api/firmen", HttpMethod.POST, firmenrumpf(name, strasse, plz, ort), String.class);
    return Objects.requireNonNull(
            jdbc.queryForObject("SELECT id FROM firma WHERE name = ?", Long.class, name))
        .longValue();
  }

  private void firmaSetzen(
      final String name, final String strasse, final String plz, final String ort) {
    ruf("/api/firmen/" + firmaId, HttpMethod.PUT, firmenrumpf(name, strasse, plz, ort), Void.class);
  }

  private static Map<String, Object> firmenrumpf(
      final String name, final String strasse, final String plz, final String ort) {
    final Map<String, Object> rumpf = new LinkedHashMap<>();
    rumpf.put("name", name);
    rumpf.put("strasse", strasse);
    rumpf.put("plz", plz);
    rumpf.put("ort", ort);
    rumpf.put("land", "Deutschland");
    rumpf.put("steuernummer", null);
    rumpf.put("umsatzsteuerId", null);
    return rumpf;
  }

  private void einstellungenSetzen(final int naechsteNummer, final String steuersatz) {
    final Map<String, Object> rumpf = new LinkedHashMap<>();
    rumpf.put("nummerMuster", "{NNNN}-{JJJJ}");
    rumpf.put("naechsteNummer", Integer.valueOf(naechsteNummer));
    rumpf.put("steuersatz", steuersatz);
    rumpf.put("zahlungszielTage", Integer.valueOf(30));
    ruf("/api/rechnung/einstellungen", HttpMethod.PUT, rumpf, Void.class);
  }

  /** Die Nummer, die das Standardmuster zur laufenden Nummer bildet. */
  private String nummer(final int laufend) {
    return "%04d-%d".formatted(Integer.valueOf(laufend), Integer.valueOf(heute.getYear()));
  }

  /** Ein bestelltes Angebot mit einer Position ueber die angegebene Menge Beratung. */
  private long bestelltesAngebot(final String menge) {
    final long angebotId =
        Objects.requireNonNull(
                ruf(
                        "/api/firmen/" + firmaId + "/angebote",
                        HttpMethod.POST,
                        null,
                        AngebotResponse.class)
                    .getBody())
            .id();
    positionSetzen(angebotId, null, "Beratung", menge, STUNDENSATZ);
    ruf("/api/angebote/" + angebotId + "/status/weiter", HttpMethod.POST, null, String.class);
    ruf("/api/angebote/" + angebotId + "/status/weiter", HttpMethod.POST, null, String.class);
    return angebotId;
  }

  private void positionSetzen(
      final long angebotId,
      final @Nullable Long positionId,
      final String bezeichnung,
      final String menge,
      final String einzelpreis) {
    final Map<String, Object> position = new LinkedHashMap<>();
    position.put("id", positionId);
    position.put("bezeichnung", bezeichnung);
    position.put("abrechnungsmodus", "AUFWAND");
    position.put("menge", menge);
    position.put("einheit", "STUNDE");
    position.put("einzelpreis", einzelpreis);
    final Map<String, Object> rumpf = new LinkedHashMap<>();
    rumpf.put("angebotDatum", heute.toString());
    rumpf.put("ansprechpartnerId", null);
    rumpf.put("beschreibung", "Neugestaltung der Website");
    rumpf.put("positionen", List.of(position));
    ruf("/api/angebote/" + angebotId, HttpMethod.PUT, rumpf, AngebotResponse.class);
  }

  /** Ein Entwurf zum Angebot, auf die angegebene Menge gesetzt. */
  private RechnungResponse entwurfUeber(final long angebotId, final String menge) {
    final RechnungResponse entwurf =
        Objects.requireNonNull(
            ruf(
                    "/api/angebote/" + angebotId + "/rechnungen",
                    HttpMethod.POST,
                    null,
                    RechnungResponse.class)
                .getBody());
    final Map<String, Object> position = new LinkedHashMap<>();
    position.put(
        "angebotPositionId", Long.valueOf(entwurf.zeilen().getFirst().angebotPositionId()));
    position.put("bezeichnung", "Beratung");
    position.put("menge", menge);
    final Map<String, Object> rumpf = new LinkedHashMap<>();
    rumpf.put("rechnungDatum", heute.toString());
    rumpf.put("leistungszeitraum", ZEITRAUM);
    rumpf.put("positionen", List.of(position));
    return Objects.requireNonNull(
        ruf("/api/rechnungen/" + entwurf.id(), HttpMethod.PUT, rumpf, RechnungResponse.class)
            .getBody());
  }

  private RechnungResponse gestellt(final long rechnungId) {
    final ResponseEntity<RechnungResponse> antwort =
        ruf(
            "/api/rechnungen/" + rechnungId + "/stellen",
            HttpMethod.POST,
            null,
            RechnungResponse.class);
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
    return Objects.requireNonNull(antwort.getBody());
  }

  private ResponseEntity<byte[]> dokument(final long rechnungId) {
    return ruf("/api/rechnungen/" + rechnungId + "/dokument", HttpMethod.GET, null, byte[].class);
  }

  /** Eine gestellte Rechnung ueber 80 der 160 angebotenen Stunden. */
  private long gestellteRechnung() {
    return gestellt(entwurfUeber(bestelltesAngebot("160.00"), "80.00").id()).id();
  }

  @Test
  void dokument_thenTheAnswerCarriesThePdfWithAllStrictHeaders() {
    // Given — E11, E5: attachment statt Anzeige, nosniff gegen das Erraten des Typs, sandbox als
    // letzte Schranke, DENY gegen das Einbetten in einen Rahmen.
    final long rechnungId = gestellteRechnung();

    // When
    final ResponseEntity<byte[]> antwort = dokument(rechnungId);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
    final HttpHeaders kopf = antwort.getHeaders();
    assertThat(kopf.getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
    assertThat(kopf.getFirst(HttpHeaders.CONTENT_DISPOSITION))
        .startsWith("attachment")
        .contains("filename=\"Rechnung-" + nummer(1) + ".pdf\"");
    assertThat(kopf.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
    assertThat(kopf.getFirst("Content-Security-Policy")).isEqualTo("sandbox");
    assertThat(kopf.getFirst("X-Frame-Options")).isEqualTo("DENY");
    assertThat(Objects.requireNonNull(antwort.getBody()))
        .isNotEmpty()
        .hasSize((int) kopf.getContentLength());
  }

  @Test
  void dokument_afterEverythingAroundItChanged_thenBothAbrufeAreByteForByteTheSame() {
    // Given — der erste Abruf, bevor sich etwas aendert.
    final long angebotId = bestelltesAngebot("160.00");
    final RechnungResponse entwurf = entwurfUeber(angebotId, "80.00");
    final long positionId = entwurf.zeilen().getFirst().angebotPositionId();
    final long rechnungId = gestellt(entwurf.id()).id();
    final byte[] erster = Objects.requireNonNull(dokument(rechnungId).getBody());

    // When — danach aendert sich alles, woraus der Beleg entstanden ist (Kriterium 14).
    firmaSetzen("Adler Holding SE", "Neue Strasse 9", "20095", "Hamburg");
    positionSetzen(
        angebotId, Long.valueOf(positionId), "Beratung, neu benannt", "160.00", "120.00");
    eigeneAngabenSetzen("99/999/99999", "DE99 9999 9999 9999 9999 99");
    einstellungenSetzen(2, "7.00");

    // Then — gelesen und nicht nachgedruckt: derselbe Beleg, Byte fuer Byte.
    final ResponseEntity<byte[]> zweiter = dokument(rechnungId);
    assertThat(zweiter.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(zweiter.getBody()).isEqualTo(erster);
    assertThat(zweiter.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
        .contains("filename=\"Rechnung-" + nummer(1) + ".pdf\"");
  }

  @Test
  void dokument_atAnEntwurf_thenConflict() {
    // Given — ein Entwurf traegt kein Dokument; es entsteht erst mit dem Stellen.
    final RechnungResponse entwurf = entwurfUeber(bestelltesAngebot("160.00"), "80.00");

    // When / Then
    assertThat(dokument(entwurf.id()).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void dokument_withAnUnknownRechnung_thenNotFound() {
    // When / Then
    assertThat(dokument(999_999L).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void dokument_withoutASitzung_thenUnauthorized() {
    // Given — derselbe Weg, nur ohne das Sitzungs-Cookie.
    final long rechnungId = gestellteRechnung();
    sitzung = new HttpHeaders();

    // When / Then
    assertThat(dokument(rechnungId).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
