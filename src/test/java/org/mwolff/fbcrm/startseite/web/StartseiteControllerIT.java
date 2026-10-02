package org.mwolff.fbcrm.startseite.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
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
 * Der eine Weg der Startseite ueber HTTP, mit Sitzung und gegen eine echte PostgreSQL-Instanz
 * (Issue #214).
 *
 * <p>Hier stehen die Zusagen, die an der echten Verdrahtung haengen: Der Weg faellt unter das
 * bestehende {@code /api/**} und ist ohne Sitzung 401, ein Monatswert, der kein Monat ist, ist eine
 * fehlerhafte Anfrage, und angemeldet antwortet er mit genau zwoelf waehlbaren Monaten, unter denen
 * der geltende steht (Plan #208, E17).
 *
 * <p>Die Zahlen selbst stehen nicht hier, sondern in {@code StartseiteUseCaseTest}: Gegenstand
 * dieses Pakets ist der Weg, nicht die Rechnung. Darum genuegt der leere Bestand — er zeigt, dass
 * die Startseite auch ohne einen einzigen Datensatz antwortet und nicht auf eine leere Liste
 * faellt.
 */
class StartseiteControllerIT extends AbstractIntegrationTest {

  private static final String MAIL = "manne@example.org";
  private static final String PASSWORT = "richtiges-passwort";
  private static final Instant ANGELEGT = Instant.parse("2026-11-01T08:00:00Z");
  private static final String PFAD = "/api/startseite";

  private final TestRestTemplate rest;
  private final AccountRepository accounts;
  private final PasswordHasher hasher;
  private final JdbcTemplate jdbc;

  private HttpHeaders sitzung = new HttpHeaders();

  @Autowired
  StartseiteControllerIT(
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
  void legeDasKontoAn() {
    jdbc.execute(
        "TRUNCATE arbeitszeit, rechnung_position, rechnung, angebot_position, angebot,"
            + " ansprechpartner, firma RESTART IDENTITY CASCADE");
    jdbc.execute("TRUNCATE outbox_message, password_reset_token, account RESTART IDENTITY CASCADE");
    accounts.save(
        new Account(null, MAIL, "Manne", hasher.hash(PASSWORT), Role.ADMIN, 0, ANGELEGT, ANGELEGT));
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

  private <T> ResponseEntity<T> ruf(final String pfad, final Class<T> typ) {
    return rest.exchange(pfad, HttpMethod.GET, new HttpEntity<>(null, sitzung), typ);
  }

  @Test
  void stand_withoutASession_thenAnswers401() {
    // Given — der Weg faellt unter /api/**; eine eigene Regel in SecurityConfig gibt es nicht.
    sitzung = new HttpHeaders();

    // When / Then
    assertThat(ruf(PFAD, String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void stand_givenAMonthThatIsNoMonth_thenAnswers400() {
    // Given — „unsinn" ist kein Monat; das ist eine fehlerhafte Anfrage und kein Serverfehler.
    // When / Then
    assertThat(ruf(PFAD + "?monat=unsinn", String.class).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void stand_whenSignedIn_thenAnswers200WithTwelveMonthsIncludingTheOneInForce() {
    // Given — E17: der gezeigte Monat ist immer einer der zwoelf waehlbaren.
    // When
    final ResponseEntity<StartseiteResponse> gelesen = ruf(PFAD, StartseiteResponse.class);

    // Then
    assertThat(gelesen.getStatusCode()).isEqualTo(HttpStatus.OK);
    final StartseiteResponse stand = Objects.requireNonNull(gelesen.getBody());
    assertThat(stand.monat()).isEqualTo(YearMonth.now(Geschaeftszone.ZONE));
    assertThat(stand.monate()).hasSize(12).contains(stand.monat());
  }

  @Test
  void stand_givenAnEmptyStock_thenAllThreeFiguresAreEmptyAndNotAbsent() {
    // Given — ohne einen einzigen Datensatz antwortet die Startseite trotzdem vollstaendig.
    // When
    final StartseiteResponse stand =
        Objects.requireNonNull(ruf(PFAD, StartseiteResponse.class).getBody());

    // Then
    assertThat(stand.inArbeit()).isEmpty();
    assertThat(stand.nichtAbgerechnet().angebote()).isEmpty();
    assertThat(stand.nichtAbgerechnet().netto()).isEqualByComparingTo("0.00");
    assertThat(stand.nichtAbgerechnet().erfasstImMonat()).isEqualByComparingTo("0.00");
    assertThat(stand.abgerechnet().anzahl()).isZero();
  }

  @Test
  void stand_givenAMonthWithinTheTwelve_thenThatMonthIsInForce() {
    // Given — ein Monat aus der Liste wird uebernommen (Plan #208, E18).
    final YearMonth vormonat = YearMonth.now(Geschaeftszone.ZONE).minusMonths(1);

    // When
    final StartseiteResponse stand =
        Objects.requireNonNull(
            ruf(PFAD + "?monat=" + vormonat, StartseiteResponse.class).getBody());

    // Then
    assertThat(stand.monat()).isEqualTo(vormonat);
    assertThat(stand.monate()).hasSize(12);
  }

  @Test
  void stand_thenTheMonthsAreWrittenAsTextNewestFirst() {
    // Given — die Oberflaeche liest den Monat als Text „JJJJ-MM". Welche Form der ObjectMapper von
    // Spring Boot schreibt, laesst sich nur hier belegen.
    final YearMonth laufend = YearMonth.now(Geschaeftszone.ZONE);

    // When
    final ResponseEntity<String> gelesen = ruf(PFAD, String.class);

    // Then
    assertThat(gelesen.getBody()).contains("\"monat\":\"" + laufend + "\"");
    final List<YearMonth> monate =
        Objects.requireNonNull(ruf(PFAD, StartseiteResponse.class).getBody()).monate();
    assertThat(monate).first().isEqualTo(laufend);
    assertThat(monate).last().isEqualTo(laufend.minusMonths(11));
  }
}
