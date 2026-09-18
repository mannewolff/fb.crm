package org.mwolff.fbcrm.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.auth.AuthProperties;

/**
 * Die Zaehlbremse (E9): Schwelle, Zeitfenster und die Trennung nach IP und Adresse.
 *
 * <p>Die Uhr ist beweglich, aber nicht echt — eine {@code Thread.sleep}-Probe fuer das Zeitfenster
 * waere langsam und wackelig.
 */
class LoginAttemptLimiterTest {

  private static final Instant JETZT = Instant.parse("2026-09-18T10:00:00Z");
  private static final Duration FENSTER = Duration.ofMinutes(15);
  private static final int SCHWELLE = 3;
  private static final String IP = "203.0.113.7";
  private static final String ANDERE_IP = "203.0.113.8";
  private static final String DRITTE_IP = "203.0.113.9";
  private static final String MAIL = "manne@example.org";
  private static final String ANDERE_MAIL = "fremd@example.org";

  private final BeweglicheUhr uhr = new BeweglicheUhr(JETZT);
  private final LoginAttemptLimiter limiter = new LoginAttemptLimiter(schalter(), uhr);

  private static AuthProperties schalter() {
    return new AuthProperties(
        "geheimnis-mit-mindestens-32-zeichen-laenge",
        Duration.ofDays(1),
        "fbcrm_session",
        true,
        SCHWELLE,
        FENSTER,
        List.of());
  }

  private void fehlversuche(final int anzahl, final String ip, final String mail) {
    IntStream.range(0, anzahl).forEach(unbenutzt -> limiter.recordFailure(ip, mail));
  }

  @Test
  void isAllowed_givenNoAttemptAtAll_thenAllowed() {
    // When / Then
    assertThat(limiter.isAllowed(IP, MAIL)).isTrue();
  }

  @Test
  void isAllowed_givenOneFailureBelowTheThreshold_thenStillAllowed() {
    // Given
    fehlversuche(SCHWELLE - 1, IP, MAIL);

    // When / Then
    assertThat(limiter.isAllowed(IP, MAIL)).isTrue();
  }

  @Test
  void isAllowed_givenAsManyFailuresAsTheThreshold_thenBlocked() {
    // Given
    fehlversuche(SCHWELLE, IP, MAIL);

    // When / Then
    assertThat(limiter.isAllowed(IP, MAIL)).isFalse();
  }

  @Test
  void isAllowed_givenTheThresholdReachedFromAnotherAddress_thenBlockedByTheMailCounter() {
    // Given — dieselbe Adresse aus vielen Netzen angegangen.
    fehlversuche(SCHWELLE, ANDERE_IP, MAIL);

    // When / Then
    assertThat(limiter.isAllowed(IP, MAIL)).isFalse();
  }

  @Test
  void isAllowed_givenTheThresholdReachedForAnotherMail_thenBlockedByTheIpCounter() {
    // Given — eine IP, die sich durch Adressen probiert.
    fehlversuche(SCHWELLE, IP, ANDERE_MAIL);

    // When / Then
    assertThat(limiter.isAllowed(IP, MAIL)).isFalse();
  }

  @Test
  void isAllowed_givenAnotherIpAndAnotherMail_thenUntouched() {
    // Given
    fehlversuche(SCHWELLE, IP, MAIL);

    // When / Then
    assertThat(limiter.isAllowed(ANDERE_IP, ANDERE_MAIL)).isTrue();
  }

  @Test
  void isAllowed_givenTheMailInAnotherCase_thenCountsOnTheSameCounter() {
    // Given
    fehlversuche(SCHWELLE, ANDERE_IP, "Manne@Example.ORG");

    // When / Then
    assertThat(limiter.isAllowed(IP, MAIL)).isFalse();
  }

  @Test
  void isAllowed_givenAllFailuresOlderThanTheWindow_thenAllowedAgain() {
    // Given
    fehlversuche(SCHWELLE, IP, MAIL);

    // When
    uhr.springe(FENSTER.plusSeconds(1));

    // Then
    assertThat(limiter.isAllowed(IP, MAIL)).isTrue();
  }

  @Test
  void isAllowed_givenFailuresExactlyAtTheEdgeOfTheWindow_thenStillBlocked() {
    // Given
    fehlversuche(SCHWELLE, IP, MAIL);

    // When — eine Sekunde vor dem Rand zaehlen sie noch.
    uhr.springe(FENSTER.minusSeconds(1));

    // Then
    assertThat(limiter.isAllowed(IP, MAIL)).isFalse();
  }

  @Test
  void isAllowed_givenOlderFailuresAndOneFreshOne_thenCountsOnlyTheFreshOne() {
    // Given
    fehlversuche(SCHWELLE - 1, IP, MAIL);
    uhr.springe(FENSTER.plusSeconds(1));

    // When
    limiter.recordFailure(IP, MAIL);

    // Then
    assertThat(limiter.isAllowed(IP, MAIL)).isTrue();
  }

  @Test
  void isAllowed_givenFailuresThatFellOutOfTheWindow_thenDropsTheirCountersEntirely() {
    // Given — sonst legte jede durchprobierte Adresse einen Schluessel an, der nie verschwindet.
    fehlversuche(SCHWELLE, IP, MAIL);
    uhr.springe(FENSTER.plusSeconds(1));

    // When
    limiter.isAllowed(IP, MAIL);

    // Then
    assertThat(limiter.trackedCounters()).isZero();
  }

  @Test
  void isAllowed_givenFreshFailures_thenKeepsOneCounterPerAddressAndMail() {
    // Given
    fehlversuche(1, IP, MAIL);

    // When
    limiter.isAllowed(IP, MAIL);

    // Then
    assertThat(limiter.trackedCounters()).isEqualTo(2);
  }

  @Test
  void isAllowed_afterASuccessfulLogin_thenTheCountersAreCleared() {
    // Given
    fehlversuche(SCHWELLE, IP, MAIL);

    // When
    limiter.recordSuccess(IP, MAIL);

    // Then
    assertThat(limiter.isAllowed(IP, MAIL)).isTrue();
  }

  @Test
  void isAllowed_afterASuccessfulLoginOnAnotherAddress_thenTheMailCounterIsStillCleared() {
    // Given
    fehlversuche(SCHWELLE, ANDERE_IP, MAIL);

    // When — dieselbe Adresse, andere IP: geloescht wird der Zaehler der Adresse.
    limiter.recordSuccess(IP, "Manne@Example.ORG");

    // Then — von einer dritten IP aus ist die Adresse wieder frei; ANDERE_IP bleibt gebremst.
    assertThat(limiter.isAllowed(DRITTE_IP, MAIL)).isTrue();
  }

  /** Eine Uhr, die auf Zuruf springt — ohne zu warten. */
  private static final class BeweglicheUhr extends Clock {

    private Instant jetzt;

    BeweglicheUhr(final Instant start) {
      this.jetzt = start;
    }

    void springe(final Duration weiter) {
      jetzt = jetzt.plus(weiter);
    }

    @Override
    public Instant instant() {
      return jetzt;
    }

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(final ZoneId zone) {
      throw new UnsupportedOperationException("Die Testuhr kennt nur UTC.");
    }
  }
}
