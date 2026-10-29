package org.mwolff.fbcrm.rechnung.application;

/**
 * Port auf den Schreiber der E-Rechnung; die Umsetzung liegt in {@code rechnung.infrastructure} und
 * schreibt CII nach EN 16931 (Plan #461, E1, E19).
 *
 * <p>Der Port steht in diesem Paket, weil hier seine Sprache liegt: Er nimmt die {@link
 * ERechnungsdaten}, die {@code Rechnungsbeleg} aus der festgeschriebenen Rechnung bildet. Kein
 * Anwendungsfall nennt den Adapter beim Namen (CLAUDE-java.md §6.1).
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
public interface ERechnungsschreiber {

  /**
   * Schreibt die E-Rechnung als XML in der Syntax CII D16B, Profil EN 16931.
   *
   * @param daten was in der E-Rechnung steht
   * @return die XML in UTF-8 als Bytefolge, wie sie spaeter als {@code factur-x.xml} im Dokument
   *     steckt
   */
  byte[] cii(ERechnungsdaten daten);
}
