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
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Uploadgrenze;
import org.mwolff.fbcrm.vorgang.domain.Dateiname;
import org.mwolff.fbcrm.vorgang.domain.Eintragsart;
import org.mwolff.fbcrm.vorgang.domain.Zeitpunktgrenze;
import org.springframework.web.multipart.MultipartFile;

/**
 * Die Regeln einer Eintragsanfrage, die mehr als ein Feld betreffen (Kriterien 13, 14, 18).
 *
 * <p>Die Constraint sitzt am ganzen Rumpf, weil ihre Regeln zwei Felder gegeneinander lesen: Was
 * Pflicht ist, haengt an der gewaehlten Art, und ob ein Zeitpunkt in der Zukunft liegt, haengt an
 * der Uhr der Anwendung. Gemeldet wird trotzdem <b>am Feld</b> — {@code text}, {@code datei} oder
 * {@code geschehenAm} —, denn nur so entsteht im {@code GlobalExceptionHandler} ein Eintrag unter
 * {@code fieldErrors.<feld>}, den die Maske an das richtige Eingabefeld schreiben kann. Dasselbe
 * Muster wie {@code auth/web/EmailRepeatConstraint}.
 *
 * <p>Zwei Validatoren, eine Annotation: Der Weg zum Anlegen kennt Art und Datei, der Weg zum
 * Aendern nur Text und Zeitpunkt. Bean Validation waehlt den Validator nach dem annotierten Typ.
 *
 * <p>Fehlende Pflichtangaben — Art, Zeitpunkt — meldet {@code @NotNull} am Feld. Die Constraint
 * schweigt dazu: Zwei Meldungen fuer dasselbe leere Feld helfen niemandem.
 *
 * <p><b>Der Riegel gegen die dritte Art.</b> {@code EREIGNIS} ist ein Wert der Aufzaehlung und
 * deshalb bindbar, aber kein Aufrufer von aussen darf ihn einreichen: Ereignisse vermerkt allein
 * {@code EreignisVermerkenUseCase} (Kriterium 19). Ohne diese Regel liefe {@code art=EREIGNIS} als
 * Kommentar weiter und endete in der Anwendungsschicht in einem 500. Gemeldet wird am Feld {@code
 * art}, weil dort der Grund liegt.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Constraint(validatedBy = {EintragConstraint.NeuerEintrag.class, EintragConstraint.Aenderung.class})
public @interface EintragConstraint {

  /** Das Feld der Eintragsart. */
  String FELD_ART = "art";

  /** Das Feld des Textes. */
  String FELD_TEXT = "text";

  /** Das Feld der hochgeladenen Datei. */
  String FELD_DATEI = "datei";

  /** Das Feld des Zeitpunkts des Geschehens. */
  String FELD_ZEITPUNKT = "geschehenAm";

  /** Die Art {@code EREIGNIS} in einer Anfrage von aussen. */
  String EREIGNIS_NICHT_EINREICHBAR =
      "Ein Ereignis vermerkt die Anwendung selbst und laesst sich nicht eintragen.";

  /** Ein Kommentar ohne Text. */
  String TEXT_FEHLT = "Ein Kommentar braucht einen Text.";

  /** Ein Anhang ohne Datei. */
  String DATEI_FEHLT = "Ein Anhang braucht eine Datei.";

  /** Ein Dateiname, von dem die Saeuberung nach E13 nichts uebrig laesst. */
  String DATEINAME_UNBRAUCHBAR = "Der Dateiname ist unbrauchbar.";

  /** Ein Zeitpunkt jenseits der Toleranz aus E15. */
  String ZUKUNFT = "Der Zeitpunkt darf nicht in der Zukunft liegen.";

  /**
   * Die Meldung der Verletzung. Jede Regel setzt ihre eigene; dieser Wert steht nur da, weil Bean
   * Validation ihn verlangt.
   */
  String message() default "Die Eingabe passt nicht zur gewaehlten Art.";

  /** Validierungsgruppen; von Bean Validation verlangt. */
  Class<?>[] groups() default {};

  /** Nutzlast; von Bean Validation verlangt. */
  Class<? extends Payload>[] payload() default {};

  /**
   * Was beide Validatoren teilen: die Zeitpunktregel und das Melden am Feld.
   *
   * <p>Zwei reine Funktionen und keine gemeinsame Oberklasse: Geteilt wird Verhalten, nicht Zustand
   * — die Uhr haelt jeder Validator selbst, weil Bean Validation sie ihm einzeln in den Konstruktor
   * reicht.
   */
  final class Regeln {

    private Regeln() {}

    /**
     * Meldet einen Zeitpunkt jenseits der Toleranz am Feld und sagt, ob die Pruefung bestand.
     *
     * <p>Ein fehlender Zeitpunkt gilt hier als gueltig — den meldet {@code @NotNull}.
     */
    static boolean zeitpunktGueltig(
        final @Nullable Instant geschehenAm,
        final Clock clock,
        final ConstraintValidatorContext kontext) {
      if (geschehenAm == null || !Zeitpunktgrenze.inDerZukunft(geschehenAm, clock.instant())) {
        return true;
      }
      melde(kontext, ZUKUNFT, FELD_ZEITPUNKT);
      return false;
    }

    /** Haengt die Meldung an das genannte Feld statt an den ganzen Rumpf. */
    static void melde(
        final ConstraintValidatorContext kontext, final String meldung, final String feld) {
      kontext
          .buildConstraintViolationWithTemplate(meldung)
          .addPropertyNode(feld)
          .addConstraintViolation();
    }
  }

  /** Die Regeln fuer einen neuen Eintrag: Art, Text, Datei und Zeitpunkt. */
  class NeuerEintrag implements ConstraintValidator<EintragConstraint, EintragRequest> {

    private final Clock clock;

    NeuerEintrag(final Clock clock) {
      this.clock = clock;
    }

    @Override
    public boolean isValid(final EintragRequest anfrage, final ConstraintValidatorContext kontext) {
      kontext.disableDefaultConstraintViolation();
      if (anfrage.art() == Eintragsart.EREIGNIS) {
        Regeln.melde(kontext, EREIGNIS_NICHT_EINREICHBAR, FELD_ART);
        return false;
      }
      boolean gueltig = Regeln.zeitpunktGueltig(anfrage.geschehenAm(), clock, kontext);
      if (anfrage.art() == Eintragsart.KOMMENTAR && leer(anfrage.text())) {
        Regeln.melde(kontext, TEXT_FEHLT, FELD_TEXT);
        gueltig = false;
      }
      if (anfrage.art() == Eintragsart.ANHANG && !dateiGueltig(anfrage.datei(), kontext)) {
        gueltig = false;
      }
      return gueltig;
    }

    private static boolean leer(final @Nullable String text) {
      return text == null || text.isBlank();
    }

    /*
     * Die drei Fragen an die Datei in der Reihenfolge, in der sie sich stellen: Ist sie ueberhaupt
     * da, ist sie klein genug (E10), und bleibt von ihrem Namen nach der Saeuberung etwas uebrig
     * (E13)? Je Anfrage geht hoechstens eine Meldung hinaus — sie stehen alle am selben Feld, und
     * drei Saetze uebereinander sagen nicht mehr als der erste.
     */
    private static boolean dateiGueltig(
        final @Nullable MultipartFile datei, final ConstraintValidatorContext kontext) {
      if (datei == null || datei.isEmpty()) {
        Regeln.melde(kontext, DATEI_FEHLT, FELD_DATEI);
        return false;
      }
      if (datei.getSize() > Uploadgrenze.MAX_BYTE) {
        Regeln.melde(kontext, Uploadgrenze.MELDUNG, FELD_DATEI);
        return false;
      }
      if (Dateiname.gesaeubert(Objects.requireNonNullElse(datei.getOriginalFilename(), ""))
          .isEmpty()) {
        Regeln.melde(kontext, DATEINAME_UNBRAUCHBAR, FELD_DATEI);
        return false;
      }
      return true;
    }
  }

  /**
   * Die Regel fuer eine Aenderung: allein der Zeitpunkt.
   *
   * <p>Die Art des Eintrags steht nicht in der Anfrage — sie steht im Bestand und ist nicht
   * aenderbar. Dass ein Text da sein muss, sagt deshalb {@code @NotBlank} am Feld und nicht diese
   * Regel.
   */
  class Aenderung implements ConstraintValidator<EintragConstraint, EintragAenderungRequest> {

    private final Clock clock;

    Aenderung(final Clock clock) {
      this.clock = clock;
    }

    @Override
    public boolean isValid(
        final EintragAenderungRequest anfrage, final ConstraintValidatorContext kontext) {
      kontext.disableDefaultConstraintViolation();
      return Regeln.zeitpunktGueltig(anfrage.geschehenAm(), clock, kontext);
    }
  }
}
