package org.mwolff.fbcrm.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.stream.Stream;

/**
 * Die eine Regel, nach der jeder Beleg seine Betraege rechnet (E5).
 *
 * <p>Sie steht hier und nicht im Modul eines Belegs, weil Angebot und Rechnung dieselbe Formel
 * brauchen: Menge mal Einzelpreis je Position, danach Cent fuer Cent addiert. Zwei Abschriften
 * derselben Formel driften beim ersten Nachziehen um einen Cent auseinander — und eine Rechnung,
 * die die Summe ihres Angebots nicht trifft, ist gegenueber dem Kunden nicht erklaerbar. Laege sie
 * stattdessen im Modul {@code angebot}, haenge die Positionsdefinition jedes weiteren Belegs am
 * Belegtyp, aus dem er zufaellig entsteht.
 *
 * <p><b>Kaufmaennisch, nicht mathematisch.</b> Gerundet wird mit {@link RoundingMode#HALF_UP}.
 * Kriterium 5 nennt dafuer ein Paar, das genau auf der halben Einheit liegt: 2,5 Personentage zu
 * 1.000,01 € ergeben 2.500,025 € und damit 2.500,03 €. {@link RoundingMode#HALF_EVEN} rundet zur
 * geraden Nachbarstelle und gaebe hier 2.500,02 € — statistisch unverzerrt, aber nicht die Zahl,
 * die auf dem Beleg stehen muss.
 *
 * <p><b>Je Position gerundet, dann addiert.</b> Die Summe ist die Summe der gerundeten Betraege und
 * nicht die Rundung der ungerundeten Produkte; Kriterium 5 schliesst die zweite Lesart aus. Die
 * Skala 2 haelt jede Zahl auch dann bei „0,00", wenn nichts zu runden war.
 */
public final class Geldrechnung {

  /** Die Zahl der Nachkommastellen eines Geldbetrags. */
  private static final int CENT = 2;

  /**
   * Der Betrag einer Belegposition: Menge mal Einzelpreis, kaufmaennisch auf den Cent gerundet.
   *
   * <p>Die Rechnung prueft ihre Faktoren nicht — dass kein negativer Betrag entsteht, halten die
   * Positionen mit ihren nicht negativen Feldern zu.
   *
   * @param menge Menge in der Einheit der Position
   * @param einzelpreis Netto-Preis je Einheit
   * @return das Produkt mit Skala 2
   */
  public static BigDecimal betrag(final BigDecimal menge, final BigDecimal einzelpreis) {
    return menge.multiply(einzelpreis).setScale(CENT, RoundingMode.HALF_UP);
  }

  /**
   * Die Summe schon gerundeter Betraege, Cent fuer Cent addiert.
   *
   * @param betraege die Betraege in beliebiger Reihenfolge
   * @return die Summe mit Skala 2; der leere Strom ergibt 0,00
   */
  public static BigDecimal summe(final Stream<BigDecimal> betraege) {
    return betraege.reduce(BigDecimal.ZERO, BigDecimal::add).setScale(CENT, RoundingMode.HALF_UP);
  }

  private Geldrechnung() {}
}
