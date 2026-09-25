package org.mwolff.fbcrm.vorgang.web;

import jakarta.validation.Configuration;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorFactory;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.time.Clock;

/**
 * Baut eine Bean-Validation-Fabrik, deren Validatoren eine feste Uhr bekommen.
 *
 * <p>Die klassenweite Constraint der Eintragsanfragen prueft den Zeitpunkt gegen die {@code
 * Clock}-Bean (E15). Im Betrieb reicht Spring sie ueber {@code SpringConstraintValidatorFactory}
 * hinein; im Test gibt es keinen Anwendungskontext, und die Standardfabrik von Hibernate Validator
 * sucht einen parameterlosen Konstruktor, den es hier nicht gibt.
 *
 * <p>Deshalb diese Fabrik: Sie kennt genau die beiden Validatoren dieses Pakets und reicht alles
 * andere — {@code @NotNull}, {@code @NotBlank} — unveraendert an die Standardfabrik weiter. Nur so
 * laeuft im Test dieselbe Kette wie im Betrieb, samt echter Meldungen und echter Property-Pfade.
 */
final class Pruefer {

  private Pruefer() {}

  /** Eine Fabrik, deren Eintrags-Validatoren gegen {@code uhr} messen. */
  static ValidatorFactory mitUhr(final Clock uhr) {
    final Configuration<?> konfiguration = Validation.byDefaultProvider().configure();
    return konfiguration
        .constraintValidatorFactory(
            new UhrFabrik(konfiguration.getDefaultConstraintValidatorFactory(), uhr))
        .buildValidatorFactory();
  }

  private record UhrFabrik(ConstraintValidatorFactory standard, Clock uhr)
      implements ConstraintValidatorFactory {

    @Override
    public <T extends ConstraintValidator<?, ?>> T getInstance(final Class<T> art) {
      if (art == EintragConstraint.NeuerEintrag.class) {
        return art.cast(new EintragConstraint.NeuerEintrag(uhr));
      }
      if (art == EintragConstraint.Aenderung.class) {
        return art.cast(new EintragConstraint.Aenderung(uhr));
      }
      return standard.getInstance(art);
    }

    @Override
    public void releaseInstance(final ConstraintValidator<?, ?> instanz) {
      standard.releaseInstance(instanz);
    }
  }
}
