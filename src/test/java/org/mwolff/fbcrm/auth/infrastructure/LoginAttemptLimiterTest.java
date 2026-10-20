package org.mwolff.fbcrm.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mwolff.fbcrm.auth.infrastructure.LoginAttemptLimiter.Zaehlart.ADRESSE_STUNDE;
import static org.mwolff.fbcrm.auth.infrastructure.LoginAttemptLimiter.Zaehlart.ADRESSE_TAG;
import static org.mwolff.fbcrm.auth.infrastructure.LoginAttemptLimiter.Zaehlart.HERKUNFT;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.auth.AuthProperties;
import org.mwolff.fbcrm.auth.infrastructure.LoginAttemptLimiter.Zaehlart;

/**
 * Die Zaehlbremse (E3, E27): drei Zaehlarten mit eigener Grenze und eigenem Fenster, die
 * Restwartezeit und was eine gelungene Anmeldung loescht.
 *
 * <p>Die Schalter sind die Vorgaben der {@code application.yml} — 10 je Herkunft in 15 Minuten, 5
 * je Adresse in der Stunde, 10 je Adresse in 24 Stunden —, damit die Proben die Zusagen aus
 * Kriterium 5 und E27 in ihren echten Zahlen nachweisen.
 *
 * <p>Die Uhr ist beweglich, aber nicht echt — eine {@code Thread.sleep}-Probe fuer das Zeitfenster
 * waere langsam und wackelig.
 */
class LoginAttemptLimiterTest {

  private static final Instant JETZT = Instant.parse("2026-09-18T10:00:00Z");
  private static final int JE_HERKUNFT = 10;
  private static final Duration HERKUNFT_FENSTER = Duration.ofMinutes(15);
  private static final int JE_STUNDE = 5;
  private static final Duration STUNDEN_FENSTER = Duration.ofHours(1);
  private static final int JE_TAG = 10;
  private static final Duration TAGES_FENSTER = Duration.ofHours(24);
  private static final String IP = "203.0.113.7";
  private static final String ANDERE_IP = "203.0.113.8";
  private static final String MAIL = "manne@example.org";
  private static final String ANDERE_MAIL = "fremd@example.org";

  private final BeweglicheUhr uhr = new BeweglicheUhr(JETZT);
  private final LoginAttemptLimiter limiter = new LoginAttemptLimiter(schalter(), uhr);

  private static AuthProperties schalter() {
    return new AuthProperties(
        "geheimnis-mit-mindestens-32-zeichen-laenge",
        Duration.ofDays(1),
        Duration.ofHours(1),
        "fbcrm_session",
        true,
        JE_HERKUNFT,
        HERKUNFT_FENSTER,
        JE_STUNDE,
        STUNDEN_FENSTER,
        JE_TAG,
        TAGES_FENSTER,
        Duration.ofMinutes(10),
        5,
        List.of());
  }

  private void zaehle(final int anzahl, final Zaehlart art, final String schluessel) {
    IntStream.range(0, anzahl).forEach(unbenutzt -> limiter.record(art, schluessel));
  }

  @Test
  void isAllowed_givenNothingCounted_thenEveryKindAllows() {
    // When / Then
    assertThat(limiter.isAllowed(HERKUNFT, IP)).isTrue();
    assertThat(limiter.isAllowed(ADRESSE_STUNDE, MAIL)).isTrue();
    assertThat(limiter.isAllowed(ADRESSE_TAG, MAIL)).isTrue();
  }

  @Test
  void isAllowed_givenTenAttemptsFromOneOrigin_thenTheEleventhWithinFifteenMinutesIsRefused() {
    // Given
    zaehle(JE_HERKUNFT - 1, HERKUNFT, IP);
    uhr.springe(HERKUNFT_FENSTER.minusMinutes(1));
    limiter.record(HERKUNFT, IP);

    // When / Then — Kriterium 5: zehn Versuche in 15 Minuten, der elfte wartet.
    assertThat(limiter.isAllowed(HERKUNFT, IP)).isFalse();
  }

  @Test
  void isAllowed_givenNineAttemptsFromOneOrigin_thenTheTenthIsStillAllowed() {
    // Given
    zaehle(JE_HERKUNFT - 1, HERKUNFT, IP);

    // When / Then
    assertThat(limiter.isAllowed(HERKUNFT, IP)).isTrue();
  }

  @Test
  void isAllowed_givenFiveRequestsForOneAddress_thenTheSixthWithinTheHourIsRefused() {
    // Given — Kriterium 5: fuenf Anforderungen je Adresse und Stunde.
    zaehle(JE_STUNDE - 1, ADRESSE_STUNDE, MAIL);
    uhr.springe(STUNDEN_FENSTER.minusMinutes(1));
    limiter.record(ADRESSE_STUNDE, MAIL);

    // When / Then
    assertThat(limiter.isAllowed(ADRESSE_STUNDE, MAIL)).isFalse();
  }

  @Test
  void isAllowed_givenFourRequestsForOneAddress_thenTheFifthIsStillAllowed() {
    // Given
    zaehle(JE_STUNDE - 1, ADRESSE_STUNDE, MAIL);

    // When / Then
    assertThat(limiter.isAllowed(ADRESSE_STUNDE, MAIL)).isTrue();
  }

  @Test
  void isAllowed_givenTenRequestsForOneAddressOverTheDay_thenTheEleventhWithin24HoursIsRefused() {
    // Given — E27: je zwei Stunden eine Anforderung, die Stundengrenze greift nie.
    IntStream.range(0, JE_TAG)
        .forEach(
            unbenutzt -> {
              limiter.record(ADRESSE_TAG, MAIL);
              uhr.springe(Duration.ofHours(2));
            });

    // When / Then
    assertThat(limiter.isAllowed(ADRESSE_TAG, MAIL)).isFalse();
  }

  @Test
  void isAllowed_givenNineRequestsForOneAddressOverTheDay_thenTheTenthIsStillAllowed() {
    // Given
    zaehle(JE_TAG - 1, ADRESSE_TAG, MAIL);

    // When / Then
    assertThat(limiter.isAllowed(ADRESSE_TAG, MAIL)).isTrue();
  }

  @Test
  void isAllowed_givenOneKindFull_thenTheOtherKindsOfTheSameKeyAreUntouched() {
    // Given — jede Zaehlart fuehrt ihren eigenen Zaehler, auch unter demselben Schluessel.
    zaehle(JE_TAG, ADRESSE_TAG, MAIL);

    // When / Then
    assertThat(limiter.isAllowed(ADRESSE_TAG, MAIL)).isFalse();
    assertThat(limiter.isAllowed(ADRESSE_STUNDE, MAIL)).isTrue();
    assertThat(limiter.isAllowed(HERKUNFT, MAIL)).isTrue();
  }

  @Test
  void isAllowed_givenOneKeyFull_thenAnotherKeyOfTheSameKindIsUntouched() {
    // Given
    zaehle(JE_HERKUNFT, HERKUNFT, IP);
    zaehle(JE_STUNDE, ADRESSE_STUNDE, MAIL);

    // When / Then
    assertThat(limiter.isAllowed(HERKUNFT, ANDERE_IP)).isTrue();
    assertThat(limiter.isAllowed(ADRESSE_STUNDE, ANDERE_MAIL)).isTrue();
  }

  @Test
  void isAllowed_givenTheAddressInAnotherCase_thenCountsOnTheSameCounter() {
    // Given — sonst liesse sich die Grenze je Adresse durch Gross- und Kleinschreibung umgehen.
    zaehle(JE_STUNDE, ADRESSE_STUNDE, "Manne@Example.ORG");

    // When / Then
    assertThat(limiter.isAllowed(ADRESSE_STUNDE, MAIL)).isFalse();
  }

  @Test
  void isAllowed_givenTheOriginWindowPassed_thenTheOriginIsFreeAgain() {
    // Given
    zaehle(JE_HERKUNFT, HERKUNFT, IP);

    // When
    uhr.springe(HERKUNFT_FENSTER.plusSeconds(1));

    // Then
    assertThat(limiter.isAllowed(HERKUNFT, IP)).isTrue();
  }

  @Test
  void isAllowed_givenOneSecondBeforeTheEndOfTheOriginWindow_thenStillRefused() {
    // Given
    zaehle(JE_HERKUNFT, HERKUNFT, IP);

    // When
    uhr.springe(HERKUNFT_FENSTER.minusSeconds(1));

    // Then
    assertThat(limiter.isAllowed(HERKUNFT, IP)).isFalse();
  }

  @Test
  void isAllowed_givenTheOriginWindowPassed_thenTheHourlyAddressLimitStillHolds() {
    // Given — jede Zaehlart hat ihr eigenes Fenster.
    zaehle(JE_STUNDE, ADRESSE_STUNDE, MAIL);

    // When
    uhr.springe(HERKUNFT_FENSTER.plusSeconds(1));

    // Then
    assertThat(limiter.isAllowed(ADRESSE_STUNDE, MAIL)).isFalse();
  }

  @Test
  void isAllowed_givenTheHourPassed_thenTheHourlyAddressLimitIsFreeAgain() {
    // Given
    zaehle(JE_STUNDE, ADRESSE_STUNDE, MAIL);

    // When
    uhr.springe(STUNDEN_FENSTER.plusSeconds(1));

    // Then
    assertThat(limiter.isAllowed(ADRESSE_STUNDE, MAIL)).isTrue();
  }

  @Test
  void isAllowed_givenOneSecondBeforeTheEndOfTheHour_thenTheHourlyAddressLimitStillHolds() {
    // Given
    zaehle(JE_STUNDE, ADRESSE_STUNDE, MAIL);

    // When
    uhr.springe(STUNDEN_FENSTER.minusSeconds(1));

    // Then
    assertThat(limiter.isAllowed(ADRESSE_STUNDE, MAIL)).isFalse();
  }

  @Test
  void isAllowed_givenTheHourPassed_thenTheDailyAddressLimitStillHolds() {
    // Given
    zaehle(JE_TAG, ADRESSE_TAG, MAIL);

    // When
    uhr.springe(STUNDEN_FENSTER.plusSeconds(1));

    // Then
    assertThat(limiter.isAllowed(ADRESSE_TAG, MAIL)).isFalse();
  }

  @Test
  void isAllowed_givenTheDayPassed_thenTheDailyAddressLimitIsFreeAgain() {
    // Given
    zaehle(JE_TAG, ADRESSE_TAG, MAIL);

    // When
    uhr.springe(TAGES_FENSTER.plusSeconds(1));

    // Then
    assertThat(limiter.isAllowed(ADRESSE_TAG, MAIL)).isTrue();
  }

  @Test
  void isAllowed_givenOlderAttemptsAndOneFreshOne_thenCountsOnlyTheFreshOne() {
    // Given — das Fenster gleitet: Was herausgefallen ist, zaehlt nicht mehr.
    zaehle(JE_HERKUNFT - 1, HERKUNFT, IP);
    uhr.springe(HERKUNFT_FENSTER.plusSeconds(1));

    // When
    limiter.record(HERKUNFT, IP);

    // Then
    assertThat(limiter.isAllowed(HERKUNFT, IP)).isTrue();
  }

  @Test
  void restwartezeit_givenNothingCounted_thenZero() {
    // When / Then
    assertThat(limiter.restwartezeit(HERKUNFT, IP)).isZero();
  }

  @Test
  void restwartezeit_givenAnAttemptJustNow_thenTheWholeWindow() {
    // Given
    limiter.record(HERKUNFT, IP);

    // When / Then — genau 15 Minuten, nicht auf 16 aufgerundet.
    assertThat(limiter.restwartezeit(HERKUNFT, IP)).isEqualTo(HERKUNFT_FENSTER);
  }

  @Test
  void restwartezeit_givenARemainderBetweenTwoMinutes_thenRoundsUpToTheNextWholeMinute() {
    // Given
    limiter.record(HERKUNFT, IP);

    // When — es bleiben 11 Minuten und 50 Sekunden.
    uhr.springe(Duration.ofMinutes(3).plusSeconds(10));

    // Then
    assertThat(limiter.restwartezeit(HERKUNFT, IP)).isEqualTo(Duration.ofMinutes(12));
  }

  @Test
  void restwartezeit_givenOneNanosecondLeft_thenOneWholeMinute() {
    // Given
    limiter.record(ADRESSE_STUNDE, MAIL);

    // When
    uhr.springe(STUNDEN_FENSTER.minusNanos(1));

    // Then
    assertThat(limiter.restwartezeit(ADRESSE_STUNDE, MAIL)).isEqualTo(Duration.ofMinutes(1));
  }

  @Test
  void restwartezeit_givenSeveralEntries_thenFollowsTheOldestOne() {
    // Given
    limiter.record(ADRESSE_STUNDE, MAIL);
    uhr.springe(Duration.ofMinutes(20));
    limiter.record(ADRESSE_STUNDE, MAIL);

    // When
    uhr.springe(Duration.ofMinutes(10));

    // Then — der aelteste Eintrag faellt nach 60 - 30 Minuten heraus.
    assertThat(limiter.restwartezeit(ADRESSE_STUNDE, MAIL)).isEqualTo(Duration.ofMinutes(30));
  }

  @Test
  void restwartezeit_givenTheDailyKind_thenMeasuresAgainstTheDailyWindow() {
    // Given
    limiter.record(ADRESSE_TAG, MAIL);

    // When
    uhr.springe(Duration.ofHours(1));

    // Then
    assertThat(limiter.restwartezeit(ADRESSE_TAG, MAIL)).isEqualTo(Duration.ofHours(23));
  }

  @Test
  void restwartezeit_givenTheWindowPassed_thenZero() {
    // Given
    zaehle(JE_HERKUNFT, HERKUNFT, IP);

    // When
    uhr.springe(HERKUNFT_FENSTER);

    // Then
    assertThat(limiter.restwartezeit(HERKUNFT, IP)).isZero();
  }

  @Test
  void recordSuccess_thenClearsTheOriginOfThatAttempt() {
    // Given
    zaehle(JE_HERKUNFT, HERKUNFT, IP);

    // When
    limiter.recordSuccess(IP);

    // Then
    assertThat(limiter.isAllowed(HERKUNFT, IP)).isTrue();
  }

  @Test
  void recordSuccess_thenLeavesBothAddressCountersStanding() {
    // Given — E3: sonst setzte jede gelungene Anmeldung die Grenzen aus Kriterium 5 zurueck.
    zaehle(JE_STUNDE, ADRESSE_STUNDE, MAIL);
    zaehle(JE_TAG, ADRESSE_TAG, MAIL);

    // When
    limiter.recordSuccess(IP);

    // Then
    assertThat(limiter.isAllowed(ADRESSE_STUNDE, MAIL)).isFalse();
    assertThat(limiter.isAllowed(ADRESSE_TAG, MAIL)).isFalse();
  }

  @Test
  void recordSuccess_givenAnAddressCounterUnderTheOriginKey_thenLeavesItStanding() {
    // Given — geloescht wird die Zaehlart HERKUNFT, nicht jeder Zaehler unter diesem Schluessel.
    zaehle(JE_STUNDE, ADRESSE_STUNDE, IP);

    // When
    limiter.recordSuccess(IP);

    // Then
    assertThat(limiter.isAllowed(ADRESSE_STUNDE, IP)).isFalse();
  }

  @Test
  void recordSuccess_thenLeavesOtherOriginsStanding() {
    // Given
    zaehle(JE_HERKUNFT, HERKUNFT, ANDERE_IP);

    // When
    limiter.recordSuccess(IP);

    // Then
    assertThat(limiter.isAllowed(HERKUNFT, ANDERE_IP)).isFalse();
  }

  @Test
  void trackedCounters_givenFreshEntries_thenOneCounterPerKindAndKey() {
    // Given
    limiter.record(HERKUNFT, IP);
    limiter.record(ADRESSE_STUNDE, MAIL);
    limiter.record(ADRESSE_TAG, MAIL);
    limiter.record(ADRESSE_TAG, MAIL);

    // When / Then
    assertThat(limiter.trackedCounters()).isEqualTo(3);
  }

  @Test
  void trackedCounters_givenEntriesThatFellOutOfTheWindow_thenAReadDropsTheirCounter() {
    // Given — sonst legte jede durchprobierte Adresse einen Schluessel an, der nie verschwindet.
    limiter.record(HERKUNFT, IP);
    limiter.record(ADRESSE_STUNDE, MAIL);
    uhr.springe(STUNDEN_FENSTER.plusSeconds(1));

    // When
    limiter.isAllowed(HERKUNFT, IP);
    limiter.restwartezeit(ADRESSE_STUNDE, MAIL);

    // Then
    assertThat(limiter.trackedCounters()).isZero();
  }

  @Test
  void trackedCounters_givenOnlyOneKindExpired_thenKeepsTheOther() {
    // Given
    limiter.record(HERKUNFT, IP);
    limiter.record(ADRESSE_TAG, MAIL);
    uhr.springe(HERKUNFT_FENSTER.plusSeconds(1));

    // When
    limiter.isAllowed(HERKUNFT, IP);
    limiter.isAllowed(ADRESSE_TAG, MAIL);

    // Then
    assertThat(limiter.trackedCounters()).isEqualTo(1);
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
