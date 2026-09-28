package org.mwolff.fbcrm.angebot.application;

import java.math.BigDecimal;
import java.util.List;
import org.mwolff.fbcrm.common.Geldrechnung;

/**
 * Die Pipeline: die offenen Angebote und die beiden Zahlen darueber (Kriterien 23, 24).
 *
 * <p>Beide Summen entstehen aus den Zeilen und werden nicht getrennt gerechnet: Die gewichtete
 * Summe ist die Summe der <b>je Angebot gerundeten</b> Betraege (E20) — wer stattdessen die
 * ungerundeten Produkte addierte und erst dann rundete, bekaeme einen anderen Cent, und genau diese
 * zweite Lesart schliesst Kriterium 5 aus. Addiert wird mit {@link Geldrechnung#summe}: Keine
 * SQL-Aggregation, weil eine zweite Rundungsimplementierung von der ersten abweichen koennte.
 *
 * <p>Die leere Pipeline traegt zwei Nullen und keine Zeile — daran erkennt die Oberflaeche „keine
 * offene Chance", ohne die Summen selbst zu rechnen.
 *
 * @param zeilen die offenen Angebote in der Reihenfolge der Ansicht
 * @param summe die ungewichtete Netto-Summe aller Zeilen
 * @param gewichteteSumme die Summe der gewichteten Betraege aller Zeilen
 */
public record Pipeline(List<PipelineZeile> zeilen, BigDecimal summe, BigDecimal gewichteteSumme) {

  /** Nimmt die Zeilen als Kopie: Der Aufrufer darf seine Liste danach weiterverwenden. */
  public Pipeline {
    zeilen = List.copyOf(zeilen);
  }

  /**
   * Die Pipeline zu ihren Zeilen, mit beiden Summen daraus.
   *
   * @param zeilen die offenen Angebote in der Reihenfolge der Ansicht
   */
  static Pipeline of(final List<PipelineZeile> zeilen) {
    return new Pipeline(
        zeilen,
        Geldrechnung.summe(zeilen.stream().map(PipelineZeile::summe)),
        Geldrechnung.summe(zeilen.stream().map(PipelineZeile::gewichteteSumme)));
  }
}
