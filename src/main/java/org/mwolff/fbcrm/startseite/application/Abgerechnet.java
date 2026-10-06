package org.mwolff.fbcrm.startseite.application;

import java.util.List;
import org.mwolff.fbcrm.rechnung.application.Monatsabrechnung;

/**
 * Die Kennzahl „Abgerechnet" fuer den gewaehlten Zeitraum (#206, Kriterium 7; #273, Kriterien 4 und
 * 7).
 *
 * <p><b>Die Monatszeilen stehen in der Kennzahl</b> und nicht daneben (Plan #274, E11): Sie sind
 * die Herkunft genau dieser Summe. Bei Monatswahl ist die Liste leer und nicht {@code null} — die
 * Ansicht unterscheidet den leeren Jahresfall vom Monatsfall an der Art des Zeitraums und nicht an
 * der Liste.
 *
 * @param summe Netto, Brutto und Anzahl der im Zeitraum gestellten Rechnungen
 * @param monate bei Jahreswahl je Monat mit mindestens einer gestellten Rechnung seine Abrechnung,
 *     aeltester zuerst; bei Monatswahl leer
 */
public record Abgerechnet(Monatsabrechnung summe, List<Abrechnungsmonat> monate) {

  /** Nimmt die Monate als Kopie: Der Aufrufer darf seine Liste danach weiterverwenden. */
  public Abgerechnet {
    monate = List.copyOf(monate);
  }
}
