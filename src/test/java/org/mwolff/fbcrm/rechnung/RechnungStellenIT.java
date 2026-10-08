package org.mwolff.fbcrm.rechnung;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;
import org.mwolff.fbcrm.angebot.web.AngebotResponse;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.PasswordHasher;
import org.mwolff.fbcrm.auth.domain.Role;
import org.mwolff.fbcrm.common.Geschaeftszone;
import org.mwolff.fbcrm.config.MinioProperties;
import org.mwolff.fbcrm.rechnung.domain.DokumentSpeicher;
import org.mwolff.fbcrm.rechnung.domain.Rechnungszustand;
import org.mwolff.fbcrm.rechnung.web.RechnungResponse;
import org.mwolff.fbcrm.rechnung.web.RechnungseinstellungenResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
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
 */
class RechnungStellenIT extends AbstractIntegrationTest {

  private static final String MAIL = "manne@example.org";
  private static final String PASSWORT = "richtiges-passwort";
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

  private final TestRestTemplate rest;
  private final AccountRepository accounts;
  private final PasswordHasher hasher;
  private final JdbcTemplate jdbc;
  private final DokumentSpeicher dokumente;
  private final S3Client s3;
  private final MinioProperties zugang;

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
      final PasswordHasher hasher,
      final JdbcTemplate jdbc,
      final DokumentSpeicher dokumente,
      final S3Client s3,
      final MinioProperties zugang) {
    this.rest = rest;
    this.accounts = accounts;
    this.hasher = hasher;
    this.jdbc = jdbc;
    this.dokumente = dokumente;
    this.s3 = s3;
    this.zugang = zugang;
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
    eigeneAngabenSetzen(STEUERNUMMER, UMSATZSTEUER_ID, BANKVERBINDUNG);
    firmaId = firmaAnlegen("Hauptstrasse 1", "28195", "Bremen");
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
    rumpf.put("land", "Deutschland");
    rumpf.put("email", "post@example.org");
    rumpf.put("telefon", "0421 123456");
    rumpf.put("webadresse", "https://example.org");
    rumpf.put("steuernummer", steuernummer);
    rumpf.put("umsatzsteuerId", umsatzsteuerId);
    rumpf.put("bankverbindung", bankverbindung);
    ruf("/api/eigene-angaben", HttpMethod.PUT, rumpf, Void.class);
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
    ruf("/api/firmen/" + firmaId, HttpMethod.PUT, rumpf, Void.class);
  }

  private static Map<String, Object> firmenrumpf(
      final @Nullable String strasse, final @Nullable String plz, final @Nullable String ort) {
    final Map<String, Object> rumpf = new LinkedHashMap<>();
    rumpf.put("name", FIRMENNAME);
    rumpf.put("strasse", strasse);
    rumpf.put("plz", plz);
    rumpf.put("ort", ort);
    rumpf.put("land", "Deutschland");
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
    ruf("/api/rechnung/einstellungen", HttpMethod.PUT, rumpf, Void.class);
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
                        null,
                        AngebotResponse.class)
                    .getBody())
            .id();
    positionSetzen(angebotId, null, "Beratung", menge, STUNDENSATZ);
    ruf("/api/angebote/" + angebotId + "/status/weiter", HttpMethod.POST, null, String.class);
    ruf("/api/angebote/" + angebotId + "/status/weiter", HttpMethod.POST, null, String.class);
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
    return Objects.requireNonNull(setzeMenge(entwurf, menge).getBody());
  }

  private ResponseEntity<RechnungResponse> setzeMenge(
      final RechnungResponse entwurf, final String menge) {
    final Map<String, Object> position = new LinkedHashMap<>();
    position.put(
        "angebotPositionId", Long.valueOf(entwurf.zeilen().getFirst().angebotPositionId()));
    position.put("bezeichnung", "Beratung");
    position.put("menge", menge);
    final Map<String, Object> rumpf = new LinkedHashMap<>();
    rumpf.put("rechnungDatum", heute.toString());
    rumpf.put("leistungszeitraum", ZEITRAUM);
    rumpf.put("positionen", List.of(position));
    return ruf("/api/rechnungen/" + entwurf.id(), HttpMethod.PUT, rumpf, RechnungResponse.class);
  }

  private ResponseEntity<String> stelle(final long rechnungId) {
    return ruf("/api/rechnungen/" + rechnungId + "/stellen", HttpMethod.POST, null, String.class);
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
            ruf("/api/rechnungen/" + gestellt.id(), HttpMethod.DELETE, null, String.class)
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
