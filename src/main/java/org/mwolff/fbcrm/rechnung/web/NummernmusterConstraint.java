package org.mwolff.fbcrm.rechnung.web;

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
import org.mwolff.fbcrm.rechnung.domain.Nummernmuster;

/**
 * Die Schreibweise der Rechnungsnummer als Pruefregel an der Schnittstelle (#159, Kriterium 8).
 *
 * <p>Eine eigene Regel und nicht {@code @Pattern}: Die Bedingungen — genau ein Nummern-Platzhalter,
 * hoechstens ein Jahres-Platzhalter, die Laengengrenze — stehen schon in {@link Nummernmuster} und
 * entscheiden dort ueber das Wertobjekt. Als zweiter regulaerer Ausdruck daneben liefen die beiden
 * Fassungen auseinander; hier wird dieselbe Regel befragt.
 *
 * <p>Und eine Pruefregel und nicht der Record-Konstruktor allein: Der wirft eine {@code
 * IllegalArgumentException}, und die waere nach aussen ein 500. Die Bean Validation laeuft davor
 * und macht daraus {@code fieldErrors} am Feld {@code nummerMuster}.
 *
 * <p>Das Muster ist <b>Pflicht</b>: {@code null} ist ungueltig. Ein fehlendes Feld ist kein leeres
 * Muster, sondern gar keines — beide bekommen dieselbe Meldung, damit die Maske nicht zwei Formen
 * derselben Aussage lesen muss.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.RECORD_COMPONENT, ElementType.FIELD, ElementType.PARAMETER})
@Constraint(validatedBy = NummernmusterConstraint.Validator.class)
public @interface NummernmusterConstraint {

  /** Der Text, den eine Eingabe ohne diese Form zurueckbekommt — er nennt die Platzhalter. */
  String MELDUNG =
      "muss genau einen Platzhalter fuer die Nummer enthalten, etwa {NNNN}-{JJJJ}, und hoechstens"
          + " einen fuer das Jahr ({JJJJ} oder {JJ})";

  /** Die Meldung der Verletzung. */
  String message() default MELDUNG;

  /** Validierungsgruppen; von Bean Validation verlangt. */
  Class<?>[] groups() default {};

  /** Nutzlast; von Bean Validation verlangt. */
  Class<? extends Payload>[] payload() default {};

  /** Prueft die Schreibweise, indem sie {@link Nummernmuster} befragt. */
  class Validator implements ConstraintValidator<NummernmusterConstraint, String> {

    @Override
    public boolean isValid(
        final @Nullable String muster, final @Nullable ConstraintValidatorContext kontext) {
      return muster != null && Nummernmuster.istGueltig(muster);
    }
  }
}
