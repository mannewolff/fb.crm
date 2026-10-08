package org.mwolff.fbcrm.firma.application;

import java.util.List;

/**
 * Das Ergebnis der Firmenuebersicht: die gefundenen Zeilen und die Zahl aller Firmen.
 *
 * <p>{@code gesamt} steht hier, weil eine leere Liste drei verschiedene Lagen bedeuten kann: Es
 * gibt noch keine Firma, der Suchtext trifft keine, oder es sind alle stillgelegt. Ohne die Zahl
 * muesste die Oberflaeche ein zweites Mal fragen, um zu wissen, welche Meldung sie zeigen soll
 * (E5).
 *
 * @param zeilen die gefundenen Firmen in der Reihenfolge des Bestands
 * @param gesamt die Zahl aller Firmen — ohne Suchtext, ohne Schalter, stillgelegte mitgezaehlt
 */
public record FirmenUebersicht(List<FirmaZeile> zeilen, long gesamt) {}
