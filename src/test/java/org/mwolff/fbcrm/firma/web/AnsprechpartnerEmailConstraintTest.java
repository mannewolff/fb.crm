package org.mwolff.fbcrm.firma.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Die E-Mail-Regel des Ansprechpartners (E8).
 *
 * <p>Dieselbe Tabelle steht im Frontend als {@code lib/emailform.ts} — die beiden Seiten muessen
 * dieselbe Eingabe gleich beurteilen, sonst weist die Maske etwas ab, das der Server annaehme, oder
 * umgekehrt.
 */
class AnsprechpartnerEmailConstraintTest {

  private final AnsprechpartnerEmailConstraint.Validator validator =
      new AnsprechpartnerEmailConstraint.Validator();

  @ParameterizedTest
  @ValueSource(strings = {"max@firma.de", "  max@firma.de  ", "", "   "})
  void isValid_givenAnAcceptedValue_thenAccepts(final String eingabe) {
    // When / Then — der leere Wert ist gueltig: Die E-Mail-Adresse ist keine Pflichtangabe, und
    // Leerraum am Rand faellt bei der Normalisierung ohnehin weg (E9).
    assertThat(validator.isValid(eingabe, null)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"max@firma", "max@@firma.de", "a@b@c.de", "@firma.de", "max@", "maxfirma.de"})
  void isValid_givenARefusedValue_thenRefuses(final String eingabe) {
    // When / Then — {@code @Email} aus Bean Validation nimmt „max@firma" an; genau deshalb steht
    // hier eine eigene Regel (E8).
    assertThat(validator.isValid(eingabe, null)).isFalse();
  }

  @Test
  void isValid_givenNull_thenAccepts() {
    // When / Then — ein fehlendes Feld ist keine falsche Adresse, sondern gar keine.
    assertThat(validator.isValid(null, null)).isTrue();
  }

  @Test
  void message_thenNamesTheExpectedForm() {
    // When / Then — K6: die Antwort soll sagen, woran es lag, nicht nur dass es lag.
    assertThat(AnsprechpartnerEmailConstraint.MESSAGE).contains("@");
  }
}
