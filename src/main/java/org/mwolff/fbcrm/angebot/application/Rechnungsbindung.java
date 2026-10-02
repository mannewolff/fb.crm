package org.mwolff.fbcrm.angebot.application;

/**
 * Ob aus einem Angebot schon eine Rechnung entstanden ist (Issue #227, E5).
 *
 * <p>Die Auskunft, an der die Art des Angebots haengt: Setzen und Entfernen des Kennzeichens {@code
 * intern} sind frei, solange keine Rechnung besteht (Kriterium 8 von #207). Gezaehlt werden
 * Entwuerfe wie gestellte Rechnungen — ein Entwurf auf ein Angebot ohne Preise waere derselbe
 * Widerspruch wie eine gestellte Rechnung darauf.
 *
 * <p><b>Warum nicht {@link Positionsverwendung} erweitert wird.</b> Der Name jenes Ports sagt
 * „welche Positionen", nicht „ob eine Rechnung", und ein leerer Kennungssatz ist nicht dasselbe wie
 * „keine Rechnung": Eine Rechnung, deren Positionen alle die Menge 0 trugen, bindet keine einzige
 * Kennung und ist trotzdem eine Rechnung. Aus {@code verwendeteKennungen(...).isEmpty()} zu
 * schliessen, es gaebe keine, waere darum falsch.
 *
 * <p>Der Port haengt die Auskunft <b>hier</b> auf und nicht dort, wo sie entsteht — dieselbe
 * Ueberlegung wie bei {@link Positionsverwendung}: Die Umsetzung liegt im Modul {@code rechnung},
 * das das Angebot ohnehin kennt, und die Richtung bleibt {@code rechnung} → {@code angebot} (Plan
 * #169, E1, E12).
 *
 * <p>Ohne eigene Transaktionsgrenze: Gelesen wird in der Transaktion des Anwendungsfalls.
 */
/*
 * Genau eine Methode, aber bewusst kein @FunctionalInterface: Der Port beschreibt eine Auskunft,
 * die ein Modul gibt, und wird nie als Lambda geschrieben — dieselbe Ueberlegung wie bei
 * Positionsverwendung.
 */
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface Rechnungsbindung {

  /**
   * Ob zu diesem Angebot mindestens eine Rechnung besteht — Entwurf oder gestellt.
   *
   * @param angebotId Kennung des Angebots
   * @return {@code true}, sobald eine Rechnung darauf existiert
   */
  boolean rechnungVorhanden(long angebotId);
}
