package org.mwolff.fbcrm.angebot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;
import org.mwolff.fbcrm.angebot.web.AngebotPositionResponse;
import org.mwolff.fbcrm.angebot.web.AngebotResponse;
import org.mwolff.fbcrm.angebot.web.FirmaAngeboteResponse;
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
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Der Lebensweg eines Angebots ueber HTTP, mit Sitzung und gegen eine echte PostgreSQL-Instanz:
 * anlegen, aendern, lesen, Status weiter und zurueck (Issue #127).
 *
 * <p>Drei Zusagen sind nur hier pruefbar, weil sie an der echten Transaktion und am echten Schema
 * haengen. Erstens E24: Nach einem {@code PUT} mit umgestellter Liste tragen die Positionszeilen
 * die Plaetze 1 bis n in der gesendeten Reihenfolge — das steht in der Spalte, nicht im Speicher.
 * Zweitens der Status: Er steht nach jedem Wechsel in der Spalte, und an den Enden antwortet der
 * Weg 409. Drittens die Wahl des Kunden (Issue #126): An einer stillgelegten Firma antwortet das
 * Anlegen 409, waehrend ein bestehendes Angebot sich weiterhin aendern laesst; ein Ansprechpartner
 * einer anderen Firma ist 422.
 *
 * <p><b>Dazu die Positionskennung ueber HTTP</b> (Plan #169, E2): Ein {@code PUT}, der sich auf
 * eine Position eines <i>anderen</i> Angebots beruft, antwortet 422, und die fremde Position bleibt
 * unberuehrt. Nur hier pruefbar, weil dazu zwei Angebote im echten Bestand stehen muessen.
 *
 * <p>Der Nachweis der Zugangsregel steht hier und nicht nur in {@code AccessRuleIT}: Ein neuer Pfad
 * unter {@code /api} ist ohne Sitzung verschlossen, und das gehoert zu jedem neuen Weg dazu.
 */
class AngebotIT extends AbstractIntegrationTest {

  private static final String MAIL = "manne@example.org";
  private static final String PASSWORT = "richtiges-passwort";
  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");

  private final TestRestTemplate rest;
  private final AccountRepository accounts;
  private final PasswordHasher hasher;
  private final JdbcTemplate jdbc;
  private final Clock clock;

  private HttpHeaders sitzung = new HttpHeaders();
  private long firmaId;
  private long ansprechpartnerId;
  private long fremderAnsprechpartnerId;

  @Autowired
  AngebotIT(
      final TestRestTemplate rest,
      final AccountRepository accounts,
      final PasswordHasher hasher,
      final JdbcTemplate jdbc,
      final Clock clock) {
    this.rest = rest;
    this.accounts = accounts;
    this.hasher = hasher;
    this.jdbc = jdbc;
    this.clock = clock;
  }

  @BeforeEach
  void leereDenBestandUndMeldeAn() {
    jdbc.execute(
        "TRUNCATE angebot_position, angebot, ansprechpartner, firma RESTART IDENTITY CASCADE");
    firmaId = firma("Adler AG");
    ansprechpartnerId = ansprechpartner(firmaId, "Adler");
    fremderAnsprechpartnerId = ansprechpartner(firma("Biber GmbH"), "Biber");
    jdbc.execute("TRUNCATE outbox_message, password_reset_token, account RESTART IDENTITY CASCADE");
    accounts.save(
        new Account(null, MAIL, "Manne", hasher.hash(PASSWORT), Role.ADMIN, 0, ANGELEGT, ANGELEGT));
    sitzung = angemeldeterKopf();
  }

  private long firma(final String name) {
    jdbc.update("INSERT INTO firma (name) VALUES (?)", name);
    return Objects.requireNonNull(
            jdbc.queryForObject("SELECT id FROM firma WHERE name = ?", Long.class, name))
        .longValue();
  }

  private long ansprechpartner(final long firma, final String nachname) {
    jdbc.update(
        "INSERT INTO ansprechpartner (firma_id, nachname) VALUES (?, ?)",
        Long.valueOf(firma),
        nachname);
    return Objects.requireNonNull(
            jdbc.queryForObject(
                "SELECT id FROM ansprechpartner WHERE nachname = ?", Long.class, nachname))
        .longValue();
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

  private static Map<String, Object> position(
      final String bezeichnung, final String modus, final String menge, final String einheit) {
    final Map<String, Object> felder = new LinkedHashMap<>();
    felder.put("bezeichnung", bezeichnung);
    felder.put("abrechnungsmodus", modus);
    felder.put("menge", menge);
    felder.put("einheit", einheit);
    felder.put("einzelpreis", "1000.01");
    return felder;
  }

  /** Dieselbe Position, aber mit einer Kennung: „schreib diese Zeile fort" (Plan #169, E2). */
  private static Map<String, Object> positionMitKennung(
      final long id, final String bezeichnung, final String modus, final String menge) {
    final Map<String, Object> felder = position(bezeichnung, modus, menge, "PERSONENTAG");
    felder.put("id", Long.valueOf(id));
    return felder;
  }

  private static Map<String, Object> rumpf(final List<Map<String, Object>> positionen) {
    final Map<String, Object> felder = new LinkedHashMap<>();
    felder.put("angebotDatum", "2026-09-25");
    felder.put("ansprechpartnerId", null);
    felder.put("beschreibung", "Neugestaltung der Website");
    felder.put("positionen", positionen);
    return felder;
  }

  private <T> ResponseEntity<T> anlegenMit(final long ansprechpartner, final Class<T> typ) {
    return ruf(
        "/api/firmen/" + firmaId + "/angebote",
        HttpMethod.POST,
        Map.of("ansprechpartnerId", Long.valueOf(ansprechpartner)),
        typ);
  }

  private AngebotResponse angebot() {
    return Objects.requireNonNull(
        ruf("/api/firmen/" + firmaId + "/angebote", HttpMethod.POST, null, AngebotResponse.class)
            .getBody());
  }

  private List<Long> positionskennungen(final long angebotId) {
    return jdbc.queryForList(
        "SELECT id FROM angebot_position WHERE angebot_id = ? ORDER BY position",
        Long.class,
        Long.valueOf(angebotId));
  }

  private List<String> bezeichnungenNachPlatz(final long angebotId) {
    return jdbc.queryForList(
        "SELECT bezeichnung FROM angebot_position WHERE angebot_id = ? ORDER BY position",
        String.class,
        Long.valueOf(angebotId));
  }

  @Test
  void anlegen_thenAnswersCreatedWithThePrefilledAngebot() {
    // Given — Angebotsdatum heute in der Geschaeftszone, Status ANGELEGT.
    final LocalDate heute = LocalDate.now(clock.withZone(Geschaeftszone.ZONE));

    // When
    final ResponseEntity<AngebotResponse> antwort =
        ruf("/api/firmen/" + firmaId + "/angebote", HttpMethod.POST, null, AngebotResponse.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    final AngebotResponse angelegt = Objects.requireNonNull(antwort.getBody());
    assertThat(angelegt.status()).isEqualTo(Angebotsstatus.ANGELEGT);
    assertThat(angelegt.angebotDatum()).isEqualTo(heute);
    assertThat(angelegt.beschreibung()).isNull();
    assertThat(angelegt.positionen()).isEmpty();
  }

  @Test
  void aendern_withAReorderedList_thenRenumbersThePositionsFromOne() {
    // Given — E24: die Plaetze entstehen aus der Reihenfolge der gesendeten Liste.
    final long angebotId = angebot().id();
    ruf(
        "/api/angebote/" + angebotId,
        HttpMethod.PUT,
        rumpf(
            List.of(
                position("Konzeption", "AUFWAND", "2.50", "PERSONENTAG"),
                position("Schulungstag", "FESTPREIS", "1.00", "PAUSCHAL"))),
        AngebotResponse.class);

    // When — dieselben Positionen, umgestellt.
    final ResponseEntity<AngebotResponse> antwort =
        ruf(
            "/api/angebote/" + angebotId,
            HttpMethod.PUT,
            rumpf(
                List.of(
                    position("Schulungstag", "FESTPREIS", "1.00", "PAUSCHAL"),
                    position("Konzeption", "AUFWAND", "2.50", "PERSONENTAG"))),
            AngebotResponse.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(bezeichnungenNachPlatz(angebotId)).containsExactly("Schulungstag", "Konzeption");
    assertThat(
            jdbc.queryForList(
                "SELECT position FROM angebot_position WHERE angebot_id = ? ORDER BY position",
                Integer.class,
                Long.valueOf(angebotId)))
        .containsExactly(Integer.valueOf(1), Integer.valueOf(2));
  }

  @Test
  void lesen_thenAnswersWithThePositionsInOrderAndTheCalculatedSumme() {
    // Given — Kriterium 5: 2,5 × 1.000,01 € = 2.500,03 €, plus 1 × 1.000,01 €.
    final long angebotId = angebot().id();
    ruf(
        "/api/angebote/" + angebotId,
        HttpMethod.PUT,
        rumpf(
            List.of(
                position("Konzeption", "AUFWAND", "2.50", "PERSONENTAG"),
                position("Schulungstag", "FESTPREIS", "1.00", "PAUSCHAL"))),
        AngebotResponse.class);

    // When
    final AngebotResponse gelesen =
        Objects.requireNonNull(
            ruf("/api/angebote/" + angebotId, HttpMethod.GET, null, AngebotResponse.class)
                .getBody());

    // Then
    assertThat(gelesen.positionen())
        .extracting(AngebotPositionResponse::bezeichnung)
        .containsExactly("Konzeption", "Schulungstag");
    assertThat(gelesen.summe()).isEqualByComparingTo(new BigDecimal("3500.04"));
    assertThat(gelesen.angebotDatum()).isEqualTo(LocalDate.of(2026, 9, 25));
    assertThat(gelesen.beschreibung()).isEqualTo("Neugestaltung der Website");
  }

  @Test
  void status_forwardThroughEveryStepAndBack_thenStoredAndBoundedByTheEnds() {
    // Given — Kriterium 4: frei weiter und zurueck, immer genau eine Stufe.
    final long angebotId = angebot().id();
    final String pfad = "/api/angebote/" + angebotId + "/status/";

    // When — viermal weiter bis ABGERECHNET, dann noch einmal.
    for (int schritt = 0; schritt < 4; schritt++) {
      assertThat(ruf(pfad + "weiter", HttpMethod.POST, null, AngebotResponse.class).getStatusCode())
          .isEqualTo(HttpStatus.OK);
    }
    final ResponseEntity<String> ueberDasEnde =
        ruf(pfad + "weiter", HttpMethod.POST, null, String.class);
    final ResponseEntity<AngebotResponse> zurueck =
        ruf(pfad + "zurueck", HttpMethod.POST, null, AngebotResponse.class);

    // Then
    assertThat(ueberDasEnde.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(Objects.requireNonNull(zurueck.getBody()).status())
        .isEqualTo(Angebotsstatus.ERLEDIGT);
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM angebot WHERE id = ?", String.class, Long.valueOf(angebotId)))
        .isEqualTo("ERLEDIGT");
  }

  @Test
  void statusZurueck_ofACreatedAngebot_thenAnswers409() {
    // Given
    final long angebotId = angebot().id();

    // When
    final ResponseEntity<String> antwort =
        ruf("/api/angebote/" + angebotId + "/status/zurueck", HttpMethod.POST, null, String.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void aendern_inTheLastStatus_thenStillAllowed() {
    // Given — Kriterium 5: auch ein abgerechnetes Angebot bleibt aenderbar.
    final long angebotId = angebot().id();
    jdbc.update("UPDATE angebot SET status = 'ABGERECHNET' WHERE id = ?", Long.valueOf(angebotId));

    // When
    final ResponseEntity<AngebotResponse> antwort =
        ruf(
            "/api/angebote/" + angebotId,
            HttpMethod.PUT,
            rumpf(List.of(position("Konzeption", "AUFWAND", "2.50", "PERSONENTAG"))),
            AngebotResponse.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(Objects.requireNonNull(antwort.getBody()).status())
        .isEqualTo(Angebotsstatus.ABGERECHNET);
  }

  @Test
  void angebote_thenListsTheAngeboteOfTheFirmaNewestFirst() {
    // Given — Kriterium 7: gleiches Datum, also entscheidet die hoehere Kennung.
    final long aelterer = angebot().id();
    final long juengerer = angebot().id();

    // When
    final FirmaAngeboteResponse liste =
        Objects.requireNonNull(
            ruf(
                    "/api/firmen/" + firmaId + "/angebote",
                    HttpMethod.GET,
                    null,
                    FirmaAngeboteResponse.class)
                .getBody());

    // Then
    assertThat(liste.angebote())
        .extracting(zeile -> Long.valueOf(zeile.id()))
        .containsExactly(Long.valueOf(juengerer), Long.valueOf(aelterer));
  }

  @Test
  void anlegen_withAContactOfTheFirma_thenAnswersCreatedAndNamesFirmaAndContact() {
    // When
    final ResponseEntity<AngebotResponse> antwort =
        anlegenMit(ansprechpartnerId, AngebotResponse.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    final AngebotResponse angelegt = Objects.requireNonNull(antwort.getBody());
    assertThat(angelegt.firmaId()).isEqualTo(firmaId);
    assertThat(angelegt.firmaName()).isEqualTo("Adler AG");
    assertThat(angelegt.ansprechpartnerId()).isEqualTo(ansprechpartnerId);
    assertThat(angelegt.ansprechpartnerName()).isEqualTo("Adler");
  }

  @Test
  void anlegen_withAContactOfAnotherFirma_thenAnswers422() {
    // When — als Text gelesen: Das Problemdokument traegt ein Zahlenfeld „status".
    final ResponseEntity<String> antwort = anlegenMit(fremderAnsprechpartnerId, String.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    // Die Maske zeigt die Meldung am Feld und nicht als Sammelmeldung (Issue #138).
    assertThat(antwort.getBody())
        .contains("\"fieldErrors\"")
        .contains("ansprechpartnerId")
        .contains("Dieser Ansprechpartner steht für das Angebot nicht zur Wahl.");
  }

  @Test
  void stilllegen_ofTheFirma_thenBlocksAnlegenButNotAendern() {
    // Given — an eine stillgelegte Firma geht kein neues Angebot; ein bestehendes bleibt pflegbar.
    final long angebotId = angebot().id();
    jdbc.update("UPDATE firma SET aktiv = false WHERE id = ?", Long.valueOf(firmaId));

    // When
    final ResponseEntity<String> anlegen =
        ruf("/api/firmen/" + firmaId + "/angebote", HttpMethod.POST, null, String.class);
    final ResponseEntity<AngebotResponse> aendern =
        ruf(
            "/api/angebote/" + angebotId,
            HttpMethod.PUT,
            rumpf(List.of(position("Konzeption", "AUFWAND", "2.50", "PERSONENTAG"))),
            AngebotResponse.class);

    // Then
    assertThat(anlegen.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(aendern.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void aendern_withAPositionOfAnotherAngebot_thenAnswers422AndLeavesItUntouched() {
    // Given — ein fremdes Angebot mit einer Position, und ein eigenes daneben.
    final long fremdesAngebot = angebot().id();
    ruf(
        "/api/angebote/" + fremdesAngebot,
        HttpMethod.PUT,
        rumpf(List.of(position("Konzeption", "AUFWAND", "2.50", "PERSONENTAG"))),
        AngebotResponse.class);
    final long fremdePosition = positionskennungen(fremdesAngebot).get(0).longValue();
    final long eigenesAngebot = angebot().id();

    // When — das eigene Angebot beruft sich auf die fremde Position.
    final ResponseEntity<String> antwort =
        ruf(
            "/api/angebote/" + eigenesAngebot,
            HttpMethod.PUT,
            rumpf(List.of(positionMitKennung(fremdePosition, "Geraubt", "AUFWAND", "1.00"))),
            String.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    // Die Maske zeigt die Meldung am Feld der Positionsliste (Issue #138).
    assertThat(antwort.getBody())
        .contains("\"fieldErrors\"")
        .contains("positionen")
        .contains("Die eingereichten Positionen passen nicht zu diesem Angebot.");
    // Die fremde Position haengt unveraendert an ihrem Angebot — nichts wurde geschrieben.
    assertThat(
            jdbc.queryForMap(
                "SELECT angebot_id, position, bezeichnung FROM angebot_position WHERE id = ?",
                Long.valueOf(fremdePosition)))
        .containsEntry("angebot_id", Long.valueOf(fremdesAngebot))
        .containsEntry("position", Integer.valueOf(1))
        .containsEntry("bezeichnung", "Konzeption");
    assertThat(bezeichnungenNachPlatz(eigenesAngebot)).isEmpty();
  }

  @Test
  void aendern_resubmittingThePositionsWithTheirIds_thenTheyKeepThem() {
    // Given — die dauerhafte Kennung ueber HTTP: gelesen, zurueckgeschickt, unveraendert.
    final long angebotId = angebot().id();
    ruf(
        "/api/angebote/" + angebotId,
        HttpMethod.PUT,
        rumpf(
            List.of(
                position("Konzeption", "AUFWAND", "2.50", "PERSONENTAG"),
                position("Schulungstag", "FESTPREIS", "1.00", "PAUSCHAL"))),
        AngebotResponse.class);
    final List<Long> vorher = positionskennungen(angebotId);

    // When — umgestellt, aber mit ihren Kennungen.
    final ResponseEntity<AngebotResponse> antwort =
        ruf(
            "/api/angebote/" + angebotId,
            HttpMethod.PUT,
            rumpf(
                List.of(
                    positionMitKennung(
                        vorher.get(1).longValue(), "Schulungstag", "FESTPREIS", "1.00"),
                    positionMitKennung(
                        vorher.get(0).longValue(), "Feinkonzept", "AUFWAND", "2.50"))),
            AngebotResponse.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(Objects.requireNonNull(antwort.getBody()).positionen())
        .extracting(AngebotPositionResponse::id, AngebotPositionResponse::bezeichnung)
        .containsExactly(tuple(vorher.get(1), "Schulungstag"), tuple(vorher.get(0), "Feinkonzept"));
    assertThat(bezeichnungenNachPlatz(angebotId)).containsExactly("Schulungstag", "Feinkonzept");
  }

  @Test
  void angebotswege_withoutASession_thenUnauthorized() {
    // Given — jeder neue Pfad unter /api ist ohne Sitzung verschlossen.
    sitzung = new HttpHeaders();

    // When / Then
    assertThat(
            ruf("/api/firmen/" + firmaId + "/angebote", HttpMethod.GET, null, String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(ruf("/api/angebote/1", HttpMethod.GET, null, String.class).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
