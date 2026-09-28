package org.mwolff.fbcrm.auftrag.application;

import java.math.BigDecimal;
import java.util.List;
import org.mwolff.fbcrm.common.Geldrechnung;

/**
 * Der Auftragsbestand: die laufenden Auftraege und die beiden Zahlen darueber (Kriterien 12, 13).
 *
 * <p>Beide Kennzahlen entstehen aus den Zeilen und werden nicht getrennt gerechnet: „Beauftragt"
 * ist die Summe der Auftragssummen, „Noch offen" die Summe der offenen Reste — je Auftrag gerundet
 * und erst danach addiert (E5, E11). Addiert wird mit {@link Geldrechnung#summe}: keine
 * SQL-Aggregation, weil eine zweite Rundungsimplementierung von der ersten abweichen koennte.
 *
 * <p>Sie kommen aus der Antwort und nicht aus der Oberflaeche: Zwei Addierer fuer dieselbe Zahl
 * laufen beim ersten Cent auseinander, und die Ansicht zeigte dann eine Summe, die keine Abfrage
 * belegt.
 *
 * <p>Der leere Bestand traegt zwei Nullen und keine Zeile — daran erkennt die Oberflaeche „nichts
 * liegt mehr vor mir", ohne die Summen selbst zu rechnen.
 *
 * @param zeilen die laufenden Auftraege in der Reihenfolge der Ansicht
 * @param beauftragt die Summe der Auftragssummen aller Zeilen
 * @param nochOffen die Summe der offenen Reste aller Zeilen
 */
public record Auftragsbestand(
    List<AuftragsbestandZeile> zeilen, BigDecimal beauftragt, BigDecimal nochOffen) {

  /** Nimmt die Zeilen als Kopie: Der Aufrufer darf seine Liste danach weiterverwenden. */
  public Auftragsbestand {
    zeilen = List.copyOf(zeilen);
  }

  /**
   * Der Bestand zu seinen Zeilen, mit beiden Kennzahlen daraus.
   *
   * @param zeilen die laufenden Auftraege in der Reihenfolge der Ansicht
   */
  static Auftragsbestand of(final List<AuftragsbestandZeile> zeilen) {
    return new Auftragsbestand(
        zeilen,
        Geldrechnung.summe(zeilen.stream().map(AuftragsbestandZeile::auftragssumme)),
        Geldrechnung.summe(zeilen.stream().map(AuftragsbestandZeile::offenerRest)));
  }
}
