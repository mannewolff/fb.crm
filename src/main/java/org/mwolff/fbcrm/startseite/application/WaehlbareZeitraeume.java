package org.mwolff.fbcrm.startseite.application;

import java.time.Year;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Welche Jahre und Monate auf der Startseite zur Wahl stehen (#273, Kriterien 1 und 2; Plan #274,
 * E8 und E9).
 *
 * <p><b>Jahre:</b> das laufende immer, das letzte nur mit mindestens einer gestellten Rechnung
 * darin, aeltere nie — neuestes zuerst (Kriterium 1).
 *
 * <p><b>Monate:</b> jeder Monat des laufenden und des letzten Jahres mit gestellter Rechnung oder
 * erfasster Arbeitszeit, dazu immer der laufende — neuester zuerst und jeder einmal (Kriterium 2).
 * Auch ein Monat nach dem laufenden steht darin, wenn eine Rechnung in ihm datiert ist (E9):
 * Kriterium 2 nennt keine obere Grenze, und jede Zeile der Jahresliste soll ein waehlbarer Monat
 * sein.
 *
 * <p><b>Die Asymmetrie ist gewollt</b> (E8): Ueber das Jahr entscheiden allein Rechnungen, ueber
 * den Monat auch Arbeitszeit. Ein Monat des letzten Jahres mit Arbeitszeit, aber ohne Rechnung
 * steht damit zur Wahl, sein Jahr daneben nicht. So hat es der PO nach dem Fachplan-Review
 * entschieden (#273, Antwort 1); geglaettet waere es eine Abweichung vom fachlichen Anlass.
 *
 * @param jahre die waehlbaren Jahre, neuestes zuerst
 * @param monate die waehlbaren Monate, neuester zuerst
 */
public record WaehlbareZeitraeume(List<Year> jahre, List<YearMonth> monate) {

  /** Nimmt die Listen als Kopie: Der Aufrufer darf seine Listen danach weiterverwenden. */
  public WaehlbareZeitraeume {
    jahre = List.copyOf(jahre);
    monate = List.copyOf(monate);
  }

  /**
   * Die waehlbaren Zeitraeume aus dem Bestand.
   *
   * @param laufend der laufende Monat in der Geschaeftszone
   * @param mitRechnung die Monate mit mindestens einer gestellten Rechnung, nach Rechnungsdatum;
   *     Doppelungen und Monate ausserhalb der beiden Jahre stoeren nicht
   * @param mitArbeitszeit die Monate mit erfasster Arbeitszeit, ebenso
   * @return die waehlbaren Jahre und Monate
   */
  public static WaehlbareZeitraeume herleiten(
      final YearMonth laufend,
      final Collection<YearMonth> mitRechnung,
      final Collection<YearMonth> mitArbeitszeit) {
    final Year diesesJahr = Year.from(laufend);
    final Year letztesJahr = diesesJahr.minusYears(1);
    final List<Year> jahre = new ArrayList<>(List.of(diesesJahr));
    if (mitRechnung.stream().anyMatch(monat -> letztesJahr.equals(Year.from(monat)))) {
      jahre.add(letztesJahr);
    }
    final NavigableSet<YearMonth> monate = new TreeSet<>(Comparator.reverseOrder());
    monate.add(laufend);
    Stream.concat(mitRechnung.stream(), mitArbeitszeit.stream())
        .filter(
            monat -> Year.from(monat).equals(diesesJahr) || Year.from(monat).equals(letztesJahr))
        .forEach(monate::add);
    return new WaehlbareZeitraeume(jahre, List.copyOf(monate));
  }

  /**
   * Ob ein Zeitraum zur Wahl steht — fuer den Rueckfall des Anwendungsfalls auf das laufende Jahr
   * (Issue #283).
   *
   * @param zeitraum der gewuenschte Zeitraum
   * @return {@code true}, wenn er unter den waehlbaren Jahren oder Monaten steht
   */
  public boolean enthaelt(final Zeitraum zeitraum) {
    return switch (zeitraum) {
      case Zeitraum.Monat monat -> monate.contains(monat.monat());
      case Zeitraum.Jahr jahr -> jahre.contains(jahr.jahr());
    };
  }
}
