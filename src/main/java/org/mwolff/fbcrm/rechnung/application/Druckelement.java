package org.mwolff.fbcrm.rechnung.application;

/**
 * Ein gesetztes Element des Belegs: was wo auf welcher Seite steht (E9, E10).
 *
 * <p>Das Ergebnis des Satzes und die einzige Sprache, die der Drucker versteht. Die Koordinaten
 * sind Punkte auf einer A4-Seite mit dem Ursprung unten links — die Zaehlrichtung von PDFBox, damit
 * der Drucker nichts umrechnen muss. Sie sind ganzzahlig: Ein halber Punkt ist auf Papier nicht zu
 * sehen, und ganze Zahlen machen den Seitenumbruch zu einer Rechnung, deren Ergebnis ein Test genau
 * benennen kann.
 *
 * <p>Ein Summentyp und keine Klassenhierarchie: Der Drucker behandelt jede Art vollstaendig, und
 * der Compiler haelt ihn dazu an, weil der Typ versiegelt ist.
 */
public sealed interface Druckelement {

  /**
   * Nummer der Seite, ab 1.
   *
   * @return die Seite, auf die dieses Element gehoert
   */
  int seite();

  /**
   * Ein Textstueck in einem Schnitt, einer Groesse, einer Farbe und einer Ausrichtung.
   *
   * <p>Ein Text ist keine Tabellenzeile: Eine Position der Tabelle besteht aus mehreren Texten mit
   * demselben {@code y} und verschiedenen {@code x}.
   *
   * @param seite Nummer der Seite, ab 1
   * @param x bei {@link Ausrichtung#LINKS} die linke, bei {@link Ausrichtung#RECHTS} die rechte
   *     Kante des Textes, gemessen vom linken Blattrand
   * @param y Abstand der Grundlinie vom unteren Blattrand in Punkten
   * @param schrift Schnitt, in dem der Text gesetzt wird
   * @param groesse Schriftgroesse in Punkten
   * @param farbe Farbe der Zeichen
   * @param ausrichtung Seite, an der der Text seinem {@code x} anliegt
   * @param text der Text; er ist bereits umgebrochen und passt in seine Spalte
   */
  record Text(
      int seite,
      int x,
      int y,
      Schrift schrift,
      int groesse,
      Farbe farbe,
      Ausrichtung ausrichtung,
      String text)
      implements Druckelement {}

  /**
   * Eine gerade Linie, etwa eine Tabellenlinie.
   *
   * @param seite Nummer der Seite, ab 1
   * @param vonX Abstand des Anfangs vom linken Blattrand in Punkten
   * @param vonY Abstand des Anfangs vom unteren Blattrand in Punkten
   * @param bisX Abstand des Endes vom linken Blattrand in Punkten
   * @param bisY Abstand des Endes vom unteren Blattrand in Punkten
   * @param staerke Strichstaerke in Punkten
   * @param farbe Farbe des Strichs
   */
  record Linie(int seite, int vonX, int vonY, int bisX, int bisY, int staerke, Farbe farbe)
      implements Druckelement {}

  /**
   * Ein gefuelltes Rechteck, etwa der farbige Randstreifen oder der Grund einer Kopfzeile.
   *
   * @param seite Nummer der Seite, ab 1
   * @param x Abstand der linken Kante vom linken Blattrand in Punkten
   * @param y Abstand der unteren Kante vom unteren Blattrand in Punkten
   * @param breite Breite in Punkten
   * @param hoehe Hoehe in Punkten
   * @param farbe Fuellfarbe
   */
  record Flaeche(int seite, int x, int y, int breite, int hoehe, Farbe farbe)
      implements Druckelement {}
}
