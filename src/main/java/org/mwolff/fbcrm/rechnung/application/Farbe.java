package org.mwolff.fbcrm.rechnung.application;

/**
 * Eine Farbe des Belegs als Anteile von Rot, Gruen und Blau, je 0 bis 255.
 *
 * <p>Ein eigener kleiner Wert und kein Typ aus einer Bibliothek: Der Satz des Belegs rechnet ohne
 * PDFBox und ohne AWT, und die Umrechnung in die Zahlen, die ein PDF braucht, ist Sache des
 * Druckers.
 *
 * <p>Die Grenzen prueft der Wert selbst. Ein Anteil ausserhalb von 0 bis 255 ist keine Farbe; er
 * faellt beim Rechnen auf und nicht erst als falscher Ton auf dem Blatt.
 *
 * @param rot Rotanteil, 0 bis 255
 * @param gruen Gruenanteil, 0 bis 255
 * @param blau Blauanteil, 0 bis 255
 */
public record Farbe(int rot, int gruen, int blau) {

  /** Der groesste zulaessige Anteil; der kleinste ist 0. */
  private static final int HOECHSTER_ANTEIL = 255;

  /** Erzeugt die Farbe und weist Anteile ausserhalb von 0 bis 255 ab. */
  public Farbe {
    pruefe(rot, "Rot");
    pruefe(gruen, "Gruen");
    pruefe(blau, "Blau");
  }

  private static void pruefe(final int anteil, final String name) {
    if (anteil < 0 || anteil > HOECHSTER_ANTEIL) {
      throw new IllegalArgumentException(
          "Der Farbanteil %s liegt ausserhalb von 0 bis %d: %d"
              .formatted(name, HOECHSTER_ANTEIL, anteil));
    }
  }
}
