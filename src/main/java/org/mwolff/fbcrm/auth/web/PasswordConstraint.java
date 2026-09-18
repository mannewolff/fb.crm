package org.mwolff.fbcrm.auth.web;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.jspecify.annotations.Nullable;

/**
 * Die einzige Passwortregel des Projekts: mindestens acht Zeichen (E26).
 *
 * <p>Bewusst <b>keine</b> Regel ueber Zeichenklassen. Erzwungene Sonderzeichen und Ziffern
 * verschieben das Passwort auf den Notizzettel neben dem Bildschirm, ohne die Entropie nennenswert
 * zu heben; die Laenge ist die Groesse, die wirkt (CLAUDE-security.md).
 *
 * <p>Die Meldung nennt die Mindestlaenge, weil eine Antwort, die nur „ist ungueltig" sagt, den
 * Aufrufer raten laesst (K6).
 *
 * <p>Genutzt vom Paket „Einrichtung" und vom Paket „Postausgangsfach und Passwort-Reset".
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.RECORD_COMPONENT, ElementType.FIELD, ElementType.PARAMETER})
@Constraint(validatedBy = PasswordConstraint.Validator.class)
public @interface PasswordConstraint {

  /** Die Mindestlaenge eines Passworts in Zeichen. */
  int MIN_LENGTH = 8;

  /** Der Text, den eine zu kurze Eingabe zurueckbekommt — er nennt die Mindestlaenge. */
  String MESSAGE = "muss mindestens " + MIN_LENGTH + " Zeichen lang sein";

  /** Die Meldung der Verletzung. */
  String message() default MESSAGE;

  /** Validierungsgruppen; von Bean Validation verlangt. */
  Class<?>[] groups() default {};

  /** Nutzlast; von Bean Validation verlangt. */
  Class<? extends Payload>[] payload() default {};

  /**
   * Prueft die Laenge.
   *
   * <p>Eine fehlende Eingabe ist hier <b>nicht</b> gueltig — anders als bei den meisten Constraints
   * von Bean Validation. Ein Passwort, das gar nicht da ist, ist kein Grenzfall fuer
   * {@code @NotBlank}, sondern schon die Antwort auf die Frage dieser Regel.
   */
  class Validator implements ConstraintValidator<PasswordConstraint, String> {

    @Override
    public boolean isValid(
        final @Nullable String passwort, final @Nullable ConstraintValidatorContext kontext) {
      return passwort != null && passwort.length() >= MIN_LENGTH;
    }
  }
}
