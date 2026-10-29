package org.mwolff.fbcrm.firma.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Die Regel fuer das Land der Firma: ein Kuerzel der Laenderliste (Plan #461, E11). */
class LaenderkuerzelConstraintTest {

  private final LaenderkuerzelConstraint.Validator validator =
      new LaenderkuerzelConstraint.Validator();

  @ParameterizedTest
  @ValueSource(strings = {"DE", "AT", " AT ", "", "   "})
  void isValid_givenACodeOfTheListOrNothing_thenAccepts(final String eingabe) {
    // When / Then — Leerraum am Rand faellt bei der Normalisierung weg; ein leeres Land meldet
    // @NotBlank, nicht diese Regel, damit am Feld nur eine Meldung steht.
    assertThat(validator.isValid(eingabe, null)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"XX", "de", "DEU", "Deutschland"})
  void isValid_givenAnythingElse_thenRefuses(final String eingabe) {
    // When / Then
    assertThat(validator.isValid(eingabe, null)).isFalse();
  }

  @Test
  void isValid_givenNull_thenAccepts() {
    // When / Then — das fehlende Land meldet @NotBlank.
    assertThat(validator.isValid(null, null)).isTrue();
  }
}
