package org.mwolff.fbcrm.vorgang.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Die Toleranz aus E15: Ein Zeitpunkt gilt erst als „in der Zukunft", wenn er mehr als eine Minute
 * hinter der Uhr der Anwendung liegt.
 *
 * <p>Die Grenze wird von beiden Seiten geprueft — 60 s noch angenommen, 61 s abgewiesen —, weil
 * genau dieser eine Schritt die Zusage von Kriterium 14 von der Nachsicht gegenueber einer
 * vorgehenden Browser-Uhr trennt.
 */
class ZeitpunktgrenzeTest {

  private static final Instant JETZT = Instant.parse("2026-09-12T09:00:00Z");

  @Test
  void inDerZukunft_givenAPastMoment_thenIsFalse() {
    // When
    final boolean zukunft = Zeitpunktgrenze.inDerZukunft(JETZT.minusSeconds(3600), JETZT);

    // Then
    assertThat(zukunft).isFalse();
  }

  @Test
  void inDerZukunft_givenFiftyNineSecondsAhead_thenIsFalse() {
    // When — die Uhr des Browsers geht vor, die Vorbelegung „jetzt" bleibt brauchbar (E15).
    final boolean zukunft = Zeitpunktgrenze.inDerZukunft(JETZT.plusSeconds(59), JETZT);

    // Then
    assertThat(zukunft).isFalse();
  }

  @Test
  void inDerZukunft_givenExactlySixtySecondsAhead_thenIsFalse() {
    // When — die Toleranz selbst ist noch erlaubt.
    final boolean zukunft =
        Zeitpunktgrenze.inDerZukunft(JETZT.plus(Zeitpunktgrenze.TOLERANZ), JETZT);

    // Then
    assertThat(zukunft).isFalse();
  }

  @Test
  void inDerZukunft_givenSixtyOneSecondsAhead_thenIsTrue() {
    // When — ein Schritt ueber die Toleranz hinaus.
    final boolean zukunft = Zeitpunktgrenze.inDerZukunft(JETZT.plusSeconds(61), JETZT);

    // Then
    assertThat(zukunft).isTrue();
  }
}
