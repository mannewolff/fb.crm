package org.mwolff.fbcrm.rechnung.application;

import java.util.List;
import org.mwolff.fbcrm.rechnung.domain.Rechnung;

/**
 * Die Rechnung mit den Zeilen ihrer Maske (Plan #169, E5).
 *
 * <p>Zwei Sichten auf dasselbe: Die Rechnung traegt, was sie abrechnet — Datum, Leistungszeitraum
 * und ihre Positionen mit Text, Menge und dem festgehaltenen Einzelpreis. Die Zeilen tragen den
 * Stand am Angebot, den die Maske daneben zeigt: angeboten, abgerechnet, offen.
 *
 * @param rechnung die Rechnung selbst
 * @param zeilen je Position des Angebots eine Zeile, in der Reihenfolge des Angebots
 */
public record Rechnungsansicht(Rechnung rechnung, List<Abrechnungszeile> zeilen) {

  /** Nimmt die Zeilen als Kopie: Der Aufrufer darf seine Liste danach weiterverwenden. */
  public Rechnungsansicht {
    zeilen = List.copyOf(zeilen);
  }
}
