package org.mwolff.fbcrm.arbeitszeit.application;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/**
 * Die Monatsliste der Ansicht „Arbeitszeit" (Issue #193, Kriterium 5; Plan #194, A20).
 *
 * <p><b>Der Monat steht mit darin</b>, auch wenn der Aufrufer ihn genannt haben kann: Wer ohne
 * Parameter fragt, bekommt den laufenden (E4), und ohne diese Angabe wuesste die Ansicht nicht,
 * welcher das ist — sie muesste die Geschaeftszone ein zweites Mal nachrechnen.
 *
 * <p><b>{@code stunden} ist die Summe der zwei Teile</b> (Issue #230, Kriterium 10 von #207), und
 * das gilt ohne Rest: Jede der drei Zahlen entsteht aus addierten Minuten, die im
 * Viertelstundenraster liegen ({@code Zeiteintrag.imRaster}), also ist jede Minutensumme ein
 * Vielfaches von 15 und ihre Umrechnung in Stunden exakt. Die Gesamtsumme bleibt darum die
 * vorhandene Rechnung und wird nicht aus den Teilen addiert.
 *
 * <p>Die Tage teilen nicht auf: {@link Arbeitstag} traegt weiter nur seine eine Summe.
 *
 * @param monat der Monat, den diese Liste zeigt
 * @param tage die Tage mit Eintraegen, aufsteigend; ein Tag ohne Eintrag fehlt
 * @param stunden die Summe des Monats in Stunden mit zwei Nachkommastellen
 * @param stundenFuerKunden der Teil davon, der auf Angebote an Kunden gebucht ist
 * @param stundenIntern der Teil davon, der auf interne Angebote gebucht ist
 */
public record Arbeitsmonat(
    YearMonth monat,
    List<Arbeitstag> tage,
    BigDecimal stunden,
    BigDecimal stundenFuerKunden,
    BigDecimal stundenIntern) {

  /** Nimmt die Tage als Kopie: Der Aufrufer darf seine Liste danach weiterverwenden. */
  public Arbeitsmonat {
    tage = List.copyOf(tage);
  }
}
