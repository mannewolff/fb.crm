package org.mwolff.fbcrm.firma.web;

import java.util.List;
import org.mwolff.fbcrm.firma.application.FirmenUebersicht;

/**
 * Die Antwort der Firmenuebersicht: die gefundenen Zeilen und die Zahl aller Firmen.
 *
 * <p>{@code gesamt} trennt fuer die Oberflaeche drei Lagen, die eine leere Liste sonst nicht
 * auseinanderhaelt: noch keine Firma, nichts gefunden, alle stillgelegt (E5).
 *
 * @param firmen die gefundenen Zeilen in der Reihenfolge des Bestands
 * @param gesamt die Zahl aller Firmen — ohne Suchtext, ohne Schalter, stillgelegte mitgezaehlt
 */
public record FirmenUebersichtResponse(List<FirmaZeileResponse> firmen, long gesamt) {

  /** Die Sicht der Oberflaeche auf die Uebersicht. */
  static FirmenUebersichtResponse of(final FirmenUebersicht uebersicht) {
    return new FirmenUebersichtResponse(
        uebersicht.zeilen().stream().map(FirmaZeileResponse::of).toList(), uebersicht.gesamt());
  }
}
