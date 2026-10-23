package org.mwolff.fbcrm.rechnung.application;

import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.rechnung.domain.Briefbogen;
import org.mwolff.fbcrm.rechnung.domain.Briefpapierblatt;
import org.mwolff.fbcrm.rechnung.domain.Randmasse;

/**
 * Der Stand des Briefbogens, wie die Maske ihn sieht (fachliche Quelle #361, Kriterien 1 und 15;
 * Plan #363, E1).
 *
 * <p>Getrennt von {@link Briefbogen}, weil dessen {@link Briefpapierblatt} den Schluessel im
 * Objektspeicher traegt: Der ist ein Detail der Ablage und geht nicht nach aussen, wie bei der
 * nachgetragenen Rechnung. Ohne Briefpapier sind Dateiname, Groesse und Seitenzahl leer.
 *
 * @param briefpapier ob ein Briefpapier hinterlegt ist
 * @param dateiname Name der hochgeladenen Datei
 * @param groesse Groesse in Byte
 * @param seiten Seitenzahl, 1 oder 2
 * @param absenderzeile ob die kleine Absenderzeile ueber der Anschrift eingedruckt wird
 * @param steuerBankZeile ob die Zeile mit Steuer- und Bankangaben eingedruckt wird
 * @param raender die sechs Raender
 * @param version der Stand der ganzen Zeile, wie ihn {@code If-Match} vorlegt (Plan #415, E11)
 */
public record Briefbogenstand(
    boolean briefpapier,
    @Nullable String dateiname,
    @Nullable Long groesse,
    @Nullable Integer seiten,
    boolean absenderzeile,
    boolean steuerBankZeile,
    Randmasse raender,
    long version) {

  /**
   * Der Stand eines Briefbogens, ohne den Schluessel seines Briefpapiers.
   *
   * @param bogen der Briefbogen aus dem Bestand
   * @param version der Stand der Zeile
   * @return was die Maske davon sieht
   */
  static Briefbogenstand von(final Briefbogen bogen, final long version) {
    final Briefpapierblatt blatt = bogen.blatt();
    if (blatt == null) {
      return new Briefbogenstand(
          false,
          null,
          null,
          null,
          bogen.absenderzeile(),
          bogen.steuerBankZeile(),
          bogen.raender(),
          version);
    }
    return new Briefbogenstand(
        true,
        blatt.dateiname(),
        blatt.groesse(),
        blatt.seiten(),
        bogen.absenderzeile(),
        bogen.steuerBankZeile(),
        bogen.raender(),
        version);
  }
}
