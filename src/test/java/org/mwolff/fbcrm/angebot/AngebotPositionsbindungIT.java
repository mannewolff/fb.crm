package org.mwolff.fbcrm.angebot;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.angebot.application.KennzeichenNichtAenderbar;
import org.mwolff.fbcrm.angebot.web.AngebotPositionResponse;
import org.mwolff.fbcrm.angebot.web.AngebotResponse;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.PasswordHasher;
import org.mwolff.fbcrm.auth.domain.Role;
import org.mwolff.fbcrm.rechnung.web.RechnungResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Die Bindung berechneter Positionen ueber HTTP, mit Sitzung und gegen eine echte
 * PostgreSQL-Instanz (#160, Kriterium 28).
 *
 * <p>Nur hier pruefbar, weil beide Module zusammenspielen muessen: Das Angebot fragt ueber den Port
 * {@code Positionsverwendung}, welche seiner Positionen in einer Rechnung stehen, und die Antwort
 * kommt aus dem Bestand des Moduls {@code rechnung}. Ein Test mit einem Doppel belegte nur die
 * Regel, nicht die Verdrahtung.
 *
 * <p>Der Ausgangspunkt ist ein bestelltes Angebot mit zwei Positionen, dessen <b>erste</b> in einem
 * Rechnungsentwurf steht — die zweite nimmt der Entwurf nicht auf. Damit stehen beide Seiten der
 * Regel nebeneinander: Die berechnete Position ist gebunden, die andere bleibt frei.
 *
 * <p><b>Dieselbe Verdrahtung traegt die Sperre der Art</b> (Issue #227, Kriterium 8 von #207): Aus
 * dem Angebot ist eine Rechnung entstanden, also laesst sich sein Kennzeichen nicht mehr wechseln.
 * Geantwortet wird 422 mit der Meldung am Feld {@code intern} (E20), und das Angebot bleibt der von
 * vorher. Auch das ist nur hier pruefbar: Die Auskunft kommt ueber {@link
 * org.mwolff.fbcrm.angebot.application.Rechnungsbindung} aus dem Bestand des Moduls {@code
 * rechnung}.
 */
class AngebotPositionsbindungIT extends AbstractIntegrationTest {

  private static final String MAIL = "manne@example.org";
  private static final String PASSWORT = "richtiges-passwort";
  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");

  private static final String KONZEPTION = "Konzeption";
  private static final String SCHULUNG = "Schulungstag";

  private final TestRestTemplate rest;
  private final AccountRepository accounts;
  private final PasswordHasher hasher;
  private final JdbcTemplate jdbc;

  private HttpHeaders sitzung = new HttpHeaders();
  private long angebotId;
  private long konzeptionId;
  private long schulungId;

  @Autowired
  AngebotPositionsbindungIT(
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
  void legeDenAusgangspunktAn() {
    jdbc.execute(
        "TRUNCATE rechnung_position, rechnung, angebot_position, angebot, ansprechpartner, firma"
            + " RESTART IDENTITY CASCADE");
    jdbc.execute("DELETE FROM rechnung_einstellungen");
    jdbc.execute("INSERT INTO rechnung_einstellungen DEFAULT VALUES");
    jdbc.execute("TRUNCATE outbox_message, password_reset_token, account RESTART IDENTITY CASCADE");
    accounts.save(
        new Account(null, MAIL, "Manne", hasher.hash(PASSWORT), Role.ADMIN, 0, ANGELEGT, ANGELEGT));
    sitzung = angemeldeterKopf();
    jdbc.update("INSERT INTO firma (name) VALUES (?)", "Adler AG");
    final long firmaId =
        Objects.requireNonNull(
                jdbc.queryForObject("SELECT id FROM firma WHERE name = ?", Long.class, "Adler AG"))
            .longValue();

    angebotId =
        Objects.requireNonNull(
                ruf(
                        "/api/firmen/" + firmaId + "/angebote",
                        HttpMethod.POST,
                        null,
                        AngebotResponse.class)
                    .getBody())
            .id();
    final AngebotResponse mitPositionen =
        Objects.requireNonNull(
            aendere(
                    List.of(
                        position(null, KONZEPTION, "AUFWAND", "2.50", "PERSONENTAG", "1000.00"),
                        position(null, SCHULUNG, "FESTPREIS", "1", "PAUSCHAL", "1200.00")),
                    AngebotResponse.class)
                .getBody());
    konzeptionId = kennung(mitPositionen.positionen().get(0));
    schulungId = kennung(mitPositionen.positionen().get(1));

    ruf("/api/angebote/" + angebotId + "/status/weiter", HttpMethod.POST, null, String.class);
    ruf("/api/angebote/" + angebotId + "/status/weiter", HttpMethod.POST, null, String.class);
    entwurfUeberDieKonzeption();
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

  private static long kennung(final AngebotPositionResponse position) {
    return Objects.requireNonNull(position.id()).longValue();
  }

  private static Map<String, Object> position(
      final @Nullable Long id,
      final String bezeichnung,
      final String abrechnungsmodus,
      final String menge,
      final String einheit,
      final String einzelpreis) {
    final Map<String, Object> felder = new LinkedHashMap<>();
    felder.put("id", id);
    felder.put("bezeichnung", bezeichnung);
    felder.put("abrechnungsmodus", abrechnungsmodus);
    felder.put("menge", menge);
    felder.put("einheit", einheit);
    felder.put("einzelpreis", einzelpreis);
    return felder;
  }

  private <T> ResponseEntity<T> aendere(
      final List<Map<String, Object>> positionen, final Class<T> typ) {
    return aendere(positionen, null, typ);
  }

  private <T> ResponseEntity<T> aendere(
      final List<Map<String, Object>> positionen,
      final @Nullable Boolean intern,
      final Class<T> typ) {
    final Map<String, Object> rumpf = new LinkedHashMap<>();
    rumpf.put("angebotDatum", "2026-09-25");
    rumpf.put("ansprechpartnerId", null);
    rumpf.put("beschreibung", "Neugestaltung der Website");
    rumpf.put("intern", intern);
    rumpf.put("positionen", positionen);
    return ruf("/api/angebote/" + angebotId, HttpMethod.PUT, rumpf, typ);
  }

  /*
   * Der Entwurf entsteht ueber alle offenen Positionen; ein PUT mit nur der Konzeption laesst die
   * Schulung wieder heraus (eine Angabe ueber die Menge 0 waere dasselbe). Danach steht genau eine
   * der beiden Positionen in einer Rechnung.
   */
  private void entwurfUeberDieKonzeption() {
    final RechnungResponse entwurf =
        Objects.requireNonNull(
            ruf(
                    "/api/angebote/" + angebotId + "/rechnungen",
                    HttpMethod.POST,
                    null,
                    RechnungResponse.class)
                .getBody());
    final Map<String, Object> angabe = new LinkedHashMap<>();
    angabe.put("angebotPositionId", Long.valueOf(konzeptionId));
    angabe.put("bezeichnung", KONZEPTION);
    angabe.put("menge", "2.50");
    final Map<String, Object> rumpf = new LinkedHashMap<>();
    rumpf.put("rechnungDatum", "2026-09-30");
    rumpf.put("leistungszeitraum", "September 2026");
    rumpf.put("positionen", List.of(angabe));
    ruf("/api/rechnungen/" + entwurf.id(), HttpMethod.PUT, rumpf, RechnungResponse.class);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM rechnung_position WHERE angebot_position_id = ?",
                Long.class,
                Long.valueOf(konzeptionId)))
        .isOne();
  }

  private AngebotResponse angebot() {
    return Objects.requireNonNull(
        ruf("/api/angebote/" + angebotId, HttpMethod.GET, null, AngebotResponse.class).getBody());
  }

  /*
   * Ein abgewiesenes Aendern: 422, die Meldung nennt die Position, und der Bestand ist danach der
   * von vorher — nichts wird geschrieben.
   */
  private void weistAbUndLaesstDasAngebot(final List<Map<String, Object>> positionen) {
    final AngebotResponse vorher = angebot();

    final ResponseEntity<String> antwort = aendere(positionen, String.class);

    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(antwort.getBody()).contains(KONZEPTION);
    assertThat(angebot()).isEqualTo(vorher);
  }

  @Test
  void aendern_withoutThePositionThatIsInARechnung_thenUnprocessableAndUnchanged() {
    // Given / When / Then — die berechnete Position darf nicht verschwinden (Kriterium 28).
    weistAbUndLaesstDasAngebot(
        List.of(
            position(Long.valueOf(schulungId), SCHULUNG, "FESTPREIS", "1", "PAUSCHAL", "1200.00")));
  }

  @Test
  void aendern_withAChangedEinheitOfThePositionInARechnung_thenUnprocessableAndUnchanged() {
    // Given / When / Then — die Einheit steht so auf der Rechnung.
    weistAbUndLaesstDasAngebot(
        List.of(
            position(
                Long.valueOf(konzeptionId), KONZEPTION, "AUFWAND", "2.50", "STUNDE", "1000.00"),
            position(Long.valueOf(schulungId), SCHULUNG, "FESTPREIS", "1", "PAUSCHAL", "1200.00")));
  }

  @Test
  void
      aendern_withAChangedAbrechnungsmodusOfThePositionInARechnung_thenUnprocessableAndUnchanged() {
    // Given / When / Then — dasselbe fuer die Abrechnungsart.
    weistAbUndLaesstDasAngebot(
        List.of(
            position(
                Long.valueOf(konzeptionId),
                KONZEPTION,
                "FESTPREIS",
                "2.50",
                "PERSONENTAG",
                "1000.00"),
            position(Long.valueOf(schulungId), SCHULUNG, "FESTPREIS", "1", "PAUSCHAL", "1200.00")));
  }

  @Test
  void aendern_withChangedTextMengePreisAndOrder_thenOk() {
    // Given — Text, Menge, Preis und Reihenfolge bleiben frei; die Rechnung haelt ihre Werte fest.
    final List<Map<String, Object>> umgestellt =
        List.of(
            position(Long.valueOf(schulungId), SCHULUNG, "FESTPREIS", "2", "PAUSCHAL", "1300.00"),
            position(
                Long.valueOf(konzeptionId),
                "Konzeption und Abstimmung",
                "AUFWAND",
                "4.00",
                "PERSONENTAG",
                "1100.00"));

    // When
    final ResponseEntity<AngebotResponse> antwort = aendere(umgestellt, AngebotResponse.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(Objects.requireNonNull(antwort.getBody()).positionen())
        .extracting(AngebotPositionResponse::bezeichnung)
        .containsExactly(SCHULUNG, "Konzeption und Abstimmung");
  }

  @Test
  void aendern_droppingThePositionThatIsInNoRechnung_thenOk() {
    // Given / When — die Schulung steht in keiner Rechnung und darf weg.
    final ResponseEntity<AngebotResponse> antwort =
        aendere(
            List.of(
                position(
                    Long.valueOf(konzeptionId),
                    KONZEPTION,
                    "AUFWAND",
                    "2.50",
                    "PERSONENTAG",
                    "1000.00")),
            AngebotResponse.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(Objects.requireNonNull(antwort.getBody()).positionen())
        .extracting(AngebotPositionResponse::bezeichnung)
        .containsExactly(KONZEPTION);
  }

  @Test
  void aendern_switchingToInternalWhileARechnungExists_thenUnprocessableAndUnchanged() {
    // Given — Kriterium 8: Die interne Arbeit wird nie abgerechnet; ein internes Angebot mit einem
    // Entwurf darauf waere ein widerspruechlicher Satz.
    final AngebotResponse vorher = angebot();

    // When
    final ResponseEntity<String> antwort =
        aendere(
            List.of(
                position(
                    Long.valueOf(konzeptionId),
                    KONZEPTION,
                    "AUFWAND",
                    "2.50",
                    "PERSONENTAG",
                    "1000.00"),
                position(
                    Long.valueOf(schulungId), SCHULUNG, "FESTPREIS", "1", "PAUSCHAL", "1200.00")),
            Boolean.TRUE,
            String.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(antwort.getBody())
        .contains(KennzeichenNichtAenderbar.MELDUNG)
        .contains(KennzeichenNichtAenderbar.FELD);
    assertThat(angebot()).isEqualTo(vorher);
  }
}
