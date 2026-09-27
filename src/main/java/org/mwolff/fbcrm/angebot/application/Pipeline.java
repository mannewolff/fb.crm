package org.mwolff.fbcrm.angebot.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Stream;

/**
 * Die Pipeline: die offenen Angebote und die beiden Zahlen darueber (Kriterien 23, 24).
 *
 * <p>Beide Summen entstehen aus den Zeilen und werden nicht getrennt gerechnet: Die gewichtete
 * Summe ist die Summe der <b>je Angebot gerundeten</b> Betraege (E20) — wer stattdessen die
 * ungerundeten Produkte addierte und erst dann rundete, bekaeme einen anderen Cent, und genau diese
 * zweite Lesart schliesst Kriterium 5 aus. Keine SQL-Aggregation: Eine zweite
 * Rundungsimplementierung koennte von der ersten abweichen.
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
        summiere(zeilen.stream().map(PipelineZeile::summe)),
        summiere(zeilen.stream().map(PipelineZeile::gewichteteSumme)));
  }

  /* Cent fuer Cent addiert; die Skala haelt die leere Pipeline bei 0,00 statt bei 0. */
  private static BigDecimal summiere(final Stream<BigDecimal> betraege) {
    return betraege.reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
  }
}
