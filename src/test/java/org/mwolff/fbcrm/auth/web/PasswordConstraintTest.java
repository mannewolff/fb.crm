package org.mwolff.fbcrm.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Die einzige Passwortregel des Projekts: mindestens acht Zeichen (E26).
 *
 * <p>Bewusst keine Regel ueber Zeichenklassen. Erzwungene Sonderzeichen verschieben das Passwort in
 * den Notizzettel, ohne die Entropie nennenswert zu heben; die Laenge ist die Groesse, die wirkt
 * (CLAUDE-security.md).
 */
class PasswordConstraintTest {

  private final PasswordConstraint.Validator validator = new PasswordConstraint.Validator();

  @Test
  void isValid_givenSevenCharacters_thenRefuses() {
    // When / Then — der Grenzfall unterhalb der Schwelle.
    assertThat(validator.isValid("1234567", null)).isFalse();
  }

  @Test
  void isValid_givenExactlyEightCharacters_thenAccepts() {
    // When / Then — der Grenzfall auf der Schwelle.
    assertThat(validator.isValid("12345678", null)).isTrue();
  }

  @Test
  void isValid_givenALongPassphrase_thenAccepts() {
    // When / Then
    assertThat(validator.isValid("ein langer satz als passwort", null)).isTrue();
  }

  @Test
  void isValid_givenNull_thenRefuses() {
    // When / Then — ein fehlendes Feld ist kein gueltiges Passwort.
    assertThat(validator.isValid(null, null)).isFalse();
  }

  @Test
  void message_thenNamesTheMinimumLength() {
    // When / Then — K6: die Antwort soll sagen, woran es lag, nicht nur dass es lag.
    assertThat(PasswordConstraint.MESSAGE).contains(String.valueOf(PasswordConstraint.MIN_LENGTH));
  }
}
