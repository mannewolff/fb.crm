package org.mwolff.fbcrm.startseite.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.auth.Konten;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.SecretHasher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Der Weg des Forecasts ueber HTTP, mit Sitzung und gegen eine echte PostgreSQL-Instanz (Issue
 * #308; Plan #303, E1, E9).
 *
 * <p>Hier steht das Beispiel des Fachplans (#302): das Angebot an IT Bildungshaus vom 15.10.2026
 * auf „angelegt" mit drei Positionen auf 2027 — 135 h, 135 h und 27 h zu je 60,00, zusammen
 * 17.820,00 netto unter 2027 und nichts unter 2026. Dazu die Textform, die nur der ObjectMapper von
 * Spring Boot festlegt: das Jahr als Text {@code "2027"} (E9), das Datum als {@code JJJJ-MM-TT}.
 *
 * <p>Die Rechnung im Einzelnen — Reste, Status, interne Arbeit — steht in {@code
 * ForecastUseCaseTest}; Gegenstand dieses Pakets ist der Weg.
 */
class ForecastControllerIT extends AbstractIntegrationTest {

  private static final String MAIL = "manne@example.org";
  private static final String PASSWORT = "richtiges-passwort";
  private static final Instant ANGELEGT = Instant.parse("2026-10-15T08:00:00Z");
  private static final String PFAD = "/api/startseite/forecast";

  private final TestRestTemplate rest;
  private final AccountRepository accounts;
  private final SecretHasher hasher;
  private final JdbcTemplate jdbc;

  private HttpHeaders sitzung = new HttpHeaders();

  @Autowired
  ForecastControllerIT(
      final TestRestTemplate rest,
      final AccountRepository accounts,
      final SecretHasher hasher,
      final JdbcTemplate jdbc) {
    this.rest = rest;
    this.accounts = accounts;
    this.hasher = hasher;
    this.jdbc = jdbc;
  }

  @BeforeEach
  void legeDasKontoAn() {
    jdbc.execute(
        "TRUNCATE arbeitszeit, rechnung_position, rechnung, angebot_position, angebot,"
            + " ansprechpartner, firma RESTART IDENTITY CASCADE");
    jdbc.execute("TRUNCATE outbox_message, account RESTART IDENTITY CASCADE");
    accounts.save(Konten.neu(MAIL, hasher.hash(PASSWORT), ANGELEGT));
    sitzung = angemeldeterKopf();
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

  private <T> ResponseEntity<T> ruf(final Class<T> typ) {
    return rest.exchange(PFAD, HttpMethod.GET, new HttpEntity<>(null, sitzung), typ);
  }

  @Test
  void forecast_withoutASession_thenAnswers401() {
    // Given — der Weg faellt unter /api/**; eine eigene Regel in SecurityConfig gibt es nicht.
    sitzung = new HttpHeaders();

    // When / Then
    assertThat(ruf(String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void forecast_givenTheExampleOfTheFachplan_thenAll17820StandUnder2027AndNothingUnder2026() {
    // Given — #302, Anlass und Kriterium 4: angeboten im Oktober 2026, geleistet 2027.
    beispielDesFachplans();

    // When
    final ResponseEntity<ForecastResponse> gelesen = ruf(ForecastResponse.class);

    // Then
    assertThat(gelesen.getStatusCode()).isEqualTo(HttpStatus.OK);
    final ForecastResponse forecast = Objects.requireNonNull(gelesen.getBody());
    assertThat(forecast.jahre())
        .extracting(ForecastResponse.Jahreszeile::jahr)
        .containsExactly("2027");
    final ForecastResponse.Jahreszeile jahr2027 = forecast.jahre().get(0);
    assertThat(jahr2027.angebotenNetto()).isEqualByComparingTo("17820.00");
    assertThat(jahr2027.zugesagtNetto()).isEqualByComparingTo("0.00");
    assertThat(jahr2027.summeNetto()).isEqualByComparingTo("17820.00");
    assertThat(jahr2027.positionen())
        .extracting(
            ForecastResponse.Positionszeile::firmaName,
            ForecastResponse.Positionszeile::angebotDatum,
            ForecastResponse.Positionszeile::bezeichnung)
        .containsExactly(
            tuple("IT Bildungshaus", LocalDate.of(2026, 10, 15), "Aufwände Mai 2027"),
            tuple("IT Bildungshaus", LocalDate.of(2026, 10, 15), "Aufwände Juni 2027"),
            tuple("IT Bildungshaus", LocalDate.of(2026, 10, 15), "Aufwände 3 Tage Rest"));
  }

  @Test
  void forecast_thenTheYearIsWrittenAsTextAndTheDateAsJjjjMmTt() {
    // Given — E9: Jackson schriebe ein Year als Zahl; die Antwort traegt Text.
    beispielDesFachplans();

    // When
    final ResponseEntity<String> gelesen = ruf(String.class);

    // Then
    assertThat(gelesen.getBody())
        .contains("\"jahr\":\"2027\"")
        .contains("\"angebotDatum\":\"2026-10-15\"");
  }

  @Test
  void forecast_givenAnEmptyStock_thenAnswersWithAnEmptyListOfYears() {
    // Given — Kriterium 6: ohne eine offene Position gibt es kein Jahr, und die Liste ist leer.
    // When
    final ResponseEntity<String> gelesen = ruf(String.class);

    // Then
    assertThat(gelesen.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(gelesen.getBody()).isEqualTo("{\"jahre\":[]}");
  }

  /**
   * Legt das Beispiel des Fachplans an: ein Kundenangebot an IT Bildungshaus vom 15.10.2026 auf
   * „angelegt" mit drei Positionen auf Leistungsjahr 2027 zu je 60,00 netto.
   *
   * <p>Direkt in der Datenbank, wie in {@link StartseiteControllerIT}: Hier geht es nur darum,
   * <b>dass</b> diese Positionen im Bestand liegen.
   */
  private void beispielDesFachplans() {
    final Long firmaId =
        jdbc.queryForObject(
            "INSERT INTO firma (name) VALUES ('IT Bildungshaus') RETURNING id", Long.class);
    final Long angebotId =
        jdbc.queryForObject(
            """
            INSERT INTO angebot (firma_id, status, angebot_datum)
            VALUES (?, 'ANGELEGT', DATE '2026-10-15') RETURNING id
            """,
            Long.class,
            firmaId);
    position(angebotId, 1, "Aufwände Mai 2027", "135");
    position(angebotId, 2, "Aufwände Juni 2027", "135");
    position(angebotId, 3, "Aufwände 3 Tage Rest", "27");
  }

  private void position(
      final Long angebotId, final int nummer, final String bezeichnung, final String stunden) {
    jdbc.update(
        """
        INSERT INTO angebot_position
            (angebot_id, position, bezeichnung, abrechnungsmodus, menge, einheit, einzelpreis,
             leistungsjahr)
        VALUES (?, ?, ?, 'AUFWAND', CAST(? AS numeric), 'STUNDE', 60.00, 2027)
        """,
        angebotId,
        nummer,
        bezeichnung,
        stunden);
  }
}
