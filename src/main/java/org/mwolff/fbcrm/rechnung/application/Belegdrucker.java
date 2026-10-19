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
 * <p>Genau eine abstrakte Methode, aber bewusst kein funktionales Interface: Ein Port beschreibt
 * eine Rolle, die ein benannter Adapter uebernimmt, und wird nie als Lambda geschrieben. Ebenso
 * gehalten wie {@code mail.domain.MailGateway}.
 */
/*
 * PMD.ImplicitFunctionalInterface: {@code @FunctionalInterface} behauptete eine Absicht, die nicht
 * besteht — siehe den letzten Absatz des Klassenkommentars.
 */
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
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
}
