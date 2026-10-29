package org.mwolff.fbcrm.rechnung;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.Stand;
import org.mwolff.fbcrm.angebot.web.AngebotResponse;
import org.mwolff.fbcrm.auth.Anmeldung;
import org.mwolff.fbcrm.auth.Konten;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.SecretHasher;
import org.mwolff.fbcrm.common.Geschaeftszone;
import org.mwolff.fbcrm.common.Laenderliste;
import org.mwolff.fbcrm.rechnung.domain.DokumentSpeicher;
import org.mwolff.fbcrm.rechnung.infrastructure.KositPruefung;
import org.mwolff.fbcrm.rechnung.infrastructure.PdfaPruefung;
import org.mwolff.fbcrm.rechnung.web.BelegabsenderResponse;
import org.mwolff.fbcrm.rechnung.web.BelegempfaengerResponse;
import org.mwolff.fbcrm.rechnung.web.RechnungResponse;
import org.mwolff.fbcrm.rechnung.web.RechnungZeileResponse;
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
import org.w3c.dom.Document;

/**
 * Die gestellte Rechnung als E-Rechnung, ueber die ganze Kette gegen eine echte PostgreSQL- und
 * MinIO-Instanz (fachliche Quelle #460, Kriterien 1 bis 6; Plan #461, E3, E4, E5, E19, E20).
 *
 * <p>Gestellt wird ueber {@code POST /api/rechnungen/{id}/stellen}, geladen ueber {@code GET
 * /api/rechnungen/{id}/dokument} — derselbe Weg wie in der Oberflaeche. Am heruntergeladenen
 * Dokument wird dreierlei geprueft: dass genau eine {@code factur-x.xml} darin steckt, dass sie die
 * Pruefung der KoSIT nach EN 16931 besteht und dass das Dokument PDF/A-3B ist ({@link
 * PdfaPruefung}). Dazu, dass die XML dieselben Angaben traegt, die die Schnittstelle zur Rechnung
 * liefert.
 *
 * <p>Die aeltere Rechnung legt der Test unmittelbar in Datenbank und Speicher, wie sie vor der
 * Einfuehrung entstanden waere: mit der Bankverbindung als Freitext und ohne IBAN.
 */
class ERechnungIT extends AbstractIntegrationTest {

  private static final String MAIL = "manne@example.org";
  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");

  private static final String FIRMENNAME = "Adler AG";
  private static final String EIGENER_NAME = "Manfred Wolff";
  private static final String STEUERNUMMER = "75/123/45678";
  private static final String UMSATZSTEUER_ID = "DE123456789";
  private static final String IBAN = "DE02120300000000202051";
  private static final String BIC = "BYLADEM1001";
  private static final String ZEITRAUM = "Oktober 2026";

  /** Die Bankzeile im Wortlaut aus #467 (#460, Kriterium 8). */
  private static final String BANKZEILE =
      "Bankverbindung: " + EIGENER_NAME + " · IBAN DE02 1203 0000 0000 2020 51 · BIC " + BIC;

  /** Die Bankverbindung einer aelteren Rechnung, als Freitext. */
  private static final String ALTE_BANKVERBINDUNG = "Sparkasse Bremen, IBAN DE02 1203 0000 0000";

  private static final String RSM = "urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100";

  private static final String RAM =
      "urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100";

  private static final String UDT = "urn:un:unece:uncefact:data:standard:UnqualifiedDataType:100";

  private static final String HANDEL = "/rsm:CrossIndustryInvoice/rsm:SupplyChainTradeTransaction/";

  private static final String ABRECHNUNG = HANDEL + "ram:ApplicableHeaderTradeSettlement/";

  private static final String SUMMEN =
      ABRECHNUNG + "ram:SpecifiedTradeSettlementHeaderMonetarySummation/";

  private static final String VERKAEUFER =
      HANDEL + "ram:ApplicableHeaderTradeAgreement/ram:SellerTradeParty/";

  private static final String KAEUFER =
      HANDEL + "ram:ApplicableHeaderTradeAgreement/ram:BuyerTradeParty/";

  private static final Map<String, String> EINHEITEN =
      Map.of("STUNDE", "HUR", "PERSONENTAG", "DAY", "PAUSCHAL", "LS");

  private final TestRestTemplate rest;
  private final AccountRepository accounts;
  private final SecretHasher hasher;
  private final JdbcTemplate jdbc;
  private final DokumentSpeicher dokumente;

  /** Das Rechnungsdatum — siehe {@code RechnungStellenIT}. */
  private final LocalDate heute = LocalDate.now(Geschaeftszone.ZONE);

  private HttpHeaders sitzung = new HttpHeaders();
  private long firmaId;

  @Autowired
  ERechnungIT(
      final TestRestTemplate rest,
      final AccountRepository accounts,
      final SecretHasher hasher,
      final JdbcTemplate jdbc,
      final DokumentSpeicher dokumente) {
    this.rest = rest;
    this.accounts = accounts;
    this.hasher = hasher;
    this.jdbc = jdbc;
    this.dokumente = dokumente;
  }

  @BeforeEach
  void leereDenBestandUndMeldeAn() {
    jdbc.execute(
        "TRUNCATE rechnung_position, rechnung, angebot_position, angebot, ansprechpartner, firma"
            + " RESTART IDENTITY CASCADE");
    jdbc.execute("DELETE FROM rechnung_nummernkreis");
    jdbc.execute("DELETE FROM rechnung_einstellungen");
    jdbc.execute("INSERT INTO rechnung_einstellungen DEFAULT VALUES");
    jdbc.execute("DELETE FROM briefbogen");
    jdbc.execute("INSERT INTO briefbogen DEFAULT VALUES");
    leereKonten(jdbc);
    accounts.save(Konten.neu(MAIL, ANGELEGT));
    sitzung = Anmeldung.perCode(rest, jdbc, hasher, MAIL);
    eigeneAngabenSetzen(STEUERNUMMER, UMSATZSTEUER_ID);
    firmaId = firmaAnlegen();
  }

  private <T> ResponseEntity<T> ruf(
      final String pfad,
      final HttpMethod methode,
      final @Nullable Object rumpf,
      final Class<T> typ) {
    return rest.exchange(pfad, methode, new HttpEntity<>(rumpf, sitzung), typ);
  }

  private <T> ResponseEntity<T> rufAufStand(
      final String pfad,
      final HttpMethod methode,
      final @Nullable Object rumpf,
      final Class<T> typ,
      final String tabelle,
      final long id) {
    return rest.exchange(
        pfad, methode, new HttpEntity<>(rumpf, Stand.aktuell(sitzung, jdbc, tabelle, id)), typ);
  }

  /** Die eigenen Angaben mit Anschrift, Bankfeldern und den beiden gewaehlten Steuerangaben. */
  private void eigeneAngabenSetzen(
      final @Nullable String steuernummer, final @Nullable String umsatzsteuerId) {
    final Map<String, Object> rumpf = new LinkedHashMap<>();
    rumpf.put("name", EIGENER_NAME);
    rumpf.put("berufsbezeichnung", "Softwarearchitekt");
    rumpf.put("strasse", "Am Deich 2");
    rumpf.put("plz", "28199");
    rumpf.put("ort", "Bremen");
    rumpf.put("land", "Deutschland");
    rumpf.put("email", "post@example.org");
    rumpf.put("telefon", "0421 123456");
    rumpf.put("webadresse", "https://example.org");
    rumpf.put("steuernummer", steuernummer);
    rumpf.put("umsatzsteuerId", umsatzsteuerId);
    rumpf.put("iban", IBAN);
    rumpf.put("bic", BIC);
    rumpf.put("kontoinhaber", EIGENER_NAME);
    assertThat(
            rufAufStand(
                    "/api/eigene-angaben",
                    HttpMethod.PUT,
                    rumpf,
                    String.class,
                    "eigene_angaben",
                    1L)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  private long firmaAnlegen() {
    final Map<String, Object> rumpf = new LinkedHashMap<>();
    rumpf.put("name", FIRMENNAME);
    rumpf.put("strasse", "Hauptstrasse 1");
    rumpf.put("plz", "28195");
    rumpf.put("ort", "Bremen");
    rumpf.put("land", "DE");
    rumpf.put("steuernummer", null);
    rumpf.put("umsatzsteuerId", null);
    ruf("/api/firmen", HttpMethod.POST, rumpf, String.class);
    return Objects.requireNonNull(
            jdbc.queryForObject("SELECT id FROM firma WHERE name = ?", Long.class, FIRMENNAME))
        .longValue();
  }

  private static Map<String, Object> angebotsposition(
      final String bezeichnung,
      final String modus,
      final String menge,
      final String einheit,
      final String einzelpreis) {
    final Map<String, Object> position = new LinkedHashMap<>();
    position.put("id", null);
    position.put("bezeichnung", bezeichnung);
    position.put("abrechnungsmodus", modus);
    position.put("menge", menge);
    position.put("einheit", einheit);
    position.put("einzelpreis", einzelpreis);
    return position;
  }

  /** Ein bestelltes Angebot mit Beratung nach Aufwand in Stunden und einer Pauschale. */
  private long bestelltesAngebot() {
    final long angebotId =
        Objects.requireNonNull(
                ruf(
                        "/api/firmen/" + firmaId + "/angebote",
                        HttpMethod.POST,
                        Map.of("titel", "Website-Relaunch"),
                        AngebotResponse.class)
                    .getBody())
            .id();
    final Map<String, Object> rumpf = new LinkedHashMap<>();
    rumpf.put("angebotDatum", heute.toString());
    rumpf.put("titel", "Website-Relaunch");
    rumpf.put("ansprechpartnerId", null);
    rumpf.put("beschreibung", "Neugestaltung der Website");
    rumpf.put(
        "positionen",
        List.of(
            angebotsposition("Beratung", "AUFWAND", "160.00", "STUNDE", "100.00"),
            angebotsposition("Konzeption", "FESTPREIS", "1.00", "PAUSCHAL", "1250.50")));
    assertThat(
            rufAufStand(
                    "/api/angebote/" + angebotId,
                    HttpMethod.PUT,
                    rumpf,
                    String.class,
                    "angebot",
                    angebotId)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    for (int schritt = 0; schritt < 2; schritt++) {
      rufAufStand(
          "/api/angebote/" + angebotId + "/status/weiter",
          HttpMethod.POST,
          null,
          String.class,
          "angebot",
          angebotId);
    }
    return angebotId;
  }

  /** Ein Entwurf ueber 12,5 Stunden Beratung und die ganze Pauschale. */
  private RechnungResponse entwurf(final long angebotId) {
    final RechnungResponse entwurf =
        Objects.requireNonNull(
            ruf(
                    "/api/angebote/" + angebotId + "/rechnungen",
                    HttpMethod.POST,
                    null,
                    RechnungResponse.class)
                .getBody());
    final List<Map<String, Object>> positionen =
        entwurf.zeilen().stream()
            .map(
                zeile -> {
                  final Map<String, Object> position = new LinkedHashMap<>();
                  position.put("angebotPositionId", Long.valueOf(zeile.angebotPositionId()));
                  position.put("bezeichnung", zeile.bezeichnung());
                  position.put("menge", "Beratung".equals(zeile.bezeichnung()) ? "12.50" : "1.00");
                  return position;
                })
            .toList();
    final Map<String, Object> rumpf = new LinkedHashMap<>();
    rumpf.put("rechnungDatum", heute.toString());
    rumpf.put("leistungszeitraum", ZEITRAUM);
    rumpf.put("positionen", positionen);
    final ResponseEntity<RechnungResponse> antwort =
        rufAufStand(
            "/api/rechnungen/" + entwurf.id(),
            HttpMethod.PUT,
            rumpf,
            RechnungResponse.class,
            "rechnung",
            entwurf.id());
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
    return Objects.requireNonNull(antwort.getBody());
  }

  /** Stellt ueber die Schnittstelle und liefert die gestellte Rechnung, wie sie sie zeigt. */
  private RechnungResponse gestellt(final long rechnungId) {
    final ResponseEntity<RechnungResponse> antwort =
        rufAufStand(
            "/api/rechnungen/" + rechnungId + "/stellen",
            HttpMethod.POST,
            null,
            RechnungResponse.class,
            "rechnung",
            rechnungId);
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
    return Objects.requireNonNull(antwort.getBody());
  }

  /** Laedt das Dokument wie die Oberflaeche. */
  private byte[] heruntergeladen(final long rechnungId) {
    final ResponseEntity<byte[]> antwort =
        ruf("/api/rechnungen/" + rechnungId + "/dokument", HttpMethod.GET, null, byte[].class);
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
    return Objects.requireNonNull(antwort.getBody());
  }

  private RechnungResponse lese(final long rechnungId) {
    return Objects.requireNonNull(
        ruf("/api/rechnungen/" + rechnungId, HttpMethod.GET, null, RechnungResponse.class)
            .getBody());
  }

  /** Stellt eine neue Rechnung ueber das Angebot und laedt ihr Dokument. */
  private RechnungResponse neueRechnungGestellt() {
    return gestellt(entwurf(bestelltesAngebot()).id());
  }

  /** Die eingebetteten Dateien des Dokuments, nach Namen. */
  private static Map<String, PDComplexFileSpecification> eingebettete(final byte[] pdf)
      throws IOException {
    try (PDDocument dokument = Loader.loadPDF(pdf)) {
      final PDDocumentNameDictionary namen = dokument.getDocumentCatalog().getNames();
      if (namen == null) {
        return Map.of();
      }
      final PDEmbeddedFilesNameTreeNode baum = namen.getEmbeddedFiles();
      return baum == null || baum.getNames() == null ? Map.of() : baum.getNames();
    }
  }

  /** Die eine {@code factur-x.xml}, mit {@code AFRelationship=Alternative}. */
  private static byte[] facturX(final byte[] pdf) throws IOException {
    try (PDDocument dokument = Loader.loadPDF(pdf)) {
      final PDEmbeddedFilesNameTreeNode baum =
          dokument.getDocumentCatalog().getNames().getEmbeddedFiles();
      final Map<String, PDComplexFileSpecification> dateien = baum.getNames();
      assertThat(dateien).containsOnlyKeys("factur-x.xml");
      final PDComplexFileSpecification datei = dateien.get("factur-x.xml");
      assertThat(datei.getCOSObject().getNameAsString("AFRelationship")).isEqualTo("Alternative");
      return datei.getEmbeddedFile().toByteArray();
    }
  }

  private static String text(final byte[] pdf) throws IOException {
    try (PDDocument dokument = Loader.loadPDF(pdf)) {
      return new PDFTextStripper().getText(dokument);
    }
  }

  /** Ein Briefpapier, das fuer PDF/A taugt: Liberation Sans eingebettet, Farben in RGB (E5). */
  private static byte[] tauglichesBriefpapier() throws IOException {
    try (PDDocument dokument = new PDDocument();
        InputStream schrift =
            Objects.requireNonNull(
                ERechnungIT.class.getResourceAsStream("/beleg/LiberationSans-Bold.ttf"))) {
      final PDPage blatt = new PDPage(PDRectangle.A4);
      dokument.addPage(blatt);
      try (PDPageContentStream inhalt = new PDPageContentStream(dokument, blatt)) {
        inhalt.setNonStrokingColor(0.1f, 0.2f, 0.6f);
        inhalt.beginText();
        inhalt.setFont(PDType0Font.load(dokument, schrift, true), 16);
        inhalt.newLineAtOffset(71, 800);
        inhalt.showText("Wolff Software-Architektur");
        inhalt.endText();
      }
      final ByteArrayOutputStream ausgabe = new ByteArrayOutputStream();
      dokument.save(ausgabe);
      return ausgabe.toByteArray();
    }
  }

  private void ladeBriefpapierHoch(final byte[] pdf) {
    final HttpHeaders dateikopf = new HttpHeaders();
    dateikopf.setContentType(MediaType.APPLICATION_PDF);
    final MultiValueMap<String, Object> felder = new LinkedMultiValueMap<>();
    felder.add(
        "datei",
        new HttpEntity<>(
            new ByteArrayResource(pdf) {
              @Override
              public String getFilename() {
                return "briefpapier.pdf";
              }
            },
            dateikopf));
    final HttpHeaders kopf = Stand.aktuell(sitzung, jdbc, "briefbogen", 1L);
    kopf.setContentType(MediaType.MULTIPART_FORM_DATA);
    assertThat(
            rest.exchange(
                    "/api/briefpapier/datei",
                    HttpMethod.POST,
                    new HttpEntity<>(felder, kopf),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("Szenario: Eine neue Rechnung wird als E-Rechnung gestellt")
  void stelle_thenTheDocumentIsAnERechnungWithTheValuesOfTheRechnung() throws Exception {
    // Given — Anschrift, USt-IdNr., Kontoinhaber und IBAN hinterlegt, der Kunde in Deutschland.
    // When — gestellt und heruntergeladen wie in der Oberflaeche.
    final RechnungResponse rechnung = neueRechnungGestellt();
    final byte[] pdf = heruntergeladen(rechnung.id());

    // Then — genau eine factur-x.xml, KoSIT ohne Fehler, PDF/A-3B ohne Verstoss.
    final byte[] xml = facturX(pdf);
    assertThat(KositPruefung.fehler(xml)).isEmpty();
    assertThat(PdfaPruefung.verstoesse(pdf)).isEmpty();

    // Then — die XML traegt die Angaben, die die Schnittstelle zur Rechnung liefert.
    final Document cii = gelesen(xml);
    assertThat(wert(cii, "/rsm:CrossIndustryInvoice/rsm:ExchangedDocument/ram:ID"))
        .isEqualTo(rechnung.nummer());
    assertThat(
            wert(
                cii,
                "/rsm:CrossIndustryInvoice/rsm:ExchangedDocument/ram:IssueDateTime"
                    + "/udt:DateTimeString"))
        .isEqualTo(tag(rechnung.rechnungDatum()));
    positionenStimmen(cii, rechnung);
    summenStimmen(cii, rechnung);
    assertThat(wert(cii, ABRECHNUNG + "ram:SpecifiedTradePaymentTerms/ram:DueDateDateTime/*"))
        .isEqualTo(
            tag(
                rechnung
                    .rechnungDatum()
                    .plusDays(Objects.requireNonNull(rechnung.zahlungszielTage()).longValue())));
    bankverbindungStimmt(cii, Objects.requireNonNull(rechnung.absender()));
    verkaeuferStimmt(cii, Objects.requireNonNull(rechnung.absender()));
    kaeuferStimmt(cii, Objects.requireNonNull(rechnung.empfaenger()));

    // Then — auf dem Dokument steht die Bankzeile im Wortlaut aus #467.
    assertThat(text(pdf)).contains(BANKZEILE);
    assertThat(rechnung.absender().bankzeile()).isEqualTo(BANKZEILE);
  }

  @Test
  void stelle_givenATauglichesBriefpapier_thenKositAndPdfaReportNothing() throws Exception {
    // Given — ein Briefpapier mit eingebetteter Schrift in RGB (E5).
    ladeBriefpapierHoch(tauglichesBriefpapier());

    // When
    final RechnungResponse rechnung = neueRechnungGestellt();
    final byte[] pdf = heruntergeladen(rechnung.id());

    // Then — das Briefpapier liegt darunter, und die Datei ist trotzdem PDF/A-3B.
    assertThat(text(pdf)).contains("Wolff Software-Architektur");
    assertThat(KositPruefung.fehler(facturX(pdf))).isEmpty();
    assertThat(PdfaPruefung.verstoesse(pdf)).isEmpty();
  }

  @Test
  void stelle_givenOnlyASteuernummer_thenKositReportsNoError() throws Exception {
    // Given — ohne USt-IdNr.; die Steuernummer steht dann auch als Kennung des Verkaeufers.
    eigeneAngabenSetzen(STEUERNUMMER, null);

    // When
    final RechnungResponse rechnung = neueRechnungGestellt();
    final byte[] xml = facturX(heruntergeladen(rechnung.id()));

    // Then
    assertThat(KositPruefung.fehler(xml)).isEmpty();
    assertThat(wert(gelesen(xml), VERKAEUFER + "ram:SpecifiedTaxRegistration/ram:ID"))
        .isEqualTo(STEUERNUMMER);
  }

  @Test
  @DisplayName("Szenario: Eine ältere Rechnung bleibt, wie sie war")
  void dokument_givenARechnungFromBeforeTheIntroduction_thenTheSameBytesWithoutERechnung()
      throws Exception {
    // Given — eine Rechnung von vor der Einfuehrung: Bankverbindung als Freitext, keine IBAN, und
    // ihr Dokument liegt mit festen Bytes im Speicher.
    final long angebotId = bestelltesAngebot();
    final long positionId =
        Objects.requireNonNull(
                jdbc.queryForObject(
                    "SELECT id FROM angebot_position WHERE angebot_id = ? AND bezeichnung = ?",
                    Long.class,
                    angebotId,
                    "Beratung"))
            .longValue();
    final long rechnungId = aeltereRechnung(angebotId, positionId);
    final byte[] vorher = gewoehnlichesDokument();
    final String schluessel = dokumente.lege(rechnungId, vorher);
    jdbc.update("UPDATE rechnung SET pdf_schluessel = ? WHERE id = ?", schluessel, rechnungId);

    // When
    final RechnungResponse ansicht = lese(rechnungId);
    final byte[] pdf = heruntergeladen(rechnungId);

    // Then — die alte Bankzeile, dieselben Bytes, keine eingebettete Datei.
    assertThat(Objects.requireNonNull(ansicht.absender()).bankzeile())
        .isEqualTo("Bankverbindung: " + ALTE_BANKVERBINDUNG);
    assertThat(pdf).isEqualTo(vorher);
    assertThat(eingebettete(pdf)).isEmpty();
  }

  private long aeltereRechnung(final long angebotId, final long positionId) {
    final long rechnungId =
        Objects.requireNonNull(
                jdbc.queryForObject(
                    """
                    INSERT INTO rechnung (angebot_id, zustand, rechnung_datum, nummer, steuersatz,
                                          zahlungsziel_tage, gestellt_am, empfaenger_firma,
                                          empfaenger_strasse, empfaenger_plz, empfaenger_ort,
                                          empfaenger_land, absender_name, absender_strasse,
                                          absender_plz, absender_ort, absender_steuernummer,
                                          absender_bankverbindung)
                    VALUES (?, 'GESTELLT', ?, '0001-2025', 19.00, 14, now(), ?, 'Hauptstrasse 1',
                            '28195', 'Bremen', 'Deutschland', ?, 'Am Deich 2', '28199', 'Bremen',
                            ?, ?)
                    RETURNING id
                    """,
                    Long.class,
                    angebotId,
                    heute,
                    FIRMENNAME,
                    EIGENER_NAME,
                    STEUERNUMMER,
                    ALTE_BANKVERBINDUNG))
            .longValue();
    jdbc.update(
        """
        INSERT INTO rechnung_position (rechnung_id, angebot_position_id, position, bezeichnung,
                                       einheit, menge, einzelpreis)
        VALUES (?, ?, 1, 'Beratung', 'STUNDE', 8.00, 100.00)
        """,
        rechnungId,
        positionId);
    return rechnungId;
  }

  /** Ein Dokument, wie es vor der Einfuehrung entstand: ein gewoehnliches PDF ohne Anhang. */
  private static byte[] gewoehnlichesDokument() throws IOException {
    try (PDDocument dokument = new PDDocument()) {
      dokument.addPage(new PDPage(PDRectangle.A4));
      final ByteArrayOutputStream ausgabe = new ByteArrayOutputStream();
      dokument.save(ausgabe);
      return ausgabe.toByteArray();
    }
  }

  private static void positionenStimmen(final Document cii, final RechnungResponse rechnung)
      throws Exception {
    final List<RechnungZeileResponse> zeilen =
        rechnung.zeilen().stream().filter(zeile -> zeile.menge().signum() > 0).toList();
    assertThat(zeilen).hasSize(2);
    assertThat(anzahl(cii, HANDEL + "ram:IncludedSupplyChainTradeLineItem")).isEqualTo(2);
    for (int i = 0; i < zeilen.size(); i++) {
      final RechnungZeileResponse zeile = zeilen.get(i);
      final String position = HANDEL + "ram:IncludedSupplyChainTradeLineItem[" + (i + 1) + "]/";
      assertThat(wert(cii, position + "ram:SpecifiedTradeProduct/ram:Name"))
          .isEqualTo(zeile.bezeichnung());
      final String menge = position + "ram:SpecifiedLineTradeDelivery/ram:BilledQuantity";
      assertThat(new BigDecimal(wert(cii, menge))).isEqualByComparingTo(zeile.menge());
      assertThat(wert(cii, menge + "/@unitCode")).isEqualTo(EINHEITEN.get(zeile.einheit().name()));
      assertThat(
              new BigDecimal(
                  wert(
                      cii,
                      position
                          + "ram:SpecifiedLineTradeAgreement/ram:NetPriceProductTradePrice"
                          + "/ram:ChargeAmount")))
          .isEqualByComparingTo(zeile.einzelpreis());
      assertThat(
              wert(
                  cii,
                  position
                      + "ram:SpecifiedLineTradeSettlement"
                      + "/ram:SpecifiedTradeSettlementLineMonetarySummation/ram:LineTotalAmount"))
          .isEqualTo(
              zeile
                  .menge()
                  .multiply(zeile.einzelpreis())
                  .setScale(2, RoundingMode.HALF_UP)
                  .toString());
    }
  }

  private static void summenStimmen(final Document cii, final RechnungResponse rechnung)
      throws Exception {
    assertThat(wert(cii, SUMMEN + "ram:TaxBasisTotalAmount")).isEqualTo(betrag(rechnung.netto()));
    assertThat(wert(cii, SUMMEN + "ram:TaxTotalAmount")).isEqualTo(betrag(rechnung.steuer()));
    assertThat(wert(cii, SUMMEN + "ram:GrandTotalAmount")).isEqualTo(betrag(rechnung.brutto()));
    assertThat(
            new BigDecimal(
                wert(cii, ABRECHNUNG + "ram:ApplicableTradeTax/ram:RateApplicablePercent")))
        .isEqualByComparingTo(rechnung.steuersatz());
  }

  /** IBAN und Kontoinhaber, wie sie die Bankzeile der Schnittstelle nennt. */
  private static void bankverbindungStimmt(final Document cii, final BelegabsenderResponse absender)
      throws Exception {
    final String konto =
        ABRECHNUNG
            + "ram:SpecifiedTradeSettlementPaymentMeans/ram:PayeePartyCreditorFinancialAccount/";
    final String bankzeile = Objects.requireNonNull(absender.bankzeile());
    final String iban = wert(cii, konto + "ram:IBANID");
    assertThat(iban).isEqualTo(IBAN);
    assertThat(bankzeile.replace(" ", "")).contains("IBAN" + iban);
    assertThat(bankzeile)
        .startsWith("Bankverbindung: " + wert(cii, konto + "ram:AccountName") + " ·");
  }

  private static void verkaeuferStimmt(final Document cii, final BelegabsenderResponse absender)
      throws Exception {
    assertThat(wert(cii, VERKAEUFER + "ram:Name")).isEqualTo(absender.name());
    anschriftStimmt(
        cii, VERKAEUFER, absender.strasse(), absender.plz(), absender.ort(), absender.land());
  }

  private static void kaeuferStimmt(final Document cii, final BelegempfaengerResponse empfaenger)
      throws Exception {
    assertThat(wert(cii, KAEUFER + "ram:Name")).isEqualTo(empfaenger.firma());
    anschriftStimmt(
        cii, KAEUFER, empfaenger.strasse(), empfaenger.plz(), empfaenger.ort(), empfaenger.land());
  }

  /** Strasse, Postleitzahl, Ort und das Land — in der XML als Kuerzel, gedruckt als Name. */
  private static void anschriftStimmt(
      final Document cii,
      final String partei,
      final @Nullable String strasse,
      final @Nullable String plz,
      final @Nullable String ort,
      final @Nullable String land)
      throws Exception {
    final String adresse = partei + "ram:PostalTradeAddress/";
    assertThat(wert(cii, adresse + "ram:LineOne")).isEqualTo(strasse);
    assertThat(wert(cii, adresse + "ram:PostcodeCode")).isEqualTo(plz);
    assertThat(wert(cii, adresse + "ram:CityName")).isEqualTo(ort);
    assertThat(land).isNotNull();
    assertThat(Laenderliste.name(wert(cii, adresse + "ram:CountryID"))).isEqualTo(land);
  }

  private static String tag(final LocalDate datum) {
    return DateTimeFormatter.BASIC_ISO_DATE.format(datum);
  }

  private static String betrag(final BigDecimal wert) {
    return wert.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
  }

  private static Document gelesen(final byte[] xml) throws Exception {
    final DocumentBuilderFactory fabrik = DocumentBuilderFactory.newInstance();
    fabrik.setNamespaceAware(true);
    return fabrik.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
  }

  private static String wert(final Document cii, final String pfad) throws Exception {
    return xpath().evaluate("string(" + pfad + ")", cii);
  }

  private static int anzahl(final Document cii, final String pfad) throws Exception {
    return Integer.parseInt(xpath().evaluate("count(" + pfad + ")", cii));
  }

  private static XPath xpath() {
    final XPath xpath = XPathFactory.newInstance().newXPath();
    xpath.setNamespaceContext(new Namensraeume());
    return xpath;
  }

  /** Die drei Praefixe der CII. */
  private static final class Namensraeume implements NamespaceContext {

    private static final Map<String, String> PRAEFIXE = Map.of("rsm", RSM, "ram", RAM, "udt", UDT);

    @Override
    public String getNamespaceURI(final String praefix) {
      return PRAEFIXE.getOrDefault(praefix, XMLConstants.NULL_NS_URI);
    }

    @Override
    public String getPrefix(final String namensraum) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<String> getPrefixes(final String namensraum) {
      throw new UnsupportedOperationException();
    }
  }
}
