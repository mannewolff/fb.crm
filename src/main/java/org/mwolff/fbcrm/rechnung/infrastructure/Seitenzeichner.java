package org.mwolff.fbcrm.rechnung.infrastructure;

import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.mwolff.fbcrm.rechnung.application.Ausrichtung;
import org.mwolff.fbcrm.rechnung.application.Druckelement;
import org.mwolff.fbcrm.rechnung.application.Farbe;
import org.mwolff.fbcrm.rechnung.application.Schrift;

/**
 * Zeichnet die Druckelemente einer Seite in deren Inhaltsstrom.
 *
 * <p>Der Umgang mit der Bibliothek, Element fuer Element: Farbe setzen, Rechteck fuellen, Strich
 * ziehen, Text ausgeben. Entschieden wird hier nichts ueber den Beleg — was wo steht, hat der Satz
 * gerechnet. Die einzige eigene Rechnung ist die Breite eines rechtsbuendigen Textes: Sie steht nur
 * der Schrift zur Verfuegung, und die hat allein der Drucker.
 */
final class Seitenzeichner {

  /** Der groesste Anteil einer {@link Farbe}; ein PDF rechnet dagegen in Bruchteilen von 1. */
  private static final float HOECHSTER_ANTEIL = 255f;

  /** Ein Schriftmass gilt je 1000 Einheiten der Schriftgroesse. */
  private static final float MASSEINHEITEN_JE_GROESSE = 1000f;

  private final PDPageContentStream inhalt;

  private final PDFont normal;

  private final PDFont fett;

  Seitenzeichner(final PDPageContentStream inhalt, final PDFont normal, final PDFont fett) {
    this.inhalt = inhalt;
    this.normal = normal;
    this.fett = fett;
  }

  private static float anteil(final int wert) {
    return wert / HOECHSTER_ANTEIL;
  }

  private static float breite(final PDFont schrift, final String text, final int groesse)
      throws IOException {
    return schrift.getStringWidth(text) / MASSEINHEITEN_JE_GROESSE * groesse;
  }

  /**
   * Zeichnet ein Element.
   *
   * @param element das gesetzte Element; die Tiefe bestimmt der Aufrufer durch die Reihenfolge
   * @throws IOException wenn der Inhaltsstrom nicht zu schreiben ist
   */
  void zeichne(final Druckelement element) throws IOException {
    switch (element) {
      case Druckelement.Flaeche flaeche -> zeichneFlaeche(flaeche);
      case Druckelement.Linie linie -> zeichneLinie(linie);
      case Druckelement.Text text -> schreibeText(text);
    }
  }

  private void zeichneFlaeche(final Druckelement.Flaeche flaeche) throws IOException {
    setzeFuellfarbe(flaeche.farbe());
    inhalt.addRect(
        (float) flaeche.x(),
        (float) flaeche.y(),
        (float) flaeche.breite(),
        (float) flaeche.hoehe());
    inhalt.fill();
  }

  private void zeichneLinie(final Druckelement.Linie linie) throws IOException {
    inhalt.setStrokingColor(
        anteil(linie.farbe().rot()), anteil(linie.farbe().gruen()), anteil(linie.farbe().blau()));
    inhalt.setLineWidth((float) linie.staerke());
    inhalt.moveTo((float) linie.vonX(), (float) linie.vonY());
    inhalt.lineTo((float) linie.bisX(), (float) linie.bisY());
    inhalt.stroke();
  }

  private void schreibeText(final Druckelement.Text text) throws IOException {
    final PDFont schrift = text.schrift() == Schrift.FETT ? fett : normal;
    final String darstellbar = WinAnsiText.darstellbar(text.text());
    setzeFuellfarbe(text.farbe());
    inhalt.beginText();
    inhalt.setFont(schrift, (float) text.groesse());
    inhalt.newLineAtOffset(text.x() - versatz(text, schrift, darstellbar), (float) text.y());
    inhalt.showText(darstellbar);
    inhalt.endText();
  }

  /**
   * Um wieviel die linke Kante links von {@code x} liegt: beim linksbuendigen Text um nichts, denn
   * sein {@code x} ist schon die Kante; beim rechtsbuendigen um die gemessene Breite.
   */
  private static float versatz(
      final Druckelement.Text text, final PDFont schrift, final String darstellbar)
      throws IOException {
    if (text.ausrichtung() == Ausrichtung.LINKS) {
      return 0f;
    }
    return breite(schrift, darstellbar, text.groesse());
  }

  private void setzeFuellfarbe(final Farbe farbe) throws IOException {
    inhalt.setNonStrokingColor(anteil(farbe.rot()), anteil(farbe.gruen()), anteil(farbe.blau()));
  }
}
