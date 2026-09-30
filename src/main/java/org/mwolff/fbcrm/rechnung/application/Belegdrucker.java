package org.mwolff.fbcrm.rechnung.application;

import java.util.List;

/**
 * Port auf den Drucker der Belege; die Umsetzung liegt in {@code rechnung.infrastructure} und
 * spricht PDFBox (E10).
 *
 * <p>Der Port steht in diesem Paket und nicht in {@code domain}, weil hier seine Sprache liegt: Er
 * nimmt die {@link Druckzeile}n, die der Satz des Belegs rechnet, und ein Satz gehoert nicht in das
 * Domaenenmodell. Die Richtung bleibt dabei die von CLAUDE-java.md §6.1 verlangte — der Adapter
 * kennt die Anwendungsschicht, nicht umgekehrt: Kein Anwendungsfall nennt {@code PdfBoxDrucker}.
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
   * Schreibt die Zeilenfolge als PDF.
   *
   * @param zeilen die gesetzten Zeilen, Seite fuer Seite
   * @return das vollstaendige PDF als Bytefolge
   */
  byte[] drucke(List<Druckzeile> zeilen);
}
