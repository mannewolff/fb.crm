package org.mwolff.fbcrm.rechnung.application;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.rechnung.domain.Rechnung;

/**
 * Welcher Steuersatz fuer eine Rechnung gilt (#160, Kriterium 14).
 *
 * <p>Zwei Quellen, eine Regel: Eine gestellte Rechnung traegt ihren eigenen, beim Stellen
 * festgeschriebenen Satz und behaelt ihn, auch wenn die Einstellungen sich danach aendern; ein
 * Entwurf hat noch keinen und rechnet mit dem der aktuellen Einstellungen.
 *
 * <p>Die Regel steht an einer Stelle, weil drei Anwendungsfaelle sie brauchen — die Liste aller
 * Rechnungen, die einzelne Rechnung und der Abrechnungsstand eines Angebots. Drei Abschriften
 * liefen beim ersten Nachziehen auseinander, und dieselbe Rechnung nennte dann in Liste und Maske
 * verschiedene Betraege.
 */
final class GeltenderSteuersatz {

  private GeltenderSteuersatz() {}

  /**
   * Der Satz, mit dem diese Rechnung rechnet.
   *
   * @param rechnung die Rechnung
   * @param aktuellerSatz der Satz der aktuellen Einstellungen
   */
  static BigDecimal fuer(final Rechnung rechnung, final BigDecimal aktuellerSatz) {
    final @Nullable BigDecimal eigener = rechnung.steuersatz();
    return eigener == null ? aktuellerSatz : eigener;
  }
}
