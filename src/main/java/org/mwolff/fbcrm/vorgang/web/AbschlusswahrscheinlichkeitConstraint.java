package org.mwolff.fbcrm.vorgang.web;

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
 * Die Form einer Abschlusswahrscheinlichkeit: ein Zehnerschritt von 0 bis 100 (Kriterium 21).
 *
 * <p><b>Eine Bedingung statt drei.</b> {@code @Min}, {@code @Max} und eine Schrittregel daneben
 * ergaeben fuer einen Wert wie 105 zwei Meldungen an einem Feld, und zwei Saetze uebereinander
 * sagen nicht mehr als der erste. Hier steht die ganze Aussage in einem Satz, und er nennt die
 * erwartete Gestalt.
 *
 * <p>Der fehlende Wert ist gueltig: Die Einschaetzung ist keine Pflichtangabe, und „nicht
 * eingeschaetzt" ist {@code null}.
 *
 * <p>Dieselbe Regel steht als CHECK in {@code V7__vorgang_pipeline_felder.sql}. Beide braucht es:
 * Die Datenbank ist die Schranke, die niemand umgeht, und diese Bedingung macht aus einer falschen
 * Eingabe eine Meldung am Feld statt eines Datenbankfehlers.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.RECORD_COMPONENT, ElementType.FIELD, ElementType.PARAMETER})
@Constraint(validatedBy = AbschlusswahrscheinlichkeitConstraint.Validator.class)
public @interface AbschlusswahrscheinlichkeitConstraint {

  /** Der Text, den eine Eingabe ausserhalb der Zehnerschritte zurueckbekommt. */
  String MESSAGE = "muss ein Zehnerschritt von 0 bis 100 sein";

  /** Die Meldung der Verletzung. */
  String message() default MESSAGE;

  /** Validierungsgruppen; von Bean Validation verlangt. */
  Class<?>[] groups() default {};

  /** Nutzlast; von Bean Validation verlangt. */
  Class<? extends Payload>[] payload() default {};

  /** Prueft Wertebereich und Schrittweite. */
  class Validator implements ConstraintValidator<AbschlusswahrscheinlichkeitConstraint, Integer> {

    /** Die Schrittweite der Auswahl (Kriterium 21). */
    private static final int SCHRITT = 10;

    /** Die obere Grenze; die untere ist 0. */
    private static final int HOECHSTWERT = 100;

    @Override
    public boolean isValid(
        final @Nullable Integer wahrscheinlichkeit,
        final @Nullable ConstraintValidatorContext kontext) {
      if (wahrscheinlichkeit == null) {
        return true;
      }
      final int wert = wahrscheinlichkeit;
      return wert >= 0 && wert <= HOECHSTWERT && wert % SCHRITT == 0;
    }
  }
}
