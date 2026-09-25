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

/**
 * Die Form einer E-Mail-Adresse: genau ein {@code @}, davor und danach Text, im Teil danach ein
 * Punkt (E8).
 *
 * <p>Bewusst <b>nicht</b> {@code @Email} aus Bean Validation: Dessen Regel nimmt {@code max@firma}
 * an, weil eine Adresse ohne Punkt in einem lokalen Netz zulaessig ist. In einem CRM ist sie
 * praktisch immer ein Tippfehler, und ein Angebot, das an eine solche Adresse geht, kommt nie an.
 *
 * <p>Der leere Wert ist gueltig: Die E-Mail-Adresse ist keine Pflichtangabe. Leerraum am Rand
 * faellt vor der Pruefung weg — die Normalisierung nimmt ihn ohnehin (E9), und eine Meldung wegen
 * eines Leerzeichens, das niemand sieht, waere Schikane.
 *
 * <p>Dieselbe Regel steht im Frontend als {@code lib/emailform.ts} und wird dort gegen dieselbe
 * Tabelle geprueft. Die Maske fuehrt damit frueh; entschieden wird hier.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.RECORD_COMPONENT, ElementType.FIELD, ElementType.PARAMETER})
@Constraint(validatedBy = AnsprechpartnerEmailConstraint.Validator.class)
public @interface AnsprechpartnerEmailConstraint {

  /** Der Text, den eine Eingabe ohne diese Form zurueckbekommt — er nennt die erwartete Gestalt. */
  String MESSAGE = "muss die Form name@beispiel.de haben";

  /** Die Meldung der Verletzung. */
  String message() default MESSAGE;

  /** Validierungsgruppen; von Bean Validation verlangt. */
  Class<?>[] groups() default {};

  /** Nutzlast; von Bean Validation verlangt. */
  Class<? extends Payload>[] payload() default {};

  /** Prueft die Form. */
  class Validator implements ConstraintValidator<AnsprechpartnerEmailConstraint, String> {

    @Override
    public boolean isValid(
        final @Nullable String email, final @Nullable ConstraintValidatorContext kontext) {
      if (email == null) {
        return true;
      }
      final String sauber = email.strip();
      if (sauber.isEmpty()) {
        return true;
      }
      // Die Grenze -1 haelt die leeren Stuecke am Rand fest; ohne sie faende „max@" nur ein
      // Stueck und saehe damit aus wie eine Adresse ganz ohne @.
      final String[] teile = sauber.split("@", -1);
      return teile.length == 2 && !teile[0].isEmpty() && teile[1].contains(".");
    }
  }
}
