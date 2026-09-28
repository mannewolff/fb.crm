package org.mwolff.fbcrm.auftrag.web;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * Die Regel des Leistungszeitraums: ganz oder gar nicht, und das Ende nicht vor dem Beginn (E21).
 *
 * <p>Die Constraint sitzt am ganzen Rumpf, weil sie zwei Felder gegeneinander liest. Gemeldet wird
 * trotzdem <b>am Feld</b> — nach dem Muster von {@code vorgang.web.EintragConstraint}: Nur so
 * entsteht im {@code GlobalExceptionHandler} ein Eintrag unter {@code fieldErrors.<feld>}, den die
 * Maske an das richtige Eingabefeld schreibt. Gemeldet wird am <b>fehlenden</b> Tag, nicht am
 * vorhandenen: Dort muss der Anwender etwas tun.
 *
 * <p>Dieselbe Regel steht ein zweites Mal als {@code CHECK} in {@code V8__auftrag.sql}. Das ist
 * keine Doppelung ohne Grund: Die Meldung am Feld braucht die Maske, den Riegel die
 * Datenintegritaet. Ein Datenbankfehler ohne Feldnamen ist fuer die Maske unbrauchbar, und eine
 * Pruefung, die nur in der Anwendung steht, ist keine Schranke.
 *
 * <p>Der gleiche Tag als Beginn und Ende zaehlt noch mit — ein Leistungszeitraum von einem Tag.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Constraint(validatedBy = LeistungszeitraumConstraint.Zeitraum.class)
public @interface LeistungszeitraumConstraint {

  /** Das Feld des ersten Tages. */
  String FELD_AB = "leistungAb";

  /** Das Feld des letzten Tages. */
  String FELD_BIS = "leistungBis";

  /** Ein Zeitraum mit Beginn, aber ohne Ende. */
  String ENDE_FEHLT = "Zu einem Leistungszeitraum gehoert auch sein Ende.";

  /** Ein Zeitraum mit Ende, aber ohne Beginn. */
  String BEGINN_FEHLT = "Zu einem Leistungszeitraum gehoert auch sein Beginn.";

  /** Ein Ende vor dem Beginn. */
  String ENDE_VOR_BEGINN = "Das Ende darf nicht vor dem Beginn des Leistungszeitraums liegen.";

  /**
   * Die Meldung der Verletzung. Jede Regel setzt ihre eigene; dieser Wert steht nur da, weil Bean
   * Validation ihn verlangt.
   */
  String message() default "Der Leistungszeitraum ist nicht schluessig.";

  /** Validierungsgruppen; von Bean Validation verlangt. */
  Class<?>[] groups() default {};

  /** Nutzlast; von Bean Validation verlangt. */
  Class<? extends Payload>[] payload() default {};

  /** Die Regel an der Anfrage zum Anlegen eines Auftrags. */
  class Zeitraum
      implements ConstraintValidator<LeistungszeitraumConstraint, AuftragAnlegenRequest> {

    @Override
    public boolean isValid(
        final AuftragAnlegenRequest anfrage, final ConstraintValidatorContext kontext) {
      kontext.disableDefaultConstraintViolation();
      return gueltig(anfrage.leistungAb(), anfrage.leistungBis(), kontext);
    }

    /*
     * Hoechstens eine Meldung je Anfrage: Fehlt ein Tag, ist der Vergleich der beiden gegenstandslos.
     *
     * Ausgeschrieben und nicht als 'melde(...) || ...': Ein Aufruf, der immer falsch liefert, haengt
     * an einer Verzweigung, die niemand nehmen kann, und eine unerreichbare Verzweigung ist weder
     * pruefbar noch lesbar.
     */
    private static boolean gueltig(
        final @Nullable LocalDate ab,
        final @Nullable LocalDate bis,
        final ConstraintValidatorContext kontext) {
      if (ab == null) {
        if (bis == null) {
          return true;
        }
        melde(kontext, BEGINN_FEHLT, FELD_AB);
        return false;
      }
      if (bis == null) {
        melde(kontext, ENDE_FEHLT, FELD_BIS);
        return false;
      }
      if (bis.isBefore(ab)) {
        melde(kontext, ENDE_VOR_BEGINN, FELD_BIS);
        return false;
      }
      return true;
    }

    /* Haengt die Meldung an das genannte Feld statt an den ganzen Rumpf. */
    private static void melde(
        final ConstraintValidatorContext kontext, final String meldung, final String feld) {
      kontext
          .buildConstraintViolationWithTemplate(meldung)
          .addPropertyNode(feld)
          .addConstraintViolation();
    }
  }
}
