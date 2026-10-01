package org.mwolff.fbcrm.rechnung;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.mwolff.fbcrm.rechnung.web.RechnungseinstellungenResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Die Vergabe der Rechnungsnummer ueber HTTP, gegen eine echte PostgreSQL- und MinIO-Instanz (#160,
 * Kriterien 16 bis 18; Plan #169).
 *
 * <p>Das Stellen selbst steht in {@code RechnungStellenIT}. Hier steht nur, <b>welche</b> Nummer
 * dabei herauskommt — und das ist in keinem Unit-Test zu sehen: Das Zaehlerjahr haengt am
 * Rechnungsdatum des Entwurfs, der Zaehler steht in der Datenbank, und ob zwei gleichzeitige
 * Aufrufe zwei verschiedene Nummern bekommen, entscheidet eine Zeilensperre in PostgreSQL.
 *
 * <p>Gefahren wird ausschliesslich ueber die Schnittstelle: Das Rechnungsdatum setzt der Test per
 * {@code PUT} am Entwurf, die naechste Nummer per {@code PUT} an den Einstellungen. Nur der
 * Jahreswechsel laesst sich so nicht herbeifuehren, ohne zu warten — darum traegt der Entwurf ein
 * Datum im Folgejahr, und das ist fachlich dasselbe: Das Zaehlerjahr kommt aus dem Rechnungsdatum
 * und nicht aus dem heutigen Tag (Kriterium 17).
 *
 * <p>Kein Beleg zum Inhalt des Dokuments und keiner zu den Abweisungen — beides steht in {@code
 * RechnungStellenIT}, und es zweimal zu fahren kostete Zeit ohne eine zweite Aussage.
 */
class RechnungNummerIT extends AbstractIntegrationTest {

  private static final String MAIL = "manne@example.org";
  private static final String PASSWORT = "richtiges-passwort";
  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");

  private static final String STUNDENSATZ = "100.00";
  private static final String ZEITRAUM = "September 2026";
  private static final String FIRMENNAME = "Adler AG";
  private static final String MIT_JAHR = "{NNNN}-{JJJJ}";
  private static final String OHNE_JAHR = "{NNNN}";
  private static final Duration GEDULD = Duration.ofSeconds(30);

  /**
   * Das laufende Jahr in der Geschaeftszone; das Folgejahr ist das des Jahreswechsels.
   *
   * <p>Der heutige Tag und nicht ein fester: Die Maske der Einstellungen zeigt den Zaehler des
   * laufenden Jahres, und ein festes Datum liesse den Test am Jahreswechsel auf zwei Zaehler
   * schauen. Als Feld der Instanz und nicht als Konstante — die Zeit gehoert nicht in einen
   * statischen Initialisierer.
   */
  private final LocalDate heute = LocalDate.now(Geschaeftszone.ZONE);

  private final TestRestTemplate rest;
  private final AccountRepository accounts;
  private final PasswordHasher hasher;
  private final JdbcTemplate jdbc;

  private HttpHeaders sitzung = new HttpHeaders();
  private long firmaId;

  @Autowired
  RechnungNummerIT(
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
    eigeneAngabenSetzen("75/123/45678");
    firmaId = firmaAnlegen();
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

  /** Die eigenen Angaben, vollstaendig bis auf die Steuernummer, die der Test waehlt. */
  private void eigeneAngabenSetzen(final @Nullable String steuernummer) {
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
    rumpf.put("umsatzsteuerId", null);
    rumpf.put("bankverbindung", "DE02 1203 0000 0000 2020 51");
    ruf("/api/eigene-angaben", HttpMethod.PUT, rumpf, Void.class);
  }

  private long firmaAnlegen() {
    final Map<String, Object> rumpf = new LinkedHashMap<>();
    rumpf.put("name", FIRMENNAME);
    rumpf.put("strasse", "Hauptstrasse 1");
    rumpf.put("plz", "28195");
    rumpf.put("ort", "Bremen");
    rumpf.put("land", "Deutschland");
    rumpf.put("steuernummer", null);
    rumpf.put("umsatzsteuerId", null);
    ruf("/api/firmen", HttpMethod.POST, rumpf, String.class);
    return Objects.requireNonNull(
            jdbc.queryForObject("SELECT id FROM firma WHERE name = ?", Long.class, FIRMENNAME))
        .longValue();
  }

  private void einstellungenSetzen(final String muster, final int naechsteNummer) {
    final Map<String, Object> rumpf = new LinkedHashMap<>();
    rumpf.put("nummerMuster", muster);
    rumpf.put("naechsteNummer", Integer.valueOf(naechsteNummer));
    rumpf.put("steuersatz", "19.00");
    rumpf.put("zahlungszielTage", Integer.valueOf(10));
    assertThat(
            ruf("/api/rechnung/einstellungen", HttpMethod.PUT, rumpf, String.class).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
  }

  /** Die naechste laufende Nummer des laufenden Jahres, wie die Maske sie zeigt. */
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

  /** Die Nummer, die {@value #MIT_JAHR} aus laufender Nummer und Jahr bildet. */
  private static String nummer(final int laufend, final int jahr) {
    return "%04d-%d".formatted(Integer.valueOf(laufend), Integer.valueOf(jahr));
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
    final Map<String, Object> position = new LinkedHashMap<>();
    position.put("id", null);
    position.put("bezeichnung", "Beratung");
    position.put("abrechnungsmodus", "AUFWAND");
    position.put("menge", menge);
    position.put("einheit", "STUNDE");
    position.put("einzelpreis", STUNDENSATZ);
    final Map<String, Object> rumpf = new LinkedHashMap<>();
    rumpf.put("angebotDatum", heute.toString());
    rumpf.put("ansprechpartnerId", null);
    rumpf.put("beschreibung", "Neugestaltung der Website");
    rumpf.put("positionen", List.of(position));
    ruf("/api/angebote/" + angebotId, HttpMethod.PUT, rumpf, AngebotResponse.class);
    ruf("/api/angebote/" + angebotId + "/status/weiter", HttpMethod.POST, null, String.class);
    ruf("/api/angebote/" + angebotId + "/status/weiter", HttpMethod.POST, null, String.class);
    return angebotId;
  }

  /** Ein Entwurf zum Angebot, auf Menge und Rechnungsdatum gesetzt. */
  private long entwurf(final long angebotId, final String menge, final LocalDate rechnungDatum) {
    final RechnungResponse angelegt =
        Objects.requireNonNull(
            ruf(
                    "/api/angebote/" + angebotId + "/rechnungen",
                    HttpMethod.POST,
                    null,
                    RechnungResponse.class)
                .getBody());
    final Map<String, Object> position = new LinkedHashMap<>();
    position.put(
        "angebotPositionId", Long.valueOf(angelegt.zeilen().getFirst().angebotPositionId()));
    position.put("bezeichnung", "Beratung");
    position.put("menge", menge);
    final Map<String, Object> rumpf = new LinkedHashMap<>();
    rumpf.put("rechnungDatum", rechnungDatum.toString());
    rumpf.put("leistungszeitraum", ZEITRAUM);
    rumpf.put("positionen", List.of(position));
    assertThat(
            ruf("/api/rechnungen/" + angelegt.id(), HttpMethod.PUT, rumpf, RechnungResponse.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    return angelegt.id();
  }

  private ResponseEntity<String> stelle(final long rechnungId) {
    return ruf("/api/rechnungen/" + rechnungId + "/stellen", HttpMethod.POST, null, String.class);
  }

  /** Stellt und liefert die Nummer; ein anderer Ausgang als 200 ist hier ein Fehlschlag. */
  private String stelleUndNimmDieNummer(final long rechnungId) {
    final ResponseEntity<RechnungResponse> antwort =
        ruf(
            "/api/rechnungen/" + rechnungId + "/stellen",
            HttpMethod.POST,
            null,
            RechnungResponse.class);
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
    return Objects.requireNonNull(Objects.requireNonNull(antwort.getBody()).nummer());
  }

  /** Die Nummern aus zwei wirklich gleichzeitigen Stellen-Aufrufen auf zwei Entwuerfe. */
  private List<String> gleichzeitig(final long eine, final long andere) throws Exception {
    final CountDownLatch start = new CountDownLatch(1);
    try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
      final Future<String> erster = pool.submit(() -> nachDemStartschuss(start, eine));
      final Future<String> zweiter = pool.submit(() -> nachDemStartschuss(start, andere));
      start.countDown();
      return List.of(
          erster.get(GEDULD.toSeconds(), TimeUnit.SECONDS),
          zweiter.get(GEDULD.toSeconds(), TimeUnit.SECONDS));
    }
  }

  private String nachDemStartschuss(final CountDownLatch start, final long rechnungId)
      throws InterruptedException {
    start.await();
    return stelleUndNimmDieNummer(rechnungId);
  }

  @Test
  void stellen_withARechnungDatumInTheNextYear_thenTheCounterStartsAtOneAgain() {
    // Given — Kriterium 16: eine Rechnung im laufenden Jahr.
    final long angebotId = bestelltesAngebot("160.00");
    assertThat(stelleUndNimmDieNummer(entwurf(angebotId, "40.00", heute)))
        .isEqualTo(nummer(1, heute.getYear()));

    // When — die naechste traegt ein Rechnungsdatum im Folgejahr.
    final String imFolgejahr =
        stelleUndNimmDieNummer(entwurf(angebotId, "40.00", heute.plusYears(1)));

    // Then — jedes Jahr zaehlt fuer sich und beginnt wieder bei 1.
    assertThat(imFolgejahr).isEqualTo(nummer(1, heute.getYear() + 1));
  }

  @Test
  void stellen_afterTheNummerWasSetByHand_thenTheSetNummerWins() {
    // Given — Kriterium 15: der Anwender setzt den Zaehler des laufenden Jahres auf 4.
    final long angebotId = bestelltesAngebot("160.00");
    einstellungenSetzen(MIT_JAHR, 4);

    // When
    final String gezogen = stelleUndNimmDieNummer(entwurf(angebotId, "40.00", heute));

    // Then — die gesetzte Zahl gilt, und die Maske zeigt danach die 5.
    assertThat(gezogen).isEqualTo(nummer(4, heute.getYear()));
    assertThat(naechsteNummer()).isEqualTo(5);
  }

  @Test
  void stellen_withADezemberdatumAfterTheNewYearHasBegun_thenTheOldYearCountsOn() {
    // Given — Kriterium 17: im Folgejahr ist schon gestellt, und das alte Jahr traegt die 0001.
    final long angebotId = bestelltesAngebot("160.00");
    assertThat(stelleUndNimmDieNummer(entwurf(angebotId, "40.00", heute)))
        .isEqualTo(nummer(1, heute.getYear()));
    assertThat(stelleUndNimmDieNummer(entwurf(angebotId, "40.00", heute.plusYears(1))))
        .isEqualTo(nummer(1, heute.getYear() + 1));

    // When — eine Rechnung mit Datum im alten Jahr, gestellt nach der des neuen.
    final String nachgereicht = stelleUndNimmDieNummer(entwurf(angebotId, "40.00", heute));

    // Then — sie zaehlt im alten Jahr weiter und traegt dessen Jahreszahl.
    assertThat(nachgereicht).isEqualTo(nummer(2, heute.getYear()));
  }

  @Test
  void stellen_withAPatternWithoutAYear_thenOneCounterRunsThroughBothYears() {
    // Given — Kriterium 16: ohne Jahres-Platzhalter gibt es einen einzigen, durchlaufenden Kreis.
    final long angebotId = bestelltesAngebot("160.00");
    einstellungenSetzen(OHNE_JAHR, 1);

    // When — zwei Rechnungen mit Rechnungsdaten in zwei verschiedenen Jahren.
    final String imLaufenden = stelleUndNimmDieNummer(entwurf(angebotId, "40.00", heute));
    final String imFolgejahr =
        stelleUndNimmDieNummer(entwurf(angebotId, "40.00", heute.plusYears(1)));

    // Then — der Jahreswechsel setzt hier nichts zurueck.
    assertThat(imLaufenden).isEqualTo("0001");
    assertThat(imFolgejahr).isEqualTo("0002");
  }

  @Test
  void stellen_twoDifferentEntwuerfeAtOnce_thenBothSucceedWithConsecutiveNummern()
      throws Exception {
    // Given — Kriterium 18, und zwar im ersten Zug eines Jahres: Fuer das Folgejahr gibt es noch
    // keine Zeile im Nummernkreis, beide Aufrufe muessen sie anlegen wollen.
    final long angebotId = bestelltesAngebot("160.00");
    final long eine = entwurf(angebotId, "40.00", heute.plusYears(1));
    final long andere = entwurf(angebotId, "40.00", heute.plusYears(1));

    // When
    final List<String> nummern = gleichzeitig(eine, andere);

    // Then — zwei verschiedene, aufeinanderfolgende Nummern; keiner der beiden faellt aus.
    assertThat(nummern)
        .containsExactlyInAnyOrder(nummer(1, heute.getYear() + 1), nummer(2, heute.getYear() + 1));
  }

  @Test
  void stellen_afterAFailedAttempt_thenNoNummerWasConsumed() {
    // Given — ohne Steuernummer und ohne Umsatzsteuer-ID fehlt eine Pflichtangabe des Belegs.
    final long angebotId = bestelltesAngebot("160.00");
    final long gescheitert = entwurf(angebotId, "40.00", heute);
    eigeneAngabenSetzen(null);

    // When
    assertThat(stelle(gescheitert).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

    // Then — die naechste erfolgreiche Rechnung traegt die lueckenlos folgende Nummer.
    eigeneAngabenSetzen("75/123/45678");
    assertThat(stelleUndNimmDieNummer(gescheitert)).isEqualTo(nummer(1, heute.getYear()));
    assertThat(naechsteNummer()).isEqualTo(2);
  }
}
