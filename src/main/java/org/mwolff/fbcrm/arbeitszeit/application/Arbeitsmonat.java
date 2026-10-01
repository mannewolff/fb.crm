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
 * @param monat der Monat, den diese Liste zeigt
 * @param tage die Tage mit Eintraegen, aufsteigend; ein Tag ohne Eintrag fehlt
 * @param stunden die Summe des Monats in Stunden mit zwei Nachkommastellen
 */
public record Arbeitsmonat(YearMonth monat, List<Arbeitstag> tage, BigDecimal stunden) {

  /** Nimmt die Tage als Kopie: Der Aufrufer darf seine Liste danach weiterverwenden. */
  public Arbeitsmonat {
    tage = List.copyOf(tage);
  }
}
