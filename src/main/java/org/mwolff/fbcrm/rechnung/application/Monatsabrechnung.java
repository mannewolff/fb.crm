package org.mwolff.fbcrm.rechnung.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;
import org.mwolff.fbcrm.common.Geldrechnung;
import org.mwolff.fbcrm.rechnung.domain.Rechnungszustand;

/**
 * Was in einem Zeitraum gestellt wurde: Netto, Brutto und die Zahl der Rechnungen (#206, Kriterium
 * 7) — fuer einen Monat oder, als {@link #summe(Stream) Summe} seiner Monate, fuer ein Jahr (Plan
 * #274, E5). Dazu, wie viel davon noch offen ist (Issue #284).
 *
 * <p>Netto ist die fuehrende Angabe, Brutto steht daneben (#206, Antwort 5). Beide sind die Summe
 * der <b>je Rechnung</b> gerundeten Betraege und nicht aus der Monatssumme gerechnet: Der Betrag,
 * der auf dem Beleg steht, ist der je Rechnung — und nur so trifft die Kennzahl den Cent, den die
 * Rechnungsliste zeigt (Kriterium 9).
 *
 * <p>Entwuerfe stehen nicht darin; gezaehlt wird allein, was gestellt ist (Antwort 6).
 *
 * <p><b>„Offen" heisst Zustand {@link Rechnungszustand#GESTELLT}</b> — gestellt und noch nicht
 * bezahlt ({@link Rechnungszustand#istOffen()}). Eine bezahlte und eine abgeschriebene Rechnung
 * zaehlen in {@link #netto()} weiter mit und in {@link #offenNetto()} nicht: Jenes ist der Umsatz
 * des Monats nach Rechnungsdatum, dieses das, worauf noch Geld fehlt. Das Offene steht darum als
 * Teil derselben Abrechnung hier und nicht in einer zweiten Karte daneben — es wird gruppiert und
 * summiert wie sie.
 *
 * @param netto die Summe der Netto-Betraege der im Zeitraum gestellten Rechnungen
 * @param brutto die Summe ihrer Brutto-Betraege, jeder mit dem Satz seiner Rechnung
 * @param anzahl die Zahl dieser Rechnungen
 * @param offenNetto die Summe der Netto-Betraege derjenigen unter ihnen, die noch offen sind
 * @param offenAnzahl die Zahl dieser offenen Rechnungen
 */
public record Monatsabrechnung(
    BigDecimal netto, BigDecimal brutto, int anzahl, BigDecimal offenNetto, int offenAnzahl) {

  /**
   * Die Summe mehrerer Abrechnungen, etwa der Monate eines Jahres (Plan #274, E5).
   *
   * <p>Addiert werden die schon je Rechnung gerundeten Betraege; das ist in jeder Gruppierung
   * dieselbe Zahl, darum trifft die Jahressumme den Cent der Rechnungsliste. Ueber keine Abrechnung
   * summiert, entstehen die Betraege 0,00 und die Anzahlen 0 — so, wie {@link Geldrechnung#summe}
   * es zusagt, und ohne einen eigenen Leerwert daneben.
   *
   * @param abrechnungen die zu summierenden Abrechnungen, auch keine
   * @return Netto, Brutto, Anzahl und das Offene ueber alle zusammen
   */
  public static Monatsabrechnung summe(final Stream<Monatsabrechnung> abrechnungen) {
    final List<Monatsabrechnung> alle = abrechnungen.toList();
    return new Monatsabrechnung(
        Geldrechnung.summe(alle.stream().map(Monatsabrechnung::netto)),
        Geldrechnung.summe(alle.stream().map(Monatsabrechnung::brutto)),
        alle.stream().mapToInt(Monatsabrechnung::anzahl).sum(),
        Geldrechnung.summe(alle.stream().map(Monatsabrechnung::offenNetto)),
        alle.stream().mapToInt(Monatsabrechnung::offenAnzahl).sum());
  }
}
