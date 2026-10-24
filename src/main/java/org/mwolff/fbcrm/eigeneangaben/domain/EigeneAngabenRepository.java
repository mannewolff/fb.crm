package org.mwolff.fbcrm.eigeneangaben.domain;

import java.time.Instant;

/**
 * Port auf den Bestand der eigenen Angaben; die Umsetzung liegt in {@code
 * eigeneangaben.infrastructure}.
 *
 * <p>Kein {@code findById} und kein {@link java.util.Optional}: Es gibt genau einen Satz Angaben,
 * und die Migration hat ihn angelegt. Ein Zweig „noch keine Angaben" waere ein Zustand, den es
 * nicht gibt.
 */
public interface EigeneAngabenRepository {

  /** Die eigenen Angaben; auf einer frischen Instanz mit lauter leeren Feldern. */
  EigeneAngaben lies();

  /** Der Stand der Zeile: Version, Urheber und Zeitpunkt der letzten Aenderung (Plan #415, E1). */
  EigeneAngabenStand stand();

  /**
   * Schreibt die Angaben fort.
   *
   * <p>Die Zeile wird nur geschrieben, wenn sie noch den Stand {@code version} traegt; sonst
   * scheitert das Schreiben an der Sperre des Bestands. So bleibt auch der Spalt zwischen dem
   * Vergleich im Anwendungsfall und dem Schreiben geschuetzt (E1).
   *
   * @param angaben die neuen Angaben, bereits normalisiert
   * @param geaendertAm Zeitpunkt der Aenderung
   * @param version der Stand, auf dem die Aenderung beruht
   * @param konto Kennung des handelnden Kontos
   * @return der neue Stand der Zeile
   */
  EigeneAngabenStand speichere(
      EigeneAngaben angaben, Instant geaendertAm, long version, long konto);
}
