package org.mwolff.fbcrm.rechnung.domain;

import java.time.Instant;

/**
 * Port auf den Bestand des Briefbogens; die Umsetzung liegt in {@code rechnung.infrastructure}.
 *
 * <p>Kein {@code findById} und kein {@link java.util.Optional}: Es gibt genau einen Briefbogen, und
 * die Migration hat ihn mit seinen Vorgaben angelegt.
 */
public interface BriefbogenRepository {

  /** Der Briefbogen; auf einer frischen Instanz die Vorgaben der Migration, ohne Briefpapier. */
  Briefbogen lies();

  /**
   * Schreibt den Briefbogen fort.
   *
   * @param briefbogen der neue Stand, bereits geprueft
   * @param geaendertAm Zeitpunkt der Aenderung
   */
  void speichere(Briefbogen briefbogen, Instant geaendertAm);
}
