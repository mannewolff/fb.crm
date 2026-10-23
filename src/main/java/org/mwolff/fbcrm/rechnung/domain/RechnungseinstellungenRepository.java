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

  /** Der Stand der Zeile: Version, Urheber und Zeitpunkt der letzten Aenderung (Plan #415, E1). */
  Zeilenstand stand();

  /**
   * Schreibt die Einstellungen fort.
   *
   * <p>Die Zeile wird nur geschrieben, wenn sie noch den Stand {@code version} traegt; sonst
   * scheitert das Schreiben an der Sperre des Bestands. So bleibt auch der Spalt zwischen dem
   * Vergleich im Anwendungsfall und dem Schreiben geschuetzt (E1).
   *
   * @param einstellungen die neuen Einstellungen, bereits geprueft
   * @param geaendertAm Zeitpunkt der Aenderung
   * @param version der Stand, auf dem die Aenderung beruht
   * @param konto Kennung des handelnden Kontos
   * @return der neue Stand der Zeile
   */
  Zeilenstand speichere(
      Rechnungseinstellungen einstellungen, Instant geaendertAm, long version, long konto);
}
