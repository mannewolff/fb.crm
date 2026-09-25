package org.mwolff.fbcrm.vorgang.web;

import java.util.List;
import org.mwolff.fbcrm.vorgang.application.VorgaengeUebersicht;

/**
 * Die Antwort der Vorgangsuebersicht: die gefundenen Zeilen und die Zahl aller Vorgaenge.
 *
 * <p>{@code gesamt} trennt fuer die Oberflaeche drei Lagen, die eine leere Liste sonst nicht
 * auseinanderhaelt: noch kein Vorgang, nichts gefunden, alle abgeschlossen (Kriterium 2).
 *
 * @param vorgaenge die gefundenen Zeilen in der Reihenfolge des Bestands (E16)
 * @param gesamt die Zahl aller Vorgaenge — ohne Suchtext, ohne Schalter, abgeschlossene mitgezaehlt
 */
public record VorgaengeUebersichtResponse(List<VorgangZeileResponse> vorgaenge, long gesamt) {

  /** Die Sicht der Oberflaeche auf die Uebersicht. */
  static VorgaengeUebersichtResponse of(final VorgaengeUebersicht uebersicht) {
    return new VorgaengeUebersichtResponse(
        uebersicht.zeilen().stream().map(VorgangZeileResponse::of).toList(), uebersicht.gesamt());
  }
}
