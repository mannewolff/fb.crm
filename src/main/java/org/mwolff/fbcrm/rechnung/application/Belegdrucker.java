package org.mwolff.fbcrm.rechnung.application;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Port auf den Drucker der Belege; die Umsetzung liegt in {@code rechnung.infrastructure} und
 * spricht PDFBox (E10).
 *
 * <p>Der Port steht in diesem Paket und nicht in {@code domain}, weil hier seine Sprache liegt: Er
 * nimmt die {@link Druckelement}e, die der Satz des Belegs rechnet, und ein Satz gehoert nicht in
 * das Domaenenmodell. Die Richtung bleibt dabei die von CLAUDE-java.md §6.1 verlangte — der Adapter
 * kennt die Anwendungsschicht, nicht umgekehrt: Kein Anwendungsfall nennt {@code PdfBoxDrucker}.
 *
 * <p>Unter die Elemente legt der Drucker auf Wunsch einen Untergrund, das hinterlegte Briefpapier
 * (Plan #363, E3): Dessen erstes Blatt liegt unter Seite 1, ein zweites unter jeder Folgeseite;
 * ohne zweites Blatt bleiben die Folgeseiten ohne Unterlage. Der Untergrund ist die unterste
 * Schicht, unter Flaeche, Linie, Text und schraegem Text.
 *
 * <p>Eine Rechnung kennt der Drucker nicht. Was auf dem Beleg steht, hat der Satz entschieden; hier
 * wird nur noch geschrieben.
 *
 * <p>Zwei Ausgaben: {@link #drucke} schreibt ein gewoehnliches PDF, wie es der Probedruck braucht;
 * {@link #druckeERechnung} schreibt dieselben Seiten als E-Rechnung, PDF/A-3b mit eingebetteter XML
 * (Plan #461, E4, E19). Welche Ausgabe ein Beleg bekommt, entscheidet der Anwendungsfall.
 */
public interface Belegdrucker {

  /**
   * Schreibt die Elementfolge als PDF.
   *
   * @param elemente die gesetzten Elemente, Seite fuer Seite; die Reihenfolge innerhalb einer Art
   *     bleibt erhalten, die Arten zeichnet der Drucker in der Tiefe Flaeche, Linie, Text,
   *     schraeger Text
   * @param briefpapier das hinterlegte Briefpapier als PDF mit einem oder zwei Blaettern, das unter
   *     den Elementen liegt; {@code null} druckt ohne Untergrund
   * @return das vollstaendige PDF als Bytefolge
   */
  byte[] drucke(List<Druckelement> elemente, byte @Nullable [] briefpapier);

  /**
   * Schreibt die Elementfolge als E-Rechnung: dieselben Seiten wie {@link #drucke}, dazu der
   * OutputIntent sRGB, die Metadaten nach PDF/A-3b und Factur-X und die XML als eingebettete Datei
   * {@code factur-x.xml}.
   *
   * <p>Volles PDF/A entsteht ohne Briefpapier und mit einem Briefpapier, das seine Schriften
   * einbettet und in RGB vorliegt. Ein anderes Briefpapier liegt unveraendert darunter; die XML ist
   * in jedem Fall dieselbe (E5).
   *
   * @param elemente die gesetzten Elemente, wie bei {@link #drucke}
   * @param briefpapier das hinterlegte Briefpapier, oder {@code null}, wie bei {@link #drucke}
   * @param cii die XML der E-Rechnung in UTF-8, aus der auch die Rechnungsnummer fuer den Titel des
   *     Dokuments stammt
   * @return das vollstaendige PDF als Bytefolge
   */
  byte[] druckeERechnung(List<Druckelement> elemente, byte @Nullable [] briefpapier, byte[] cii);
}
