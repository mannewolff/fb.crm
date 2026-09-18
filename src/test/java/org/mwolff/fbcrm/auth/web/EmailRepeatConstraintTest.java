package org.mwolff.fbcrm.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Die Wiederholung der E-Mail-Adresse (E6).
 *
 * <p>Geprueft wird gegen den echten Bean-Validation-Motor und nicht gegen den Validator allein: Der
 * Nachweis dieses Tests ist, dass die Verletzung am Feld {@code emailRepeat} haengt. Nur dann wird
 * daraus im {@code GlobalExceptionHandler} ein Eintrag unter {@code fieldErrors.emailRepeat}, den
 * die Oberflaeche an das richtige Eingabefeld schreiben kann. Ein Test gegen den Validator allein
 * saehe den Pfad nicht.
 */
class EmailRepeatConstraintTest {

  private static final String MAIL = "manne@example.org";
  private static final String PASSWORT = "sicheres-passwort";

  private static ValidatorFactory factory;
  private static Validator validator;

  @BeforeAll
  static void baueDenValidator() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void schliesseDenValidator() {
    factory.close();
  }

  private static SetupRequest anfrage(final String email, final String wiederholung) {
    return new SetupRequest(email, wiederholung, "Manne", PASSWORT, "einmal-schluessel");
  }

  private static Set<String> verletzteFelder(final SetupRequest anfrage) {
    return validator.validate(anfrage).stream()
        .map(ConstraintViolation::getPropertyPath)
        .map(String::valueOf)
        .collect(Collectors.toSet());
  }

  /** Nur die Meldungen der Wiederholungsregel — die uebrigen Constraints melden eigenstaendig. */
  private static List<String> wiederholungsFehler(final SetupRequest anfrage) {
    return validator.validate(anfrage).stream()
        .map(ConstraintViolation::getMessage)
        .filter(EmailRepeatConstraint.MESSAGE::equals)
        .toList();
  }

  @Test
  void validate_givenAMatchingRepeat_thenReportsNothing() {
    // When / Then
    assertThat(validator.validate(anfrage(MAIL, MAIL))).isEmpty();
  }

  @Test
  void validate_givenARepeatInDifferentCase_thenReportsNothing() {
    // When / Then — Adressen sind ohne Ruecksicht auf Gross- und Kleinschreibung eindeutig
    // (account_email_key auf lower(email)); ein Abweisen waere Schikane, kein Schutz.
    assertThat(validator.validate(anfrage(MAIL, "Manne@Example.ORG"))).isEmpty();
  }

  @Test
  void validate_givenADifferentRepeat_thenReportsItOnTheRepeatField() {
    // When / Then — E6: der Fehler haengt am Formularfeld, nicht am ganzen Rumpf.
    assertThat(verletzteFelder(anfrage(MAIL, "vertippt@example.org")))
        .containsExactly("emailRepeat");
  }

  @Test
  void validate_givenADifferentRepeat_thenTheMessageNamesWhatIsExpected() {
    // When / Then
    assertThat(wiederholungsFehler(anfrage(MAIL, "vertippt@example.org")))
        .containsExactly(EmailRepeatConstraint.MESSAGE);
  }

  @Test
  void validate_givenAMissingRepeat_thenLeavesTheComplaintToNotBlank() {
    // When / Then — ein fehlendes Feld meldet @NotBlank; die Wiederholungsregel schweigt dazu,
    // sonst stuenden zwei Meldungen fuer dasselbe leere Feld.
    assertThat(wiederholungsFehler(anfrage(MAIL, null))).isEmpty();
  }

  @Test
  void validate_givenAMissingEmail_thenLeavesTheComplaintToNotBlank() {
    // When / Then — dasselbe von der anderen Seite: ohne Adresse gibt es nichts zu wiederholen.
    assertThat(wiederholungsFehler(anfrage(null, MAIL))).isEmpty();
  }
}
