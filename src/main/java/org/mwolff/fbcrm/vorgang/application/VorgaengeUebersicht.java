package org.mwolff.fbcrm.vorgang.application;

import java.util.List;

/**
 * Das Ergebnis der Vorgangsuebersicht: die gefundenen Zeilen und die Zahl aller Vorgaenge.
 *
 * <p>{@code gesamt} steht hier, weil eine leere Liste drei verschiedene Lagen bedeuten kann: Es
 * gibt noch keinen Vorgang, der Suchtext trifft keinen, oder sie sind alle abgeschlossen. Ohne die
 * Zahl muesste die Oberflaeche ein zweites Mal fragen, um zu wissen, welche Meldung sie zeigen soll
 * — dieselbe Dreiteilung wie bei den Firmen (Kriterium 2).
 *
 * @param zeilen die gefundenen Vorgaenge in der Reihenfolge des Bestands (E16)
 * @param gesamt die Zahl aller Vorgaenge — ohne Suchtext, ohne Schalter, abgeschlossene mitgezaehlt
 */
public record VorgaengeUebersicht(List<VorgangZeile> zeilen, long gesamt) {}
