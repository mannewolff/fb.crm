package org.mwolff.fbcrm.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Das Wettrennen zweier gleichzeitiger Einrichtungsaufrufe (E5).
 *
 * <p>Die beiden Aufrufe tragen <b>verschiedene</b> Adressen. Genau das macht den Fall scharf: Beide
 * kommen durch die Existenzpruefung im Anwendungsfall, und beide kommen durch den eindeutigen Index
 * auf {@code lower(email)} — der sieht zwei verschiedene Schluessel. Was sie trennt, ist allein der
 * partielle eindeutige Index {@code account_single_admin} aus {@code V1__baseline.sql}, der fuer
 * jede ADMIN-Zeile denselben konstanten Schluessel traegt.
 *
 * <p>Ein Test, der beide Aufrufe mit derselben Adresse fuehrte, waere gruen, ohne diesen Index zu
 * beruehren — und uebersaehe damit genau die Luecke, die er schliessen soll.
 */
@TestPropertySource(properties = "fbcrm.setup.bootstrap-token=" + SetupControllerIT.SCHLUESSEL)
class SetupRaceIT extends AbstractIntegrationTest {

  private static final Duration GEDULD = Duration.ofSeconds(30);

  private final TestRestTemplate rest;
  private final JdbcTemplate jdbc;

  @Autowired
  SetupRaceIT(final TestRestTemplate rest, final JdbcTemplate jdbc) {
    this.rest = rest;
    this.jdbc = jdbc;
  }

  @BeforeEach
  void leereDieDatenbank() {
    jdbc.execute("TRUNCATE outbox_message, password_reset_token, account RESTART IDENTITY CASCADE");
  }

  /** Beide Aufrufe warten auf dieselbe Startsperre und laufen danach wirklich nebenlaeufig. */
  private List<HttpStatus> gleichzeitigEinrichten() throws Exception {
    final CountDownLatch start = new CountDownLatch(1);
    try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
      final Future<HttpStatus> erster = pool.submit(() -> richteEin(start, "erster@example.org"));
      final Future<HttpStatus> zweiter = pool.submit(() -> richteEin(start, "zweiter@example.org"));
      start.countDown();
      return List.of(
          erster.get(GEDULD.toSeconds(), TimeUnit.SECONDS),
          zweiter.get(GEDULD.toSeconds(), TimeUnit.SECONDS));
    }
  }

  private HttpStatus richteEin(final CountDownLatch start, final String email) throws Exception {
    start.await();
    final ResponseEntity<String> antwort =
        SetupControllerIT.richteEin(
            rest,
            SetupControllerIT.rumpf(
                email, email, SetupControllerIT.PASSWORT, SetupControllerIT.SCHLUESSEL));
    return HttpStatus.valueOf(antwort.getStatusCode().value());
  }

  private long konten() {
    return Objects.requireNonNull(jdbc.queryForObject("SELECT count(*) FROM account", Long.class));
  }

  @Test
  void initialize_givenTwoConcurrentCallsWithDifferentAddresses_thenCreatesExactlyOneAccount()
      throws Exception {
    // When
    gleichzeitigEinrichten();

    // Then — der partielle Index laesst genau eine ADMIN-Zeile zu.
    assertThat(konten()).isEqualTo(1);
  }

  @Test
  void initialize_givenTwoConcurrentCallsWithDifferentAddresses_thenExactlyOneSucceeds()
      throws Exception {
    // When
    final List<HttpStatus> ausgang = gleichzeitigEinrichten();

    // Then
    assertThat(ausgang).filteredOn(HttpStatus.OK::equals).hasSize(1);
  }

  @Test
  void initialize_givenTwoConcurrentCallsWithDifferentAddresses_thenTheLoserGetsTheUsualRefusal()
      throws Exception {
    // When
    final List<HttpStatus> ausgang = gleichzeitigEinrichten();

    // Then — dieselbe Abweisung wie „Konto existiert bereits"; die Datenbankmeldung bleibt drinnen.
    assertThat(ausgang).filteredOn(HttpStatus.FORBIDDEN::equals).hasSize(1);
  }
}
