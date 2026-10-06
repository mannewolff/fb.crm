package org.mwolff.fbcrm.rechnung.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;
import org.mwolff.fbcrm.common.Geldrechnung;

/**
 * Was in einem Zeitraum gestellt wurde: Netto, Brutto und die Zahl der Rechnungen (#206, Kriterium
 * 7) — fuer einen Monat oder, als {@link #summe(Stream) Summe} seiner Monate, fuer ein Jahr (Plan
 * #274, E5).
 *
 * <p>Netto ist die fuehrende Angabe, Brutto steht daneben (#206, Antwort 5). Beide sind die Summe
 * der <b>je Rechnung</b> gerundeten Betraege und nicht aus der Monatssumme gerechnet: Der Betrag,
 * der auf dem Beleg steht, ist der je Rechnung — und nur so trifft die Kennzahl den Cent, den die
 * Rechnungsliste zeigt (Kriterium 9).
 *
 * <p>Entwuerfe stehen nicht darin; gezaehlt wird allein, was gestellt ist (Antwort 6).
 *
 * @param netto die Summe der Netto-Betraege der im Zeitraum gestellten Rechnungen
 * @param brutto die Summe ihrer Brutto-Betraege, jeder mit dem Satz seiner Rechnung
 * @param anzahl die Zahl dieser Rechnungen
 */
public record Monatsabrechnung(BigDecimal netto, BigDecimal brutto, int anzahl) {

  /**
   * Die Summe mehrerer Abrechnungen, etwa der Monate eines Jahres (Plan #274, E5).
   *
   * <p>Addiert werden die schon je Rechnung gerundeten Betraege; das ist in jeder Gruppierung
   * dieselbe Zahl, darum trifft die Jahressumme den Cent der Rechnungsliste. Ueber keine Abrechnung
   * summiert, entstehen die Betraege 0,00 und die Anzahl 0 — so, wie {@link Geldrechnung#summe} es
   * zusagt, und ohne einen eigenen Leerwert daneben.
   *
   * @param abrechnungen die zu summierenden Abrechnungen, auch keine
   * @return Netto, Brutto und Anzahl ueber alle zusammen
   */
  public static Monatsabrechnung summe(final Stream<Monatsabrechnung> abrechnungen) {
    final List<Monatsabrechnung> alle = abrechnungen.toList();
    return new Monatsabrechnung(
        Geldrechnung.summe(alle.stream().map(Monatsabrechnung::netto)),
        Geldrechnung.summe(alle.stream().map(Monatsabrechnung::brutto)),
        alle.stream().mapToInt(Monatsabrechnung::anzahl).sum());
  }
}
