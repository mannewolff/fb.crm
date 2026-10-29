package org.mwolff.fbcrm.firma.web;

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
import org.mwolff.fbcrm.common.Laenderliste;

/**
 * Das Land der Firma ist ein Kuerzel der {@link Laenderliste} (Plan #461, E11).
 *
 * <p>Der leere Wert ist hier gueltig: Dass das Land fehlt, meldet {@code @NotBlank} — so steht am
 * Feld genau eine Meldung. Leerraum am Rand faellt vor der Pruefung weg, wie bei der Normalisierung
 * in {@code FirmaDaten}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.RECORD_COMPONENT, ElementType.FIELD, ElementType.PARAMETER})
@Constraint(validatedBy = LaenderkuerzelConstraint.Validator.class)
public @interface LaenderkuerzelConstraint {

  /** Der Text, den ein unbekanntes Kuerzel zurueckbekommt. */
  String MELDUNG = "muss ein Land aus der Länderliste sein";

  /** Die Meldung der Verletzung. */
  String message() default MELDUNG;

  /** Validierungsgruppen; von Bean Validation verlangt. */
  Class<?>[] groups() default {};

  /** Nutzlast; von Bean Validation verlangt. */
  Class<? extends Payload>[] payload() default {};

  /** Prueft das Kuerzel. */
  class Validator implements ConstraintValidator<LaenderkuerzelConstraint, String> {

    @Override
    public boolean isValid(
        final @Nullable String land, final @Nullable ConstraintValidatorContext kontext) {
      if (land == null) {
        return true;
      }
      final String sauber = land.strip();
      return sauber.isEmpty() || Laenderliste.bekannt(sauber);
    }
  }
}
