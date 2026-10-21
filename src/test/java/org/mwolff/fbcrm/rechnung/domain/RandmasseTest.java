package org.mwolff.fbcrm.rechnung.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.function.IntFunction;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Die sechs Raender in Zehntel-Millimetern und ihre Umrechnung in Punkte (Plan #363, E8).
 *
 * <p>Jeder Wert wird einzeln negativ gesetzt, damit eine vergessene Pruefung an genau einem Rand
 * auffiele (fachliche Quelle #361, Kriterium 13, erster Satz). Die Null ist erlaubt — ein Rand von
 * 0 mm ist kein negativer.
 */
class RandmasseTest {

  private static Stream<Arguments> einzelnNegativ() {
    return Stream.of(
        Arguments.of(
            "links", (IntFunction<Randmasse>) w -> new Randmasse(w, 200, 985, 200, 200, 200)),
        Arguments.of(
            "rechts", (IntFunction<Randmasse>) w -> new Randmasse(250, w, 985, 200, 200, 200)),
        Arguments.of(
            "inhaltOben", (IntFunction<Randmasse>) w -> new Randmasse(250, 200, w, 200, 200, 200)),
        Arguments.of(
            "unten", (IntFunction<Randmasse>) w -> new Randmasse(250, 200, 985, w, 200, 200)),
        Arguments.of(
            "folgeOben", (IntFunction<Randmasse>) w -> new Randmasse(250, 200, 985, 200, w, 200)),
        Arguments.of(
            "folgeUnten", (IntFunction<Randmasse>) w -> new Randmasse(250, 200, 985, 200, 200, w)));
  }

  @Test
  void new_thenCarriesTheSixValues() {
    // When
    final Randmasse raender = new Randmasse(250, 200, 985, 210, 220, 230);

    // Then
    assertThat(raender)
        .satisfies(
            r -> assertThat(r.links()).isEqualTo(250),
            r -> assertThat(r.rechts()).isEqualTo(200),
            r -> assertThat(r.inhaltOben()).isEqualTo(985),
            r -> assertThat(r.unten()).isEqualTo(210),
            r -> assertThat(r.folgeOben()).isEqualTo(220),
            r -> assertThat(r.folgeUnten()).isEqualTo(230));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("einzelnNegativ")
  void new_givenOneNegativeValue_thenRejected(
      final String rand, final IntFunction<Randmasse> mitWert) {
    // When / Then
    assertThatThrownBy(() -> mitWert.apply(-1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(rand);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("einzelnNegativ")
  void new_givenZero_thenAccepted(final String rand, final IntFunction<Randmasse> mitWert) {
    // When / Then — die Grenze liegt unter der Null, nicht auf ihr.
    assertThatCode(() -> mitWert.apply(0)).doesNotThrowAnyException();
  }

  @ParameterizedTest
  @CsvSource({
    // die Vorgaben aus Kriterium 15
    "250, 71",
    "200, 57",
    "985, 279",
    // die Rundungsgrenze: 0,2 mm sind 0,567 pt und runden auf, 0,1 mm sind 0,283 pt und ab
    "1, 0",
    "2, 1",
    "0, 0"
  })
  void inPunkten_thenRoundsHalfUpToWholePoints(final int zehntelMm, final int punkte) {
    // When / Then
    assertThat(Randmasse.inPunkten(zehntelMm)).isEqualTo(punkte);
  }
}
