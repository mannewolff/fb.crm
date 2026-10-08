package org.mwolff.fbcrm.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Die Verzoegerung nach einem Fehlschlag: 200 bis 800 ms, zufaellig gezogen.
 *
 * <p>Gemessen wird echte Zeit — anders liesse sich nicht belegen, dass ueberhaupt gewartet wird.
 * Die Zufallsquelle ist ersetzt, damit die gezogene Dauer feststeht.
 */
@ExtendWith(MockitoExtension.class)
class FailedLoginDelayTest {

  private static final int SPANNE = 601;

  @Mock private Random random;

  private static Duration gemessen(final Runnable lauf) {
    final Instant vorher = Instant.now();
    lauf.run();
    return Duration.between(vorher, Instant.now());
  }

  @Test
  void apply_thenDrawsFromTheRangeOfSixHundredAndOneMilliseconds() {
    // Given
    when(random.nextInt(SPANNE)).thenReturn(0);

    // When
    new FailedLoginDelay(random).apply();

    // Then — 200 + [0, 600] deckt genau 200 bis 800 ms ab.
    verify(random).nextInt(SPANNE);
  }

  @Test
  void apply_givenTheLowestDraw_thenWaitsAtLeastTwoHundredMilliseconds() {
    // Given
    when(random.nextInt(SPANNE)).thenReturn(0);
    final FailedLoginDelay verzoegerung = new FailedLoginDelay(random);

    // When
    final Duration gedauert = gemessen(verzoegerung::apply);

    // Then
    assertThat(gedauert).isGreaterThanOrEqualTo(Duration.ofMillis(200));
  }

  @Test
  void apply_givenTheHighestDraw_thenWaitsAtLeastEightHundredMilliseconds() {
    // Given
    when(random.nextInt(SPANNE)).thenReturn(SPANNE - 1);
    final FailedLoginDelay verzoegerung = new FailedLoginDelay(random);

    // When
    final Duration gedauert = gemessen(verzoegerung::apply);

    // Then
    assertThat(gedauert).isGreaterThanOrEqualTo(Duration.ofMillis(800));
  }

  @Test
  void apply_givenAnInterruptedThread_thenRestoresTheInterruptFlag() {
    // Given — die Abbruchabsicht darf nicht im catch verschwinden.
    when(random.nextInt(SPANNE)).thenReturn(0);
    final FailedLoginDelay verzoegerung = new FailedLoginDelay(random);
    Thread.currentThread().interrupt();

    // When
    verzoegerung.apply();

    // Then — interrupted() liest das Zeichen und raeumt es zugleich fuer die naechsten Tests weg.
    assertThat(Thread.interrupted()).isTrue();
  }

  @Test
  void constructor_withoutARandomSource_thenUsesTheOneOfThePlatform() {
    // When
    final Duration gedauert = gemessen(new FailedLoginDelay()::apply);

    // Then
    assertThat(gedauert).isGreaterThanOrEqualTo(Duration.ofMillis(200));
  }
}
