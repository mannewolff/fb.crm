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
   * Der Stand der Zeile: Version, Urheber und Zeitpunkt der letzten Aenderung (Plan #415, E1, E11).
   */
  Zeilenstand stand();

  /**
   * Schreibt den Briefbogen fort.
   *
   * <p>Die Zeile wird nur geschrieben, wenn sie noch den Stand {@code version} traegt; sonst
   * scheitert das Schreiben an der Sperre des Bestands (E1).
   *
   * @param briefbogen der neue Stand, bereits geprueft
   * @param geaendertAm Zeitpunkt der Aenderung
   * @param version der Stand, auf dem die Aenderung beruht
   * @param konto Kennung des handelnden Kontos
   * @return der neue Stand der Zeile
   */
  Zeilenstand speichere(Briefbogen briefbogen, Instant geaendertAm, long version, long konto);
}
