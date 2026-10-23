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
import org.mwolff.fbcrm.auth.Anmeldung;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Das Wettrennen zweier gleichzeitiger Einrichtungsaufrufe (E5; Plan #414, A5 und E12).
 *
 * <p>Die beiden Aufrufe tragen <b>verschiedene</b> Adressen. Beide kommen durch die
 * Existenzpruefung im Anwendungsfall und beide durch den eindeutigen Index auf {@code
 * lower(email)}. Bis V30 trennte sie der Index {@code account_single_admin}; er trug die Zusage
 * „genau ein Konto", die mit mehreren Konten entfaellt. Seitdem enden zwei gleichzeitige Aufrufe
 * mit richtigem Einmal-Schluessel in einem oder zwei C-Level-Konten desselben Betreibers — fachlich
 * erlaubt (A5).
 *
 * <p>Was der Test festhaelt, ist die neue Zusage: Nach dem Wettrennen ist die Instanz eingerichtet,
 * jedes entstandene Konto ist ein aktives C-Level-Konto, und ein dritter Aufruf wird abgewiesen —
 * der Schluessel wirkt danach nicht mehr.
 *
 * <p>Seit der Einrichtung per Code fordert jeder der beiden Faeden zuerst seinen eigenen Code an.
 * Ohne ihn scheiterte jede Bestaetigung schon am Code, und der Test pruefte das Wettrennen nicht
 * mehr. Die Anforderungen laufen vor der Startsperre; nebenlaeufig ist allein die Bestaetigung.
 */
@TestPropertySource(
    properties = {
      "fbcrm.setup.bootstrap-token=" + SetupControllerIT.SCHLUESSEL,
      "fbcrm.mail.enabled=true",
      "fbcrm.auth.login-max-attempts=1000"
    })
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
    SetupControllerIT.leere(jdbc);
  }

  /**
   * Beide Faeden fordern ihren Code an, warten danach auf dieselbe Startsperre und bestaetigen dann
   * wirklich nebenlaeufig.
   */
  private List<HttpStatus> gleichzeitigEinrichten() throws Exception {
    final CountDownLatch bereit = new CountDownLatch(2);
    final CountDownLatch start = new CountDownLatch(1);
    try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
      final Future<HttpStatus> erster =
          pool.submit(() -> richteEin(bereit, start, "erster@example.org"));
      final Future<HttpStatus> zweiter =
          pool.submit(() -> richteEin(bereit, start, "zweiter@example.org"));
      assertThat(bereit.await(GEDULD.toSeconds(), TimeUnit.SECONDS)).isTrue();
      start.countDown();
      return List.of(
          erster.get(GEDULD.toSeconds(), TimeUnit.SECONDS),
          zweiter.get(GEDULD.toSeconds(), TimeUnit.SECONDS));
    }
  }

  private HttpStatus richteEin(
      final CountDownLatch bereit, final CountDownLatch start, final String email)
      throws Exception {
    SetupControllerIT.fordereCodeAn(rest, email, SetupControllerIT.SCHLUESSEL);
    final String code = Anmeldung.codeAusDemPostausgang(jdbc, email);
    bereit.countDown();
    start.await();
    final ResponseEntity<String> antwort =
        SetupControllerIT.richteEin(
            rest, SetupControllerIT.rumpf(email, code, SetupControllerIT.SCHLUESSEL));
    return HttpStatus.valueOf(antwort.getStatusCode().value());
  }

  private long konten() {
    return Objects.requireNonNull(jdbc.queryForObject("SELECT count(*) FROM account", Long.class));
  }

  private long aktiveCLevel() {
    return Objects.requireNonNull(
        jdbc.queryForObject(
            "SELECT count(*) FROM account WHERE role = 'C_LEVEL' AND aktiv", Long.class));
  }

  @Test
  void initialize_givenTwoConcurrentCallsWithDifferentAddresses_thenTheInstanceIsInitialized()
      throws Exception {
    // When
    gleichzeitigEinrichten();

    // Then
    final ResponseEntity<String> stand = rest.getForEntity("/api/setup/status", String.class);
    assertThat(stand.getBody()).contains("\"initialized\":true");
  }

  @Test
  void initialize_givenTwoConcurrentCallsWithDifferentAddresses_thenAtLeastOneSucceeds()
      throws Exception {
    // When
    final List<HttpStatus> ausgang = gleichzeitigEinrichten();

    // Then — wer verliert, bekommt die uebliche Abweisung; mehr als OK oder 403 gibt es nicht.
    assertThat(ausgang)
        .contains(HttpStatus.OK)
        .allMatch(status -> status == HttpStatus.OK || status == HttpStatus.FORBIDDEN);
  }

  @Test
  void initialize_givenTwoConcurrentCallsWithDifferentAddresses_thenEveryAccountIsAnActiveCLevel()
      throws Exception {
    // When
    final List<HttpStatus> ausgang = gleichzeitigEinrichten();

    // Then — so viele Konten wie gelungene Aufrufe, und jedes ist C-Level und aktiv.
    assertThat(konten())
        .isEqualTo(ausgang.stream().filter(HttpStatus.OK::equals).count())
        .isEqualTo(aktiveCLevel());
  }

  @Test
  void requestCode_afterTheRace_thenAThirdCallIsRefused() throws Exception {
    // Given
    gleichzeitigEinrichten();

    // When
    final ResponseEntity<String> dritter =
        SetupControllerIT.fordereCodeAn(rest, "dritter@example.org", SetupControllerIT.SCHLUESSEL);

    // Then — der Einmal-Schluessel wirkt danach nicht mehr.
    assertThat(dritter.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }
}
