package org.mwolff.fbcrm.rechnung;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.Stand;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;
import org.mwolff.fbcrm.angebot.web.AngebotResponse;
import org.mwolff.fbcrm.auth.Anmeldung;
import org.mwolff.fbcrm.auth.Konten;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.SecretHasher;
import org.mwolff.fbcrm.common.Geschaeftszone;
import org.mwolff.fbcrm.config.MinioProperties;
import org.mwolff.fbcrm.rechnung.application.Belegdrucker;
import org.mwolff.fbcrm.rechnung.application.Kupferwolkensatz;
import org.mwolff.fbcrm.rechnung.domain.DokumentSpeicher;
import org.mwolff.fbcrm.rechnung.domain.Rechnung;
import org.mwolff.fbcrm.rechnung.domain.RechnungRepository;
import org.mwolff.fbcrm.rechnung.domain.Rechnungszustand;
import org.mwolff.fbcrm.rechnung.web.RechnungResponse;
import org.mwolff.fbcrm.rechnung.web.RechnungseinstellungenResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;

/**
 * Das Stellen einer Rechnung ueber HTTP, gegen eine echte PostgreSQL- und MinIO-Instanz (#160,
 * Kriterien 5, 13 bis 16, 18 und 27).
 *
 * <p>Hier steht der ganze Weg, weil er nur hier zu sehen ist: Der Nummernkreis zaehlt in der
 * Datenbank, das Dokument liegt im Objektspeicher, und die Festschreibung erweist sich erst, wenn
 * Firma, Angebot, eigene Angaben und Einstellungen sich <b>nach</b> dem Stellen aendern und die
 * gestellte Rechnung davon unberuehrt bleibt.
 *
 * <p>Der Hauptweg ist der der Teilabrechnung: 160 angebotene Stunden, zwei Rechnungen ueber je 80.
 * Der zweite Entwurf entsteht <b>vor</b> dem Stellen des ersten — nur dann zeigt sich, dass der
 * Sprung des Angebots auf „abgerechnet" allein mit gestellten Rechnungen rechnet.
 *
 * <p>Der Nachweis der Zugangsregel steht hier und nicht nur in {@code AccessRuleIT}: Jeder neue
 * Pfad unter {@code /api} ist ohne Sitzung verschlossen.
 *
 * <p>Auf welchem Papier der Beleg entsteht, entscheidet der Briefbogen im Augenblick des Stellens
 * (Plan #363, E3, E7, E18): Mit hinterlegtem Briefpapier liegt es unter dem Satz, ohne entsteht der
 * Beleg der Kupferwolke wie bisher. Der Briefbogen wird darum vor jeder Methode auf den Stand der
 * Migration gesetzt — andere Testklassen der geteilten Datenbank hinterlassen ihn mit Schluesseln,
 * zu denen es kein Objekt gibt.
 */
class RechnungStellenIT extends AbstractIntegrationTest {

  private static final String MAIL = "manne@example.org";
  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");

  private static final String STUNDENSATZ = "100.00";
  private static final String ZEITRAUM = "September 2026";
  private static final String FIRMENNAME = "Adler AG";
  private static final String EIGENER_NAME = "Manfred Wolff";
  private static final String STEUERNUMMER = "75/123/45678";
  private static final String UMSATZSTEUER_ID = "DE123456789";
  private static final String BANKVERBINDUNG = "DE02 1203 0000 0000 2020 51";
  private static final String MUSTER = "{NNNN}-{JJJJ}";
  private static final Duration GEDULD = Duration.ofSeconds(30);

  /** Die Aufschrift des hinterlegten Briefpapiers. */
  private static final String BRIEFKOPF = "Wolff Software-Architektur";

  /** Der untere Rand der ersten Seite nach Kriterium 15: 20 mm in Punkten. */
  private static final double UNTERER_RAND = 20 / 25.4 * 72;

  /** Eine Farbe im Inhaltsstrom: drei Anteile und der Operator fuer Fuellung oder Strich. */
  private static final Pattern FARBE = Pattern.compile("(\\S+ \\S+ \\S+) (?:rg|RG)\\n");

  /** Die Kennung im Trailer: PDFBox bildet sie bei jedem Schreiben aus der Uhrzeit. */
  private static final Pattern KENNUNG = Pattern.compile("/ID \\[<([0-9A-F]+)> <([0-9A-F]+)>\\]");

  private final TestRestTemplate rest;
  private final AccountRepository accounts;
  private final SecretHasher hasher;
  private final JdbcTemplate jdbc;
  private final DokumentSpeicher dokumente;
  private final S3Client s3;
  private final MinioProperties zugang;
  private final RechnungRepository rechnungen;
  private final Belegdrucker drucker;

  /**
   * Das Rechnungsdatum und damit das Zaehlerjahr der Nummer (Kriterium 16).
   *
   * <p>Der heutige Tag und nicht ein fester: Die Maske der Einstellungen zeigt den Zaehler des
   * laufenden Jahres, und ein festes Datum liesse den Test am Jahreswechsel auf zwei Zaehler
   * schauen. Als Feld der Instanz und nicht als Konstante — die Zeit gehoert nicht in einen
   * statischen Initialisierer.
   */
  private final LocalDate heute = LocalDate.now(Geschaeftszone.ZONE);

  private HttpHeaders sitzung = new HttpHeaders();
  private long firmaId;

  @Autowired
  RechnungStellenIT(
      final TestRestTemplate rest,
      final AccountRepository accounts,
      final SecretHasher hasher,
      final JdbcTemplate jdbc,
      final DokumentSpeicher dokumente,
      final S3Client s3,
      final MinioProperties zugang,
      final RechnungRepository rechnungen,
      final Belegdrucker drucker) {
    this.rest = rest;
    this.accounts = accounts;
    this.hasher = hasher;
    this.jdbc = jdbc;
    this.dokumente = dokumente;
    this.s3 = s3;
    this.zugang = zugang;
    this.rechnungen = rechnungen;
    this.drucker = drucker;
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
    eigeneAngabenSetzen(STEUERNUMMER, UMSATZSTEUER_ID, BANKVERBINDUNG);
    firmaId = firmaAnlegen("Hauptstrasse 1", "28195", "Bremen");
  }

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

  /** Die eigenen Angaben, vollstaendig bis auf die drei Werte, die der Test waehlt. */
  private void eigeneAngabenSetzen(
      final @Nullable String steuernummer,
      final @Nullable String umsatzsteuerId,
      final @Nullable String bankverbindung) {
    final Map<String, Object> rumpf = new LinkedHashMap<>();
    rumpf.put("name", EIGENER_NAME);
    rumpf.put("berufsbezeichnung", "Softwarearchitekt");
    rumpf.put("strasse", "Am Deich 2");
    rumpf.put("plz", "28199");
    rumpf.put("ort", "Bremen");
    rumpf.put("land", "DE");
    rumpf.put("email", "post@example.org");
    rumpf.put("telefon", "0421 123456");
    rumpf.put("webadresse", "https://example.org");
    rumpf.put("steuernummer", steuernummer);
    rumpf.put("umsatzsteuerId", umsatzsteuerId);
    rumpf.put("bankverbindung", bankverbindung);
    // Auf dem aktuellen Stand der eigenen Angaben, wie die Maske (Plan #415, E2).
    assertThat(
            rest.exchange(
                    "/api/eigene-angaben",
                    HttpMethod.PUT,
                    new HttpEntity<>(rumpf, Stand.aktuell(sitzung, jdbc, "eigene_angaben", 1L)),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  private long firmaAnlegen(
      final @Nullable String strasse, final @Nullable String plz, final @Nullable String ort) {
    ruf("/api/firmen", HttpMethod.POST, firmenrumpf(strasse, plz, ort), String.class);
    return Objects.requireNonNull(
            jdbc.queryForObject("SELECT id FROM firma WHERE name = ?", Long.class, FIRMENNAME))
        .longValue();
  }

  private void firmaSetzen(
      final String name,
      final @Nullable String strasse,
      final @Nullable String plz,
      final @Nullable String ort) {
    final Map<String, Object> rumpf = firmenrumpf(strasse, plz, ort);
    rumpf.put("name", name);
    // Auf dem aktuellen Stand der Firma, wie die Maske (Plan #415, E2).
    assertThat(
            rest.exchange(
                    "/api/firmen/" + firmaId,
                    HttpMethod.PUT,
                    new HttpEntity<>(rumpf, Stand.aktuell(sitzung, jdbc, "firma", firmaId)),
                    String.class)
                .getStatusCode()
                .is2xxSuccessful())
        .isTrue();
  }

  private static Map<String, Object> firmenrumpf(
      final @Nullable String strasse, final @Nullable String plz, final @Nullable String ort) {
    final Map<String, Object> rumpf = new LinkedHashMap<>();
    rumpf.put("name", FIRMENNAME);
    rumpf.put("strasse", strasse);
    rumpf.put("plz", plz);
    rumpf.put("ort", ort);
    rumpf.put("land", "DE");
    rumpf.put("steuernummer", null);
    rumpf.put("umsatzsteuerId", null);
    return rumpf;
  }

  private void einstellungenSetzen(
      final int naechsteNummer, final String steuersatz, final int zahlungszielTage) {
    final Map<String, Object> rumpf = new LinkedHashMap<>();
    rumpf.put("nummerMuster", MUSTER);
    rumpf.put("naechsteNummer", Integer.valueOf(naechsteNummer));
    rumpf.put("steuersatz", steuersatz);
    rumpf.put("zahlungszielTage", Integer.valueOf(zahlungszielTage));
    // Auf dem aktuellen Stand der Einstellungen, wie die Maske (Plan #415, E2).
    rest.exchange(
        "/api/rechnung/einstellungen",
        HttpMethod.PUT,
        new HttpEntity<>(rumpf, Stand.aktuell(sitzung, jdbc, "rechnung_einstellungen", 1L)),
        Void.class);
  }

  /** Die naechste laufende Nummer, wie die Maske der Einstellungen sie zeigt. */
  private int naechsteNummer() {
    return Objects.requireNonNull(
            ruf(
                    "/api/rechnung/einstellungen",
                    HttpMethod.GET,
                    null,
                    RechnungseinstellungenResponse.class)
                .getBody())
        .naechsteNummer();
  }

  /** Die Nummer, die das Muster {@value #MUSTER} zur laufenden Nummer bildet. */
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
                        Map.of("titel", "Website-Relaunch"),
                        AngebotResponse.class)
                    .getBody())
            .id();
    positionSetzen(angebotId, null, "Beratung", menge, STUNDENSATZ);
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
    return angebotId;
  }

  /*
   * Mit Kennung heisst „schreib diese Zeile fort" (Plan #169, E2); ohne legt der Anwendungsfall
   * eine neue an. Eine Position, die in einer Rechnung steht, braucht ihre Kennung — eine neue
   * Zeile liesse die Rechnungsposition ins Leere zeigen.
   */
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
    rumpf.put("titel", "Website-Relaunch");
    rumpf.put("ansprechpartnerId", null);
    rumpf.put("beschreibung", "Neugestaltung der Website");
    rumpf.put("positionen", List.of(position));
    rest.exchange(
        "/api/angebote/" + angebotId,
        HttpMethod.PUT,
        new HttpEntity<>(rumpf, Stand.aktuell(sitzung, jdbc, "angebot", angebotId)),
        AngebotResponse.class);
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
    return Objects.requireNonNull(setzeMenge(entwurf, menge).getBody());
  }

  private ResponseEntity<RechnungResponse> setzeMenge(
      final RechnungResponse entwurf, final String menge) {
    return rufAufStand(
        "/api/rechnungen/" + entwurf.id(),
        HttpMethod.PUT,
        mengenrumpf(entwurf, menge),
        RechnungResponse.class,
        entwurf.id());
  }

  /** Der Rumpf der Entwurfsmaske mit der Beratung ueber die angegebene Menge. */
  private Map<String, Object> mengenrumpf(final RechnungResponse entwurf, final String menge) {
    final Map<String, Object> position = new LinkedHashMap<>();
    position.put(
        "angebotPositionId", Long.valueOf(entwurf.zeilen().getFirst().angebotPositionId()));
    position.put("bezeichnung", "Beratung");
    position.put("menge", menge);
    final Map<String, Object> rumpf = new LinkedHashMap<>();
    rumpf.put("rechnungDatum", heute.toString());
    rumpf.put("leistungszeitraum", ZEITRAUM);
    rumpf.put("positionen", List.of(position));
    return rumpf;
  }

  /** Ein zweites Konto mit eigenem Anzeigenamen, angemeldet in einer eigenen Sitzung. */
  private HttpHeaders zweitesKonto(final String mail, final String anzeigename) {
    accounts.save(Konten.neu(mail, ANGELEGT));
    jdbc.update("UPDATE account SET display_name = ? WHERE email = ?", anzeigename, mail);
    return Anmeldung.perCode(rest, jdbc, hasher, mail);
  }

  private long kontoZu(final String mail) {
    return Objects.requireNonNull(
            jdbc.queryForObject("SELECT id FROM account WHERE email = ?", Long.class, mail))
        .longValue();
  }

  /** Die Kopfzeilen einer Sitzung samt {@code If-Match} auf einen frei gewaehlten Stand. */
  private static HttpHeaders aufStand(final HttpHeaders kopf, final long version) {
    final HttpHeaders mitStand = new HttpHeaders();
    mitStand.putAll(kopf);
    mitStand.setIfMatch("\"" + version + "\"");
    return mitStand;
  }

  private ResponseEntity<String> stelle(final long rechnungId) {
    return rufAufStand(
        "/api/rechnungen/" + rechnungId + "/stellen",
        HttpMethod.POST,
        null,
        String.class,
        rechnungId);
  }

  private RechnungResponse gestellt(final long rechnungId) {
    final ResponseEntity<RechnungResponse> antwort =
        rufAufStand(
            "/api/rechnungen/" + rechnungId + "/stellen",
            HttpMethod.POST,
            null,
            RechnungResponse.class,
            rechnungId);
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
    return Objects.requireNonNull(antwort.getBody());
  }

  private RechnungResponse lese(final long rechnungId) {
    return Objects.requireNonNull(
        ruf("/api/rechnungen/" + rechnungId, HttpMethod.GET, null, RechnungResponse.class)
            .getBody());
  }

  private Angebotsstatus angebotsstatus(final long angebotId) {
    return Objects.requireNonNull(
            ruf("/api/angebote/" + angebotId, HttpMethod.GET, null, AngebotResponse.class)
                .getBody())
        .status();
  }

  /** Der Schluessel des archivierten Dokuments; er geht nicht ueber die Schnittstelle hinaus. */
  private @Nullable String pdfSchluessel(final long rechnungId) {
    return jdbc.queryForObject(
        "SELECT pdf_schluessel FROM rechnung WHERE id = ?", String.class, Long.valueOf(rechnungId));
  }

  /**
   * Wie viele Objekte unter dem Praefix dieser Rechnung im Eimer liegen.
   *
   * <p>Verglichen wird immer der <b>Zuwachs</b> und nie die absolute Zahl: Der Eimer gehoert der
   * ganzen Suite (E23), und {@code TRUNCATE … RESTART IDENTITY} gibt die Kennungen der Rechnungen
   * wieder aus — unter demselben Praefix koennen darum Belege aus fruehen Testmethoden liegen.
   */
  private int objekteZu(final long rechnungId) {
    return s3.listObjectsV2(
            ListObjectsV2Request.builder()
                .bucket(zugang.bucket())
                .prefix("rechnung/" + rechnungId + "/")
                .build())
        .keyCount()
        .intValue();
  }

  /** Der Text des abgelegten Belegs, gelesen wie von einem Aussenstehenden. */
  private String belegtext(final long rechnungId) throws IOException {
    final byte[] pdf =
        dokumente.lies(Objects.requireNonNull(pdfSchluessel(rechnungId), "pdf_schluessel"));
    try (PDDocument dokument = Loader.loadPDF(pdf)) {
      return new PDFTextStripper().getText(dokument);
    }
  }

  /** Ein bestelltes Angebot mit drei Positionen ueber Beratung, Konzeption und Umsetzung. */
  private long bestelltesAngebotMitDreiPositionen() {
    final long angebotId = bestelltesAngebot("160.00");
    final long beratung =
        Objects.requireNonNull(
            jdbc.queryForObject(
                "SELECT id FROM angebot_position WHERE angebot_id = ?",
                Long.class,
                Long.valueOf(angebotId)));
    final List<Map<String, Object>> positionen = new ArrayList<>();
    positionen.add(angebotsposition(beratung, "Beratung", "160.00"));
    positionen.add(angebotsposition(null, "Konzeption", "40.00"));
    positionen.add(angebotsposition(null, "Umsetzung", "80.00"));
    final Map<String, Object> rumpf = new LinkedHashMap<>();
    rumpf.put("angebotDatum", heute.toString());
    rumpf.put("titel", "Website-Relaunch");
    rumpf.put("ansprechpartnerId", null);
    rumpf.put("beschreibung", "Neugestaltung der Website");
    rumpf.put("positionen", positionen);
    rest.exchange(
        "/api/angebote/" + angebotId,
        HttpMethod.PUT,
        new HttpEntity<>(rumpf, Stand.aktuell(sitzung, jdbc, "angebot", angebotId)),
        AngebotResponse.class);
    return angebotId;
  }

  private static Map<String, Object> angebotsposition(
      final @Nullable Long positionId, final String bezeichnung, final String menge) {
    final Map<String, Object> position = new LinkedHashMap<>();
    position.put("id", positionId);
    position.put("bezeichnung", bezeichnung);
    position.put("abrechnungsmodus", "AUFWAND");
    position.put("menge", menge);
    position.put("einheit", "STUNDE");
    position.put("einzelpreis", STUNDENSATZ);
    return position;
  }

  /** Ein Entwurf zum Angebot, in dem jede Zeile die angegebene Menge traegt. */
  private RechnungResponse entwurfUeberAlleZeilen(final long angebotId, final String menge) {
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
                  position.put("menge", menge);
                  return position;
                })
            .toList();
    final Map<String, Object> rumpf = new LinkedHashMap<>();
    rumpf.put("rechnungDatum", heute.toString());
    rumpf.put("leistungszeitraum", ZEITRAUM);
    rumpf.put("positionen", positionen);
    return Objects.requireNonNull(
        rufAufStand(
                "/api/rechnungen/" + entwurf.id(),
                HttpMethod.PUT,
                rumpf,
                RechnungResponse.class,
                entwurf.id())
            .getBody());
  }

  /** Ein einseitiges Briefpapier in A4 hochkant mit einer blauen Aufschrift oben links. */
  private static byte[] briefpapier(final String aufschrift) throws IOException {
    try (PDDocument dokument = new PDDocument()) {
      final PDPage blatt = new PDPage(PDRectangle.A4);
      dokument.addPage(blatt);
      try (PDPageContentStream inhalt = new PDPageContentStream(dokument, blatt)) {
        inhalt.setNonStrokingColor(0.1f, 0.2f, 0.6f);
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
    // Auf dem aktuellen Stand des Briefbogens, wie die Maske (Plan #415, E2, E11).
    final HttpHeaders kopf = Stand.aktuell(sitzung, jdbc, "briefbogen", 1L);
    kopf.setContentType(MediaType.MULTIPART_FORM_DATA);
    final ResponseEntity<String> antwort =
        rest.exchange(
            "/api/briefpapier/datei",
            HttpMethod.POST,
            new HttpEntity<>(felder, kopf),
            String.class);
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  /** Der abgelegte Beleg, Byte fuer Byte. */
  private byte[] beleg(final long rechnungId) {
    return dokumente.lies(Objects.requireNonNull(pdfSchluessel(rechnungId), "pdf_schluessel"));
  }

  /** Der Inhaltsstrom der ersten Seite: alles, was der Drucker eingedruckt hat. */
  private static String eingedruckt(final byte[] pdf) throws IOException {
    try (PDDocument dokument = Loader.loadPDF(pdf);
        InputStream strom = dokument.getPage(0).getContents()) {
      return new String(strom.readAllBytes(), ISO_8859_1);
    }
  }

  /** Jede Farbe, die der Inhaltsstrom setzt, als ihre drei Anteile. */
  private static List<String> farben(final String strom) {
    final List<String> farben = new ArrayList<>();
    final Matcher treffer = FARBE.matcher(strom);
    while (treffer.find()) {
      farben.add(treffer.group(1));
    }
    return farben;
  }

  /**
   * Die Zeilen der ersten Seite mit dem Abstand ihrer Grundlinie von der unteren Blattkante, in
   * Punkten.
   */
  private static Map<String, Float> grundlinien(final byte[] pdf) throws IOException {
    final Map<String, Float> zeilen = new LinkedHashMap<>();
    try (PDDocument dokument = Loader.loadPDF(pdf)) {
      final float hoehe = dokument.getPage(0).getMediaBox().getHeight();
      final PDFTextStripper leser =
          new PDFTextStripper() {
            @Override
            protected void writeString(final String text, final List<TextPosition> zeichen) {
              zeilen.put(text, Float.valueOf(hoehe - zeichen.getFirst().getYDirAdj()));
            }
          };
      leser.setStartPage(1);
      leser.setEndPage(1);
      leser.getText(dokument);
    }
    return zeilen;
  }

  /**
   * Der Beleg mit genullter Kennung im Trailer. Die Kennung behaelt ihre Laenge, damit alle
   * Verweise im Dokument stehen bleiben; verglichen wird jedes andere Byte.
   */
  private static byte[] ohneKennung(final byte[] pdf) {
    final Matcher kennung = KENNUNG.matcher(new String(pdf, ISO_8859_1));
    final byte[] genullt = pdf.clone();
    while (kennung.find()) {
      for (int gruppe = 1; gruppe <= 2; gruppe++) {
        for (int stelle = kennung.start(gruppe); stelle < kennung.end(gruppe); stelle++) {
          genullt[stelle] = '0';
        }
      }
    }
    return genullt;
  }

  /** Zwei wirklich gleichzeitige Stellen-Aufrufe auf denselben Entwurf. */
  private List<HttpStatusCode> zweiMalGleichzeitig(final long rechnungId) throws Exception {
    final CountDownLatch start = new CountDownLatch(1);
    try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
      final Future<HttpStatusCode> erster =
          pool.submit(() -> nachDemStartschuss(start, rechnungId));
      final Future<HttpStatusCode> zweiter =
          pool.submit(() -> nachDemStartschuss(start, rechnungId));
      start.countDown();
      return List.of(
          erster.get(GEDULD.toSeconds(), TimeUnit.SECONDS),
          zweiter.get(GEDULD.toSeconds(), TimeUnit.SECONDS));
    }
  }

  private HttpStatusCode nachDemStartschuss(final CountDownLatch start, final long rechnungId)
      throws InterruptedException {
    start.await();
    return stelle(rechnungId).getStatusCode();
  }

  @Test
  void stellen_givenTwoEntwuerfe_thenEachGetsItsNummerAndTheAngebotEndsAbgerechnet() {
    // Given — 160 angebotene Stunden, zwei Entwuerfe ueber je 80; beide liegen schon.
    final long angebotId = bestelltesAngebot("160.00");
    final RechnungResponse erster = entwurfUeber(angebotId, "80.00");
    final RechnungResponse zweiter = entwurfUeber(angebotId, "80.00");

    // When — der erste wird gestellt, waehrend der zweite noch Entwurf ist.
    final RechnungResponse eine = gestellt(erster.id());

    // Then — Entwuerfe zaehlen fuer den Sprung nicht mit (Kriterium 27).
    assertThat(eine.nummer()).isEqualTo(nummer(1));
    assertThat(eine.zustand()).isEqualTo(Rechnungszustand.GESTELLT);
    assertThat(angebotsstatus(angebotId)).isEqualTo(Angebotsstatus.BESTELLT);

    // When — jetzt der zweite.
    final RechnungResponse andere = gestellt(zweiter.id());

    // Then — nichts mehr offen: das Angebot ist abgerechnet, der Zaehler steht bei 3.
    assertThat(andere.nummer()).isEqualTo(nummer(2));
    assertThat(angebotsstatus(angebotId)).isEqualTo(Angebotsstatus.ABGERECHNET);
    assertThat(naechsteNummer()).isEqualTo(3);
  }

  @Test
  void stellen_thenTheBelegLiesInTheObjektspeicherAndCarriesItsAngaben() throws Exception {
    // Given
    final long angebotId = bestelltesAngebot("160.00");
    final RechnungResponse entwurf = entwurfUeber(angebotId, "80.00");
    final int vorher = objekteZu(entwurf.id());

    // When
    final long rechnungId = gestellt(entwurf.id()).id();

    // Then — genau ein neues Objekt, und sein Text nennt, was auf einem Beleg stehen muss.
    assertThat(objekteZu(rechnungId)).isEqualTo(vorher + 1);
    assertThat(pdfSchluessel(rechnungId)).startsWith("rechnung/" + rechnungId + "/");
    assertThat(belegtext(rechnungId))
        .contains("Rechnung " + nummer(1))
        .contains("Sehr geehrte Damen und Herren,")
        .contains(ZEITRAUM)
        .contains("8.000,00 €")
        .contains("1.520,00 €")
        .contains("9.520,00 €")
        .contains("Bitte überweisen Sie den Betrag innerhalb von 10 Tagen");
  }

  @Test
  void stelle_givenBriefpapierUndSteuerBankZeile_thenRechnungAufEigenemBriefpapier()
      throws IOException {
    // Given — Szenario „Rechnung auf eigenem Briefpapier": einseitig, A4 hochkant; die Steuer- und
    // Bankzeile ist nach der Migration eingeschaltet. Allein die Steuernummer, damit sie in der
    // Zeile steht und nicht die USt-IdNr. (Kriterium 6).
    eigeneAngabenSetzen(STEUERNUMMER, null, BANKVERBINDUNG);
    ladeBriefpapierHoch(briefpapier(BRIEFKOPF));
    final RechnungResponse entwurf =
        entwurfUeberAlleZeilen(bestelltesAngebotMitDreiPositionen(), "10.00");
    assertThat(entwurf.zeilen()).hasSize(3);

    // When
    final byte[] pdf = beleg(gestellt(entwurf.id()).id());

    // Then — das Briefpapier und darauf, was zur Rechnung gehoert.
    final String text;
    try (PDDocument dokument = Loader.loadPDF(pdf)) {
      assertThat(dokument.getNumberOfPages()).isEqualTo(1);
      text = new PDFTextStripper().getText(dokument);
    }
    assertThat(text)
        .contains(BRIEFKOPF)
        .contains(FIRMENNAME, "Hauptstrasse 1", "28195 Bremen")
        .contains("Rechnung " + nummer(1))
        .contains("Beratung", "Konzeption", "Umsetzung")
        .contains("Gesamtbetrag netto", "Gesamtbetrag brutto", "3.570,00 €")
        .contains("Mit freundlichen Grüßen", EIGENER_NAME);

    // Then — Kopf und Fuss der Kupferwolke fehlen: die Berufsbezeichnung des Kopfes, die
    // Webadresse aus Kopf und Fuss.
    assertThat(text).doesNotContain("Softwarearchitekt").doesNotContain("https://example.org");

    // Then — alles Eingedruckte ist schwarz, und keine Flaeche: kein Randstreifen.
    final String strom = eingedruckt(pdf);
    assertThat(farben(strom)).isNotEmpty().containsOnly("0 0 0");
    assertThat(strom).doesNotContain(" re\n");

    // Then — die Steuer- und Bankzeile steht ueber dem unteren Rand, unter allem anderen.
    final Map<String, Float> zeilen = grundlinien(pdf);
    final String steuerBank =
        "Steuernummer " + STEUERNUMMER + " · Bankverbindung: " + BANKVERBINDUNG;
    assertThat(zeilen).containsKey(steuerBank);
    final float ueberDemBlattrand = zeilen.get(steuerBank).floatValue();
    assertThat((double) ueberDemBlattrand).isGreaterThan(UNTERER_RAND);
    zeilen.remove(steuerBank);
    assertThat(zeilen.values())
        .allSatisfy(hoehe -> assertThat(hoehe).isGreaterThan(ueberDemBlattrand));
  }

  @Test
  void stelle_givenKeinBriefpapier_thenOhneBriefpapierWieHeute() {
    // Given — Szenario „Ohne Briefpapier": der Briefbogen steht auf dem Stand der Migration.
    final RechnungResponse entwurf = entwurfUeber(bestelltesAngebot("160.00"), "80.00");

    // When
    final long rechnungId = gestellt(entwurf.id()).id();

    // Then — Kriterium 11: derselbe Beleg wie der Druck der Kupferwolke ohne Untergrund, Byte fuer
    // Byte bis auf die Kennung im Trailer, die PDFBox bei jedem Schreiben neu bildet.
    final Rechnung gestellt = rechnungen.findById(rechnungId).orElseThrow();
    final byte[] heute = drucker.drucke(Kupferwolkensatz.von(gestellt), null);
    assertThat(ohneKennung(beleg(rechnungId))).isEqualTo(ohneKennung(heute));
  }

  @Test
  void stellen_withAnUnknownRechnung_thenNotFound() {
    // When / Then
    assertThat(stelle(999_999L).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(naechsteNummer()).isEqualTo(1);
  }

  @Test
  void stellen_withoutTheBankverbindung_thenUnprocessableAndTheZaehlerIsUnchanged() {
    // Given — ohne Bankverbindung kann der Kunde nicht zahlen (Kriterium 13).
    final long angebotId = bestelltesAngebot("160.00");
    final RechnungResponse entwurf = entwurfUeber(angebotId, "80.00");
    eigeneAngabenSetzen(STEUERNUMMER, UMSATZSTEUER_ID, null);

    // When
    final ResponseEntity<String> antwort = stelle(entwurf.id());

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(antwort.getBody()).contains("\"fieldErrors\"").contains("\"bankverbindung\"");
    assertThat(naechsteNummer()).isEqualTo(1);
  }

  @Test
  void stellen_withoutSteuernummerAndUmsatzsteuerId_thenTheSteuernummerIsNamed() {
    // Given — eine von beiden muss auf dem Beleg stehen.
    final long angebotId = bestelltesAngebot("160.00");
    final RechnungResponse entwurf = entwurfUeber(angebotId, "80.00");
    eigeneAngabenSetzen(null, null, BANKVERBINDUNG);

    // When
    final ResponseEntity<String> antwort = stelle(entwurf.id());

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(antwort.getBody()).contains("\"fieldErrors\"").contains("\"steuernummer\"");
    assertThat(naechsteNummer()).isEqualTo(1);
  }

  @Test
  void stellen_withOnlyTheUmsatzsteuerId_thenTheRechnungIsIssued() {
    // Given — welche der beiden Angaben ein Freiberufler fuehrt, bleibt ihm ueberlassen.
    final long angebotId = bestelltesAngebot("160.00");
    final RechnungResponse entwurf = entwurfUeber(angebotId, "80.00");
    eigeneAngabenSetzen(null, UMSATZSTEUER_ID, BANKVERBINDUNG);

    // When / Then
    assertThat(gestellt(entwurf.id()).nummer()).isEqualTo(nummer(1));
  }

  @Test
  void stellen_withOnlyTheSteuernummer_thenTheRechnungIsIssued() {
    // Given
    final long angebotId = bestelltesAngebot("160.00");
    final RechnungResponse entwurf = entwurfUeber(angebotId, "80.00");
    eigeneAngabenSetzen(STEUERNUMMER, null, BANKVERBINDUNG);

    // When / Then
    assertThat(gestellt(entwurf.id()).nummer()).isEqualTo(nummer(1));
  }

  @Test
  void stellen_withAFirmaWithoutOrt_thenTheFirmenfeldIsNamed() {
    // Given — eine Rechnung ohne Anschrift des Empfaengers geht an niemanden.
    final long angebotId = bestelltesAngebot("160.00");
    final RechnungResponse entwurf = entwurfUeber(angebotId, "80.00");
    firmaSetzen(FIRMENNAME, "Hauptstrasse 1", "28195", null);

    // When
    final ResponseEntity<String> antwort = stelle(entwurf.id());

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(antwort.getBody()).contains("\"fieldErrors\"").contains("\"firma.ort\"");
    assertThat(naechsteNummer()).isEqualTo(1);
  }

  @Test
  void stellen_withoutAPosition_thenUnprocessable() {
    // Given — alle Mengen auf 0: der Entwurf traegt keine Position mehr (Kriterium 5).
    final long angebotId = bestelltesAngebot("160.00");
    final RechnungResponse entwurf = entwurfUeber(angebotId, "0");

    // When
    final ResponseEntity<String> antwort = stelle(entwurf.id());

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(naechsteNummer()).isEqualTo(1);
  }

  @Test
  void stellen_twiceInARow_thenConflict() {
    // Given — eine zweite Nummer auf derselben Rechnung risse ein Loch in den Nummernkreis.
    final long angebotId = bestelltesAngebot("160.00");
    final long rechnungId = gestellt(entwurfUeber(angebotId, "80.00").id()).id();

    // When
    final ResponseEntity<String> antwort = stelle(rechnungId);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(naechsteNummer()).isEqualTo(2);
  }

  @Test
  void stellen_withAnAlreadyTakenNummer_thenConflictAndNoDocument() {
    // Given — der Zaehler wird von Hand zurueckgesetzt (Kriterium 15) und trifft die 0001.
    final long angebotId = bestelltesAngebot("160.00");
    gestellt(entwurfUeber(angebotId, "80.00").id());
    final RechnungResponse zweiter = entwurfUeber(angebotId, "80.00");
    final int vorher = objekteZu(zweiter.id());
    jdbc.update(
        "UPDATE rechnung_nummernkreis SET naechste_nummer = 1 WHERE jahr = ?",
        Integer.valueOf(heute.getYear()));

    // When
    final ResponseEntity<String> antwort = stelle(zweiter.id());

    // Then — 409 mit der Nummer, kein neues Dokument, und der Entwurf bleibt ein Entwurf.
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(antwort.getBody()).contains(nummer(1));
    assertThat(objekteZu(zweiter.id())).isEqualTo(vorher);
    assertThat(lese(zweiter.id()).zustand()).isEqualTo(Rechnungszustand.ENTWURF);
  }

  @Test
  void stellen_twiceAtOnce_thenExactlyOneSucceedsAndOneNummerIsUsed() throws Exception {
    // Given — ohne Sperre auf der Zeile stellten beide Aufrufe, und eine Nummer verschwaende.
    final long angebotId = bestelltesAngebot("160.00");
    final RechnungResponse entwurf = entwurfUeber(angebotId, "80.00");
    final int vorher = objekteZu(entwurf.id());

    // When
    final List<HttpStatusCode> antworten = zweiMalGleichzeitig(entwurf.id());

    // Then — genau eine Nummer verbraucht und genau ein Beleg entstanden.
    assertThat(antworten).containsExactlyInAnyOrder(HttpStatus.OK, HttpStatus.CONFLICT);
    assertThat(naechsteNummer()).isEqualTo(2);
    assertThat(objekteZu(entwurf.id())).isEqualTo(vorher + 1);
  }

  @Test
  void stellen_givenRechnungAufVeraltetemStand_thenNothingIsGestelltAndAnnaIsNamed() {
    // Given — #411, Szenario „Rechnung auf veraltetem Stand stellen": Manfred hat den Entwurf
    // geoeffnet, Anna aendert inzwischen eine Menge darin.
    final HttpHeaders anna = zweitesKonto("anna@example.org", "Anna");
    final long angebotId = bestelltesAngebot("160.00");
    final RechnungResponse geoeffnet = entwurfUeber(angebotId, "80.00");
    final int vorher = objekteZu(geoeffnet.id());
    assertThat(
            rest.exchange(
                    "/api/rechnungen/" + geoeffnet.id(),
                    HttpMethod.PUT,
                    new HttpEntity<>(
                        mengenrumpf(geoeffnet, "60.00"),
                        Stand.aktuell(anna, jdbc, "rechnung", geoeffnet.id())),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);

    // When — Manfred stellt auf dem Stand, den er geoeffnet hat.
    final ResponseEntity<JsonNode> antwort =
        rest.exchange(
            "/api/rechnungen/" + geoeffnet.id() + "/stellen",
            HttpMethod.POST,
            new HttpEntity<>(null, aufStand(sitzung, geoeffnet.version())),
            JsonNode.class);

    // Then — nichts gestellt; Manfred liest, dass Anna den Entwurf inzwischen geaendert hat.
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    final JsonNode konflikt = Objects.requireNonNull(antwort.getBody()).path("konflikt");
    assertThat(konflikt.path("art").asText()).isEqualTo("veraltet");
    assertThat(konflikt.path("geaendertVon").asText()).isEqualTo("Anna");
    assertThat(konflikt.path("eigenesFenster").asBoolean()).isFalse();
    final RechnungResponse danach = lese(geoeffnet.id());
    assertThat(danach.zustand()).isEqualTo(Rechnungszustand.ENTWURF);
    assertThat(danach.nummer()).isNull();
    assertThat(danach.zeilen().getFirst().menge()).isEqualByComparingTo("60.00");
    // Der Nummernkreis bleibt auf seinem Stand, und kein Dokument entsteht.
    assertThat(naechsteNummer()).isEqualTo(1);
    assertThat(objekteZu(geoeffnet.id())).isEqualTo(vorher);
  }

  @Test
  void stellen_whenAngebotAndAngabenChangedSinceOpening_thenGestelltWithTheirCurrentStand() {
    // Given — #411, Kriterium 5: Manfred oeffnet den Entwurf ueber alle 160 Stunden. Danach aendert
    // Anna das Angebot, und Einstellungen, Eigene Angaben und Briefbogen werden neu gefasst.
    final HttpHeaders anna = zweitesKonto("anna@example.org", "Anna");
    final long angebotId = bestelltesAngebot("160.00");
    final RechnungResponse geoeffnet = entwurfUeber(angebotId, "160.00");
    final Map<String, Object> angebot = new LinkedHashMap<>();
    angebot.put("angebotDatum", heute.toString());
    angebot.put("titel", "Website-Relaunch, zweite Fassung");
    angebot.put("ansprechpartnerId", null);
    angebot.put("beschreibung", "Neugestaltung der Website");
    angebot.put(
        "positionen",
        List.of(
            angebotsposition(
                Long.valueOf(geoeffnet.zeilen().getFirst().angebotPositionId()),
                "Beratung",
                "160.00")));
    assertThat(
            rest.exchange(
                    "/api/angebote/" + angebotId,
                    HttpMethod.PUT,
                    new HttpEntity<>(angebot, Stand.aktuell(anna, jdbc, "angebot", angebotId)),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    einstellungenSetzen(5, "7.00", 21);
    eigeneAngabenSetzen(STEUERNUMMER, UMSATZSTEUER_ID, "DE89 3704 0044 0532 0130 00");
    final Map<String, Object> bogen = new LinkedHashMap<>();
    bogen.put("absenderzeile", false);
    bogen.put("steuerBankZeile", false);
    bogen.put("links", Integer.valueOf(250));
    bogen.put("rechts", Integer.valueOf(200));
    bogen.put("inhaltOben", Integer.valueOf(985));
    bogen.put("unten", Integer.valueOf(200));
    bogen.put("folgeOben", Integer.valueOf(200));
    bogen.put("folgeUnten", Integer.valueOf(200));
    assertThat(
            rest.exchange(
                    "/api/briefpapier",
                    HttpMethod.PUT,
                    new HttpEntity<>(bogen, Stand.aktuell(sitzung, jdbc, "briefbogen", 1L)),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);

    // When — Manfred stellt auf dem Stand des Entwurfs, den er geoeffnet hat.
    final ResponseEntity<RechnungResponse> antwort =
        rest.exchange(
            "/api/rechnungen/" + geoeffnet.id() + "/stellen",
            HttpMethod.POST,
            new HttpEntity<>(null, aufStand(sitzung, geoeffnet.version())),
            RechnungResponse.class);

    // Then — gestellt mit allem, wie es jetzt gilt; das Angebot ist abgerechnet und traegt
    // Manfred als Urheber, nicht Anna.
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
    final RechnungResponse gestellt = Objects.requireNonNull(antwort.getBody());
    assertThat(gestellt.nummer()).isEqualTo(nummer(5));
    assertThat(gestellt.steuersatz()).isEqualByComparingTo("7.00");
    assertThat(gestellt.zahlungszielTage()).isEqualTo(21);
    assertThat(Objects.requireNonNull(gestellt.absender()).bankverbindung())
        .isEqualTo("DE89 3704 0044 0532 0130 00");
    assertThat(gestellt.version()).isGreaterThan(geoeffnet.version());
    assertThat(angebotsstatus(angebotId)).isEqualTo(Angebotsstatus.ABGERECHNET);
    assertThat(
            jdbc.queryForObject(
                "SELECT geaendert_von FROM angebot WHERE id = ?",
                Long.class,
                Long.valueOf(angebotId)))
        .isEqualTo(kontoZu(MAIL));
    assertThat(
            jdbc.queryForObject(
                "SELECT geaendert_von FROM rechnung WHERE id = ?",
                Long.class,
                Long.valueOf(geoeffnet.id())))
        .isEqualTo(kontoZu(MAIL));
  }

  @Test
  void lesen_afterEverythingAroundItChanged_thenTheGestellteRechnungIsUnchanged() {
    // Given — gestellt mit 19 Prozent, 10 Tagen und der Firma „Adler AG" in Bremen.
    final long angebotId = bestelltesAngebot("160.00");
    final RechnungResponse entwurf = entwurfUeber(angebotId, "80.00");
    final long positionId = entwurf.zeilen().getFirst().angebotPositionId();
    final RechnungResponse gestellt = gestellt(entwurf.id());

    // When — danach aendert sich alles, woraus der Beleg entstanden ist (Kriterium 14).
    firmaSetzen("Adler Holding SE", "Neue Strasse 9", "20095", "Hamburg");
    positionSetzen(
        angebotId, Long.valueOf(positionId), "Beratung, neu benannt", "160.00", "120.00");
    eigeneAngabenSetzen("99/999/99999", "DE999999999", "DE99 9999 9999 9999 9999 99");
    einstellungenSetzen(2, "7.00", 30);

    // Then — die gestellte Rechnung zeigt weiter, was der Kunde gelesen hat.
    final RechnungResponse danach = lese(gestellt.id());
    assertThat(danach)
        .satisfies(
            rechnung -> assertThat(rechnung.nummer()).isEqualTo(nummer(1)),
            rechnung -> assertThat(rechnung.steuersatz()).isEqualByComparingTo("19.00"),
            rechnung -> assertThat(rechnung.zahlungszielTage()).isEqualTo(10),
            rechnung -> assertThat(rechnung.firmaName()).isEqualTo(FIRMENNAME),
            rechnung -> assertThat(rechnung.netto()).isEqualByComparingTo("8000.00"),
            rechnung -> assertThat(rechnung.brutto()).isEqualByComparingTo("9520.00"));
    assertThat(Objects.requireNonNull(danach.empfaenger()))
        .satisfies(
            kopie -> assertThat(kopie.firma()).isEqualTo(FIRMENNAME),
            kopie -> assertThat(kopie.strasse()).isEqualTo("Hauptstrasse 1"),
            kopie -> assertThat(kopie.plz()).isEqualTo("28195"),
            kopie -> assertThat(kopie.ort()).isEqualTo("Bremen"));
    assertThat(Objects.requireNonNull(danach.absender()))
        .satisfies(
            kopie -> assertThat(kopie.name()).isEqualTo(EIGENER_NAME),
            kopie -> assertThat(kopie.steuernummer()).isEqualTo(STEUERNUMMER),
            kopie -> assertThat(kopie.umsatzsteuerId()).isEqualTo(UMSATZSTEUER_ID),
            kopie -> assertThat(kopie.bankverbindung()).isEqualTo(BANKVERBINDUNG));
    assertThat(danach.zeilen().getFirst())
        .satisfies(
            zeile -> assertThat(zeile.bezeichnung()).isEqualTo("Beratung"),
            zeile -> assertThat(zeile.menge()).isEqualByComparingTo("80.00"),
            zeile -> assertThat(zeile.einzelpreis()).isEqualByComparingTo("100.00"));
  }

  @Test
  void aendernUndLoeschen_atAGestellteRechnung_thenConflict() {
    // Given
    final long angebotId = bestelltesAngebot("160.00");
    final RechnungResponse entwurf = entwurfUeber(angebotId, "80.00");
    final RechnungResponse gestellt = gestellt(entwurf.id());

    // When / Then — eine gestellte Rechnung ist festgeschrieben (Kriterium 14).
    assertThat(setzeMenge(gestellt, "40.00").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(
            rufAufStand(
                    "/api/rechnungen/" + gestellt.id(),
                    HttpMethod.DELETE,
                    null,
                    String.class,
                    gestellt.id())
                .getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void stellen_withoutASitzung_thenUnauthorized() {
    // Given — derselbe Weg, nur ohne das Sitzungs-Cookie.
    sitzung = new HttpHeaders();

    // When / Then
    assertThat(stelle(1L).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
