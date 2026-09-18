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
 * Die Wiederholung der E-Mail-Adresse muss zur Adresse passen (E6).
 *
 * <p>Die Regel sitzt am ganzen Rumpf, weil sie zwei Felder vergleicht — gemeldet wird sie aber
 * ausdruecklich am Feld {@code emailRepeat}. Nur so entsteht im {@code GlobalExceptionHandler} ein
 * Eintrag unter {@code fieldErrors.emailRepeat}, den die Oberflaeche an das richtige Eingabefeld
 * schreiben kann; eine Verletzung ohne Feldbezug landete als Meldung ueber dem ganzen Formular.
 *
 * <p>Verglichen wird <b>ohne Ruecksicht auf Gross- und Kleinschreibung</b>: Adressen sind genau so
 * eindeutig ({@code account_email_key} auf {@code lower(email)}). Ein Abweisen wegen eines grossen
 * Anfangsbuchstabens waere Schikane und kein Schutz — der Zweck der Wiederholung ist der Vertipper,
 * der zu einer anderen Postadresse fuehrt.
 *
 * <p>Fehlt eines der beiden Felder, schweigt die Regel: Das meldet {@code @NotBlank}, und zwei
 * Meldungen fuer dasselbe leere Feld helfen niemandem.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Constraint(validatedBy = EmailRepeatConstraint.Validator.class)
public @interface EmailRepeatConstraint {

  /** Das Feld, an dem die Verletzung haengt. */
  String FIELD = "emailRepeat";

  /** Der Text, den eine abweichende Wiederholung zurueckbekommt. */
  String MESSAGE = "muss der E-Mail-Adresse entsprechen";

  /** Die Meldung der Verletzung. */
  String message() default MESSAGE;

  /** Validierungsgruppen; von Bean Validation verlangt. */
  Class<?>[] groups() default {};

  /** Nutzlast; von Bean Validation verlangt. */
  Class<? extends Payload>[] payload() default {};

  /** Vergleicht Adresse und Wiederholung und haengt die Verletzung an das Wiederholungsfeld. */
  class Validator implements ConstraintValidator<EmailRepeatConstraint, SetupRequest> {

    @Override
    public boolean isValid(final SetupRequest anfrage, final ConstraintValidatorContext kontext) {
      if (passt(anfrage.email(), anfrage.emailRepeat())) {
        return true;
      }
      kontext.disableDefaultConstraintViolation();
      kontext
          .buildConstraintViolationWithTemplate(kontext.getDefaultConstraintMessageTemplate())
          .addPropertyNode(FIELD)
          .addConstraintViolation();
      return false;
    }

    /*
     * Jackson setzt fuer ein fehlendes JSON-Feld null ein, auch wenn die Record-Komponente
     * non-null deklariert ist — die Pruefung hier faengt genau diesen Weg ab.
     */
    private static boolean passt(
        final @Nullable String email, final @Nullable String wiederholung) {
      return email == null || wiederholung == null || wiederholung.equalsIgnoreCase(email);
    }
  }
}
