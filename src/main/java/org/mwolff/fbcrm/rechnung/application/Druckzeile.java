package org.mwolff.fbcrm.rechnung.application;

/**
 * Eine gesetzte Zeile des Belegs: was wo in welcher Schrift steht (E10).
 *
 * <p>Das Ergebnis des Satzes und die einzige Sprache, die der Drucker versteht. Die Koordinaten
 * sind Punkte auf einer A4-Seite mit dem Ursprung unten links — die Zaehlrichtung von PDFBox, damit
 * der Drucker nichts umrechnen muss. Sie sind ganzzahlig: Ein halber Punkt ist auf Papier nicht zu
 * sehen, und ganze Zahlen machen den Seitenumbruch zu einer Rechnung, deren Ergebnis ein Test genau
 * benennen kann.
 *
 * <p>Eine Zeile ist keine Tabellenzeile: Eine Position der Tabelle besteht aus mehreren Druckzeilen
 * mit demselben {@code y} und verschiedenen {@code x}.
 *
 * @param seite Nummer der Seite, ab 1
 * @param x Abstand vom linken Blattrand in Punkten
 * @param y Abstand der Grundlinie vom unteren Blattrand in Punkten
 * @param schrift Schnitt, in dem der Text gesetzt wird
 * @param groesse Schriftgroesse in Punkten
 * @param text der Text; er ist bereits umgebrochen und passt in seine Spalte
 */
public record Druckzeile(int seite, int x, int y, Schrift schrift, int groesse, String text) {}
