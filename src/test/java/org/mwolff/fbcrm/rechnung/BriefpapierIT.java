package org.mwolff.fbcrm.rechnung;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.Stand;
import org.mwolff.fbcrm.angebot.web.AngebotResponse;
import org.mwolff.fbcrm.auth.Anmeldung;
import org.mwolff.fbcrm.auth.Konten;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.SecretHasher;
import org.mwolff.fbcrm.common.Geschaeftszone;
import org.mwolff.fbcrm.rechnung.application.BriefpapierNichtAnnehmbar;
import org.mwolff.fbcrm.rechnung.domain.Briefpapierspeicher;
import org.mwolff.fbcrm.rechnung.domain.DokumentSpeicherAusfall;
import org.mwolff.fbcrm.rechnung.web.BriefbogenResponse;
import org.mwolff.fbcrm.rechnung.web.RechnungResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Stand und Pflege des Briefbogens, das Briefpapier selbst und der Probedruck ueber HTTP, mit
 * Sitzung und gegen eine echte PostgreSQL- und MinIO-Instanz (fachliche Quelle #361, Kriterien 1,
 * 2, 9, 13, 15 und 16; Plan #363, E1, E4, E6, E14 und E15).
 *
 * <p>Eine frische Instanz liefert die Vorgaben der Migration und beide Schalter an, ohne einen
 * Schluessel des Objektspeichers; ein {@code PUT} steht anschliessend wirklich im Bestand, und ein
 * abgewiesener laesst die bisherige Zeile unberuehrt.
 *
 * <p>Nur hier zu sehen: dass ein ersetztes oder entferntes Briefpapier nach dem Commit wirklich aus
 * dem Objektspeicher verschwunden ist und das neue unter einem eigenen Schluessel liegt. Die PDFs
 * entstehen mit PDFBox, damit der echte Pruefer sie annimmt oder abweist.
 *
 * <p>Ebenso nur hier: dass ein ausgetauschtes Briefpapier eine schon gestellte Rechnung nicht
 * beruehrt und erst die naechste traegt (Kriterium 10).
 *
 * <p>Jeder schreibende Weg legt den Stand der Zeile vor, wie die Maske (Plan #415, E2, E11). Hier
 * zu sehen: dass ein {@code PUT} auf dem Stand vor einem Hochladen abgewiesen wird, obwohl beide
 * Wege in getrennte Spalten schreiben.
 *
 * <p>Die eine Zeile wird vor jeder Methode auf den Stand der Migration zurueckgesetzt; die
 * Datenbank der Suite ist geteilt.
 */
class BriefpapierIT extends AbstractIntegrationTest {

  private static final String PFAD = "/api/briefpapier";
  private static final String MAIL = "manne@example.org";
  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");

  /** A4 quer: die Masse von A4, Breite und Hoehe vertauscht. */
  private static final PDRectangle A4_QUER = new PDRectangle(842, 595);

  private final TestRestTemplate rest;
  private final AccountRepository accounts;
  private final SecretHasher hasher;
  private final JdbcTemplate jdbc;
  private final Briefpapierspeicher speicher;

  private HttpHeaders sitzung = new HttpHeaders();

  @Autowired
  BriefpapierIT(
      final TestRestTemplate rest,
      final AccountRepository accounts,
      final SecretHasher hasher,
      final JdbcTemplate jdbc,
      final Briefpapierspeicher speicher) {
    this.rest = rest;
    this.accounts = accounts;
    this.hasher = hasher;
    this.jdbc = jdbc;
    this.speicher = speicher;
  }

  @BeforeEach
  void stelleDenStandDerMigrationHerUndMeldeAn() {
    jdbc.execute("DELETE FROM briefbogen");
    jdbc.execute("INSERT INTO briefbogen DEFAULT VALUES");
    leereKonten(jdbc);
    accounts.save(Konten.neu(MAIL, ANGELEGT));
    sitzung = Anmeldung.perCode(rest, jdbc, hasher, MAIL);
  }

  /** Liest ohne Stand; jeder schreibende Weg legt den aktuellen Stand der Zeile vor (E2). */
  private <T> ResponseEntity<T> ruf(
      final HttpMethod methode, final @Nullable Object rumpf, final Class<T> typ) {
    final HttpHeaders kopf =
        HttpMethod.GET.equals(methode) ? sitzung : Stand.aktuell(sitzung, jdbc, "briefbogen", 1L);
    return rest.exchange(PFAD, methode, new HttpEntity<>(rumpf, kopf), typ);
  }

  /** Die Kopfzeilen der Sitzung samt {@code If-Match} auf einen frei gewaehlten Stand. */
  private HttpHeaders aufStand(final long version) {
    final HttpHeaders kopf = new HttpHeaders();
    kopf.putAll(sitzung);
    kopf.setIfMatch("\"" + version + "\"");
    return kopf;
  }

  private BriefbogenResponse lies() {
    return Objects.requireNonNull(ruf(HttpMethod.GET, null, BriefbogenResponse.class).getBody());
  }

  /** Ein leeres PDF mit je einer Seite in den genannten Formaten. */
  private static byte[] pdf(final PDRectangle... formate) throws IOException {
    try (PDDocument dokument = new PDDocument()) {
      for (final PDRectangle format : formate) {
        dokument.addPage(new PDPage(format));
      }
      final ByteArrayOutputStream ausgabe = new ByteArrayOutputStream();
      dokument.save(ausgabe);
      return ausgabe.toByteArray();
    }
  }

  private <T> ResponseEntity<T> ladeHoch(
      final String dateiname, final byte[] bytes, final Class<T> typ) {
    final HttpHeaders dateikopf = new HttpHeaders();
    dateikopf.setContentType(MediaType.APPLICATION_PDF);
    final MultiValueMap<String, Object> felder = new LinkedMultiValueMap<>();
    felder.add(
        "datei",
        new HttpEntity<>(
            new ByteArrayResource(bytes) {
              @Override
              public String getFilename() {
                return dateiname;
              }
            },
            dateikopf));
    final HttpHeaders kopf = Stand.aktuell(sitzung, jdbc, "briefbogen", 1L);
    kopf.setContentType(MediaType.MULTIPART_FORM_DATA);
    return rest.exchange(PFAD + "/datei", HttpMethod.POST, new HttpEntity<>(felder, kopf), typ);
  }

  private <T> ResponseEntity<T> entferne(final Class<T> typ) {
    return rest.exchange(
        PFAD + "/datei",
        HttpMethod.DELETE,
        new HttpEntity<>(Stand.aktuell(sitzung, jdbc, "briefbogen", 1L)),
        typ);
  }

  private String schluessel() {
    return Objects.requireNonNull(
        jdbc.queryForObject("SELECT pdf_schluessel FROM briefbogen", String.class));
  }

  private static Map<String, Object> satz(
      final boolean absenderzeile,
      final boolean steuerBankZeile,
      final int links,
      final int rechts,
      final int inhaltOben,
      final int unten,
      final int folgeOben,
      final int folgeUnten) {
    return Map.of(
        "absenderzeile", absenderzeile,
        "steuerBankZeile", steuerBankZeile,
        "links", links,
        "rechts", rechts,
        "inhaltOben", inhaltOben,
        "unten", unten,
        "folgeOben", folgeOben,
        "folgeUnten", folgeUnten);
  }

  @Test
  void lesen_givenTheFreshInstance_thenAnswersWithTheDefaultsAndNoKey() {
    // When
    final ResponseEntity<String> roh = ruf(HttpMethod.GET, null, String.class);

    // Then — Kriterium 15: 25, 20, 98,5, 20, 20, 20 mm; beide Schalter an (E15).
    assertThat(roh.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(roh.getBody()).doesNotContainIgnoringCase("schluessel");
    assertThat(lies())
        .isEqualTo(
            new BriefbogenResponse(
                false, null, null, null, true, true, 250, 200, 985, 200, 200, 200, 0L));
  }

  @Test
  void pflegen_thenTheNextReadAnswersWithTheStoredValues() {
    // When
    final ResponseEntity<BriefbogenResponse> antwort =
        ruf(
            HttpMethod.PUT,
            satz(false, true, 300, 150, 1_000, 250, 180, 220),
            BriefbogenResponse.class);

    // Then
    final BriefbogenResponse erwartet =
        new BriefbogenResponse(
            false, null, null, null, false, true, 300, 150, 1_000, 250, 180, 220, 1L);
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(antwort.getBody()).isEqualTo(erwartet);
    assertThat(lies()).isEqualTo(erwartet);
  }

  @Test
  void pflegen_givenAnImpossibleMargin_thenAnswersWith422AndKeepsTheRow() {
    // When — Kriterium 13: der untere Rand traegt hoechstens 70,0 mm.
    final ResponseEntity<String> antwort =
        ruf(HttpMethod.PUT, satz(false, true, 250, 200, 985, 701, 200, 200), String.class);

    // Then — auch der Schalter, der fuer sich gepasst haette, ist nicht geschrieben.
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(antwort.getBody()).contains("70,0 mm");
    assertThat(lies())
        .isEqualTo(
            new BriefbogenResponse(
                false, null, null, null, true, true, 250, 200, 985, 200, 200, 200, 0L));
  }

  @Test
  void pflegen_withoutIfMatch_thenAnswers428AndKeepsTheRow() {
    // When — Plan #415, E3: ohne vorgelegten Stand wird nicht geschrieben.
    final ResponseEntity<String> antwort =
        rest.exchange(
            PFAD,
            HttpMethod.PUT,
            new HttpEntity<>(satz(false, true, 300, 150, 1_000, 250, 180, 220), sitzung),
            String.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.PRECONDITION_REQUIRED);
    assertThat(lies().absenderzeile()).isTrue();
    assertThat(lies().version()).isZero();
  }

  @Test
  void pflegen_afterAnUploadOnThePreviousVersion_thenAnswers409AndKeepsTheBlatt()
      throws IOException {
    // Given — E11: die Maske ist auf Stand 0 geoeffnet; inzwischen wird ein Briefpapier
    // hochgeladen. Das schreibt in andere Spalten als Schalter und Raender, erhoeht aber denselben
    // Stand.
    final long geoeffnet = lies().version();
    final ResponseEntity<BriefbogenResponse> hochgeladen =
        ladeHoch("kopf.pdf", pdf(PDRectangle.A4), BriefbogenResponse.class);
    assertThat(Objects.requireNonNull(hochgeladen.getBody()).version()).isEqualTo(geoeffnet + 1);
    final BriefbogenResponse nachDemHochladen = lies();

    // When — Schalter und Raender auf dem Stand vor dem Hochladen.
    final ResponseEntity<String> antwort =
        rest.exchange(
            PFAD,
            HttpMethod.PUT,
            new HttpEntity<>(
                satz(false, false, 300, 150, 1_000, 250, 180, 220), aufStand(geoeffnet)),
            String.class);

    // Then — abgewiesen mit der Erweiterung konflikt; Briefpapier, Schalter und Raender bleiben.
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(antwort.getBody()).contains("\"konflikt\"").contains("veraltet");
    assertThat(lies()).isEqualTo(nachDemHochladen);
  }

  @Test
  void entfernen_onAnOutdatedStand_thenAnswers409AndKeepsTheBlatt() throws IOException {
    // Given — ein Briefpapier, danach ein geaenderter Rand: der Stand ist weitergezaehlt.
    ladeHoch("kopf.pdf", pdf(PDRectangle.A4), BriefbogenResponse.class);
    final long geoeffnet = lies().version();
    ruf(HttpMethod.PUT, satz(true, true, 300, 150, 1_000, 250, 180, 220), BriefbogenResponse.class);
    final String schluessel = schluessel();

    // When
    final ResponseEntity<String> antwort =
        rest.exchange(
            PFAD + "/datei",
            HttpMethod.DELETE,
            new HttpEntity<>(aufStand(geoeffnet)),
            String.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(schluessel()).isEqualTo(schluessel);
    assertThat(speicher.lies(schluessel)).isNotEmpty();
  }

  @Test
  void hochladen_thenZeigtGetDateinameGroesseUndSeitenzahl() throws IOException {
    // When
    final ResponseEntity<BriefbogenResponse> antwort =
        ladeHoch("kopf.pdf", pdf(PDRectangle.A4, PDRectangle.A4), BriefbogenResponse.class);

    // Then — die Groesse ist die des gereinigten, abgelegten Objekts.
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
    final BriefbogenResponse stand = lies();
    assertThat(stand).isEqualTo(antwort.getBody());
    assertThat(stand.briefpapier()).isTrue();
    assertThat(stand.dateiname()).isEqualTo("kopf.pdf");
    assertThat(stand.seiten()).isEqualTo(2);
    assertThat(stand.groesse()).isEqualTo((long) speicher.lies(schluessel()).length);
  }

  @Test
  void hochladen_givenVorhandenesBriefpapier_thenLiegtDasAlteNachDemCommitNichtMehrImSpeicher()
      throws IOException {
    // Given
    ladeHoch("alt.pdf", pdf(PDRectangle.A4), BriefbogenResponse.class);
    final String alt = schluessel();

    // When
    final ResponseEntity<BriefbogenResponse> antwort =
        ladeHoch("neu.pdf", pdf(PDRectangle.A4, PDRectangle.A4), BriefbogenResponse.class);

    // Then — das neue liegt unter einem anderen Schluessel, das alte ist fort.
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
    final String neu = schluessel();
    assertThat(neu).isNotEqualTo(alt);
    assertThat(speicher.lies(neu)).isNotEmpty();
    assertThatThrownBy(() -> speicher.lies(alt)).isInstanceOf(DokumentSpeicherAusfall.class);
    assertThat(lies().dateiname()).isEqualTo("neu.pdf");
  }

  @Test
  void hochladen_givenQuerformat_thenAbgewiesenUndDasBisherigeGiltWeiter() throws IOException {
    // Given — Szenario „Ungeeignetes PDF".
    ladeHoch("kopf.pdf", pdf(PDRectangle.A4), BriefbogenResponse.class);
    final BriefbogenResponse vorher = lies();
    final String alt = schluessel();

    // When
    final ResponseEntity<String> antwort = ladeHoch("quer.pdf", pdf(A4_QUER), String.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(antwort.getBody()).contains(BriefpapierNichtAnnehmbar.MELDUNG_FORMAT);
    assertThat(lies()).isEqualTo(vorher);
    assertThat(schluessel()).isEqualTo(alt);
    assertThat(speicher.lies(alt)).isNotEmpty();
  }

  @Test
  void entfernen_thenKeinBriefpapierUndDieselbenSchalterUndRaender() throws IOException {
    // Given — Schalter und Raender abseits der Vorgaben, dann ein Briefpapier.
    ruf(
        HttpMethod.PUT,
        satz(false, true, 300, 150, 1_000, 250, 180, 220),
        BriefbogenResponse.class);
    ladeHoch("kopf.pdf", pdf(PDRectangle.A4), BriefbogenResponse.class);
    final String alt = schluessel();

    // When
    final ResponseEntity<BriefbogenResponse> antwort = entferne(BriefbogenResponse.class);

    // Then — Kriterium 16: allein das Briefpapier ist fort, auch aus dem Speicher.
    final BriefbogenResponse erwartet =
        new BriefbogenResponse(
            false, null, null, null, false, true, 300, 150, 1_000, 250, 180, 220, 3L);
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(antwort.getBody()).isEqualTo(erwartet);
    assertThat(lies()).isEqualTo(erwartet);
    assertThatThrownBy(() -> speicher.lies(alt)).isInstanceOf(DokumentSpeicherAusfall.class);
  }

  @Test
  void entfernen_givenOhneBriefpapier_thenAntwortetMit404() {
    // When / Then
    assertThat(entferne(String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  // --- gestellte Rechnungen ---------------------------------------------------------------

  private <T> ResponseEntity<T> ruf(
      final String pfad,
      final HttpMethod methode,
      final @Nullable Object rumpf,
      final Class<T> typ) {
    return rest.exchange(pfad, methode, new HttpEntity<>(rumpf, sitzung), typ);
  }

  /**
   * Ein schreibender Weg an der Rechnung, auf ihrem aktuellen Stand (Plan #415, E2, E3): so, wie
   * die Maske ihn vorlegt.
   */
  private <T> ResponseEntity<T> rufAufStand(
      final String pfad,
      final HttpMethod methode,
      final @Nullable Object rumpf,
      final Class<T> typ,
      final long rechnungId) {
    return rest.exchange(
        pfad,
        methode,
        new HttpEntity<>(rumpf, Stand.aktuell(sitzung, jdbc, "rechnung", rechnungId)),
        typ);
  }

  /** Ein einseitiges Briefpapier in A4 hochkant mit einer Aufschrift oben links. */
  private static byte[] briefpapier(final String aufschrift) throws IOException {
    try (PDDocument dokument = new PDDocument()) {
      final PDPage blatt = new PDPage(PDRectangle.A4);
      dokument.addPage(blatt);
      try (PDPageContentStream inhalt = new PDPageContentStream(dokument, blatt)) {
        inhalt.beginText();
        inhalt.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 16);
        inhalt.newLineAtOffset(71, 800);
        inhalt.showText(aufschrift);
        inhalt.endText();
      }
      final ByteArrayOutputStream ausgabe = new ByteArrayOutputStream();
      dokument.save(ausgabe);
      return ausgabe.toByteArray();
    }
  }

  /** Leerer Bestand an Belegen, vollstaendige eigene Angaben und eine Firma mit Anschrift. */
  private void bereiteDasStellenVor() {
    jdbc.execute(
        "TRUNCATE rechnung_position, rechnung, angebot_position, angebot, ansprechpartner, firma"
            + " RESTART IDENTITY CASCADE");
    jdbc.execute("DELETE FROM rechnung_nummernkreis");
    jdbc.execute("DELETE FROM rechnung_einstellungen");
    jdbc.execute("INSERT INTO rechnung_einstellungen DEFAULT VALUES");
    final Map<String, Object> eigene = new LinkedHashMap<>();
    eigene.put("name", "Manfred Wolff");
    eigene.put("strasse", "Am Deich 2");
    eigene.put("plz", "28199");
    eigene.put("ort", "Bremen");
    eigene.put("steuernummer", "75/123/45678");
    eigene.put("bankverbindung", "DE02 1203 0000 0000 2020 51");
    // Auf dem aktuellen Stand der eigenen Angaben, wie die Maske (Plan #415, E2).
    assertThat(
            rest.exchange(
                    "/api/eigene-angaben",
                    HttpMethod.PUT,
                    new HttpEntity<>(eigene, Stand.aktuell(sitzung, jdbc, "eigene_angaben", 1L)),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    final Map<String, Object> firma = new LinkedHashMap<>();
    firma.put("name", "Adler AG");
    firma.put("strasse", "Hauptstrasse 1");
    firma.put("plz", "28195");
    firma.put("ort", "Bremen");
    firma.put("land", "DE");
    ruf("/api/firmen", HttpMethod.POST, firma, String.class);
  }

  /** Ein bestelltes Angebot ueber 10 Stunden Beratung, vollstaendig abgerechnet und gestellt. */
  private long stelleEineRechnung() {
    final long firmaId =
        Objects.requireNonNull(jdbc.queryForObject("SELECT id FROM firma", Long.class));
    final long angebotId =
        Objects.requireNonNull(
                ruf(
                        "/api/firmen/" + firmaId + "/angebote",
                        HttpMethod.POST,
                        Map.of("titel", "Beratung"),
                        AngebotResponse.class)
                    .getBody())
            .id();
    final LocalDate heute = LocalDate.now(Geschaeftszone.ZONE);
    final Map<String, Object> position = new LinkedHashMap<>();
    position.put("bezeichnung", "Beratung");
    position.put("abrechnungsmodus", "AUFWAND");
    position.put("menge", "10.00");
    position.put("einheit", "STUNDE");
    position.put("einzelpreis", "100.00");
    final Map<String, Object> angebot = new LinkedHashMap<>();
    angebot.put("angebotDatum", heute.toString());
    angebot.put("titel", "Beratung");
    angebot.put("positionen", List.of(position));
    rest.exchange(
        "/api/angebote/" + angebotId,
        HttpMethod.PUT,
        new HttpEntity<>(angebot, Stand.aktuell(sitzung, jdbc, "angebot", angebotId)),
        AngebotResponse.class);
    rest.exchange(
        "/api/angebote/" + angebotId + "/status/weiter",
        HttpMethod.POST,
        new HttpEntity<>(null, Stand.aktuell(sitzung, jdbc, "angebot", angebotId)),
        String.class);
    rest.exchange(
        "/api/angebote/" + angebotId + "/status/weiter",
        HttpMethod.POST,
        new HttpEntity<>(null, Stand.aktuell(sitzung, jdbc, "angebot", angebotId)),
        String.class);
    final RechnungResponse entwurf =
        Objects.requireNonNull(
            ruf(
                    "/api/angebote/" + angebotId + "/rechnungen",
                    HttpMethod.POST,
                    null,
                    RechnungResponse.class)
                .getBody());
    final Map<String, Object> zeile = new LinkedHashMap<>();
    zeile.put("angebotPositionId", Long.valueOf(entwurf.zeilen().getFirst().angebotPositionId()));
    zeile.put("bezeichnung", "Beratung");
    zeile.put("menge", "10.00");
    final Map<String, Object> rechnung = new LinkedHashMap<>();
    rechnung.put("rechnungDatum", heute.toString());
    rechnung.put("leistungszeitraum", "Oktober 2026");
    rechnung.put("positionen", List.of(zeile));
    rufAufStand(
        "/api/rechnungen/" + entwurf.id(),
        HttpMethod.PUT,
        rechnung,
        RechnungResponse.class,
        entwurf.id());
    final ResponseEntity<RechnungResponse> gestellt =
        rufAufStand(
            "/api/rechnungen/" + entwurf.id() + "/stellen",
            HttpMethod.POST,
            null,
            RechnungResponse.class,
            entwurf.id());
    assertThat(gestellt.getStatusCode()).isEqualTo(HttpStatus.OK);
    return entwurf.id();
  }

  /** Das Dokument der gestellten Rechnung, wie die Schnittstelle es ausliefert. */
  private byte[] dokument(final long rechnungId) {
    return Objects.requireNonNull(
        ruf("/api/rechnungen/" + rechnungId + "/dokument", HttpMethod.GET, null, byte[].class)
            .getBody());
  }

  private static String text(final byte[] pdf) throws IOException {
    try (PDDocument dokument = Loader.loadPDF(pdf)) {
      return new PDFTextStripper().getText(dokument);
    }
  }

  @Test
  void ladeHoch_givenGestellteRechnung_thenBriefpapierAendernNachGestellterRechnung()
      throws IOException {
    // Given — Szenario „Briefpapier aendern nach gestellter Rechnung".
    bereiteDasStellenVor();
    ladeHoch("alt.pdf", briefpapier("Altes Briefpapier"), BriefbogenResponse.class);
    final long gestellte = stelleEineRechnung();
    final byte[] vorher = dokument(gestellte);
    assertThat(text(vorher)).contains("Altes Briefpapier");

    // When
    final ResponseEntity<BriefbogenResponse> antwort =
        ladeHoch("neu.pdf", briefpapier("Neues Briefpapier"), BriefbogenResponse.class);

    // Then — Kriterium 10: die gestellte Rechnung ist Byte fuer Byte dieselbe.
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(dokument(gestellte)).isEqualTo(vorher);

    // Then — erst die naechste Rechnung traegt das neue Briefpapier.
    assertThat(text(dokument(stelleEineRechnung())))
        .contains("Neues Briefpapier")
        .doesNotContain("Altes Briefpapier");
  }

  // --- /probedruck -------------------------------------------------------------------------

  private ResponseEntity<byte[]> probedruck() {
    return rest.exchange(
        PFAD + "/probedruck", HttpMethod.GET, new HttpEntity<>(sitzung), byte[].class);
  }

  private long rechnungen() {
    return Objects.requireNonNull(jdbc.queryForObject("SELECT count(*) FROM rechnung", Long.class));
  }

  private List<Map<String, Object>> nummernkreis() {
    return jdbc.queryForList(
        "SELECT jahr, naechste_nummer FROM rechnung_nummernkreis ORDER BY jahr");
  }

  @Test
  void probedruck_thenKeineRechnungUndKeineNummerVerbraucht() throws IOException {
    // Given — Szenario „Probedruck vor der ersten Rechnung": ein zweiseitiges Briefpapier.
    ladeHoch("kopf.pdf", pdf(PDRectangle.A4, PDRectangle.A4), BriefbogenResponse.class);
    final long rechnungenVorher = rechnungen();
    final List<Map<String, Object>> nummernVorher = nummernkreis();

    // When
    final ResponseEntity<byte[]> antwort = probedruck();

    // Then — ein PDF mit Folgeseite, und im Bestand hat sich nichts bewegt.
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(antwort.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
    try (PDDocument dokument = Loader.loadPDF(Objects.requireNonNull(antwort.getBody()))) {
      assertThat(dokument.getNumberOfPages()).isGreaterThanOrEqualTo(2);
    }
    assertThat(rechnungen()).isEqualTo(rechnungenVorher);
    assertThat(nummernkreis()).isEqualTo(nummernVorher);
  }

  @Test
  void probedruck_givenOhneBriefpapier_thenAntwortetMit404() {
    // When / Then — Kriterium 9, zweiter Satz.
    assertThat(probedruck().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }
}
