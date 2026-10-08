package org.mwolff.fbcrm.rechnung.domain;

import java.time.Instant;

/**
 * Port auf den Bestand der Rechnungseinstellungen; die Umsetzung liegt in {@code
 * rechnung.infrastructure}.
 *
 * <p>Kein {@code findById} und kein {@link java.util.Optional}: Es gibt genau einen Satz
 * Einstellungen, und die Migration hat ihn mit seinen Vorbelegungen angelegt. Ein Zweig „noch keine
 * Einstellungen" waere ein Zustand, den es nicht gibt.
 */
public interface RechnungseinstellungenRepository {

  /** Die Einstellungen; auf einer frischen Instanz die Vorbelegungen der Migration. */
  Rechnungseinstellungen lies();

  /**
   * Schreibt die Einstellungen fort.
   *
   * @param einstellungen die neuen Einstellungen, bereits geprueft
   * @param geaendertAm Zeitpunkt der Aenderung
   */
  void speichere(Rechnungseinstellungen einstellungen, Instant geaendertAm);
}
