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

  /**
   * Schreibt die Angaben fort.
   *
   * @param angaben die neuen Angaben, bereits normalisiert
   * @param geaendertAm Zeitpunkt der Aenderung
   */
  void speichere(EigeneAngaben angaben, Instant geaendertAm);
}
