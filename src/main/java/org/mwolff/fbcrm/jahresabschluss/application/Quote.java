package org.mwolff.fbcrm.jahresabschluss.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.jspecify.annotations.Nullable;

/**
 * Die Prozentrundung des Jahresabschlusses: ein Anteil in Prozent auf eine Nachkommastelle,
 * kaufmaennisch gerundet (Plan #288, E10).
 *
 * <p><b>Sie steht hier und nicht in {@code common.Geldrechnung}</b>: Eine Quote ist kein
 * Geldbetrag. Die Geldrechnung ist die eine Regel, nach der jeder Beleg seine Betraege auf den Cent
 * rechnet; eine Prozentzahl mit einer Nachkommastelle dort unterzubringen, vermischte zwei
 * Genauigkeiten in einer Klasse, deren Zusage gerade die eine ist.
 *
 * <p>Gerechnet wird auf dem Server (E10): In der Oberflaeche entsteht keine Zahl. Ein Nenner null
 * ist kein Fehler, sondern eine Kennzahl, die es nicht gibt — die Antwort ist dann {@code null},
 * und was an ihrer Stelle erscheint, entscheidet die Ansicht (#287, Kriterium 11). Ein Zaehler null
 * bei positivem Nenner ergibt 0,0.
 */
final class Quote {

  /** Die Zahl der Nachkommastellen einer Quote. */
  private static final int STELLEN = 1;

  private static final BigDecimal HUNDERT = BigDecimal.valueOf(100);

  private Quote() {}

  /**
   * Der Anteil des Teils am Ganzen in Prozent.
   *
   * @param teil der Zaehler
   * @param ganzes der Nenner
   * @return die Prozentzahl mit Skala 1, kaufmaennisch gerundet, oder {@code null} bei Nenner null
   */
  static @Nullable BigDecimal prozent(final BigDecimal teil, final BigDecimal ganzes) {
    if (ganzes.signum() == 0) {
      return null;
    }
    return teil.multiply(HUNDERT).divide(ganzes, STELLEN, RoundingMode.HALF_UP);
  }
}
