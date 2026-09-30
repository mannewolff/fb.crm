package org.mwolff.fbcrm.rechnung.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Die Regel der Farbe: jeder Anteil liegt zwischen 0 und 255.
 *
 * <p>Der Wert traegt sie selbst, damit ein Satz mit einem unmoeglichen Anteil beim Rechnen
 * auffaellt und nicht erst der Drucker eine Farbe ausserhalb des Bereichs auf das Blatt bringt.
 */
class FarbeTest {

  @Test
  void farbe_givenAnteileAtTheBounds_thenTheyAreKept() {
    // When
    final Farbe farbe = new Farbe(0, 128, 255);

    // Then — die Grenzen gelten noch als Farbe.
    assertThat(farbe.rot()).isZero();
    assertThat(farbe.gruen()).isEqualTo(128);
    assertThat(farbe.blau()).isEqualTo(255);
  }

  @ParameterizedTest
  @CsvSource({
    "-1, 0, 0, Rot",
    "256, 0, 0, Rot",
    "0, -1, 0, Gruen",
    "0, 256, 0, Gruen",
    "0, 0, -1, Blau",
    "0, 0, 256, Blau"
  })
  void farbe_givenAnAnteilOutsideTheRange_thenItIsRejected(
      final int rot, final int gruen, final int blau, final String name) {
    // When / Then — die Meldung nennt den Anteil, der nicht passt.
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Farbe(rot, gruen, blau))
        .withMessageContaining(name);
  }
}
