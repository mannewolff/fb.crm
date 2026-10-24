package org.mwolff.fbcrm.angebot.application;

import java.util.Set;

/**
 * Welche Positionen eines Angebots erfasste Arbeitszeit tragen (Issue #228, Kriterium 8 von #207).
 *
 * <p>Der Port haengt die Auskunft <b>hier</b> auf und nicht dort, wo sie entsteht: Die Umsetzung
 * liegt im Modul {@code arbeitszeit}, das das Angebot ohnehin kennt — die Zeit wird auf eine
 * Angebotsposition gebucht. Wuerde das Angebot umgekehrt die Zeiterfassung fragen, zeigten beide
 * Module aufeinander, ein Paketzyklus, den {@code ArchitectureTest} verbietet (CLAUDE-java.md §6.5;
 * Plan #218, E6). So bleibt die Richtung eine: {@code arbeitszeit} → {@code angebot}. Dasselbe
 * Muster wie {@link Positionsverwendung} gegenueber {@code rechnung}.
 *
 * <p>Gefragt wird mit einer Menge von Kennungen und nicht mit der des Angebots: Der Aufrufer prueft
 * eine <em>Einreichung</em>, und darin stehen moeglicherweise andere Positionen als am
 * gespeicherten Angebot.
 *
 * <p>Ohne eigene Transaktionsgrenze: Gelesen und geschrieben wird in der Transaktion des
 * Anwendungsfalls.
 *
 * <p><b>Eine geloeschte Zeit bindet nichts</b> (Plan #415, E17). Ein geloeschter Zeiteintrag bleibt
 * gekennzeichnet an seiner Zeile stehen und zeigt weiter ohne {@code ON DELETE} auf seine Position.
 * {@link #mitZeit(Set)} zaehlt ihn nicht mit, und bevor das Angebot eine Position entfernt, raeumt
 * {@link #entferneGeloeschte(Set)} solche Eintraege hart ab — sonst scheiterte das Speichern am
 * Fremdschluessel.
 */
public interface Zeitbindung {

  /**
   * Welche der genannten Positionen erfasste Arbeitszeit tragen.
   *
   * @param angebotPositionIds die Kennungen der Positionen, nach denen gefragt wird
   * @return die Teilmenge mit erfasster Zeit; leer, wenn keine davon Zeit traegt
   */
  Set<Long> mitZeit(Set<Long> angebotPositionIds);

  /**
   * Entfernt die geloeschten Zeiteintraege auf den genannten Positionen hart (Plan #415, E17).
   *
   * <p>Ein nicht geloeschter Eintrag bleibt stehen und haelt seine Position weiter fest: Deren
   * Entfernen scheitert dann wie bisher am Fremdschluessel.
   *
   * @param angebotPositionIds die Kennungen der Positionen, die das Angebot entfernt
   */
  void entferneGeloeschte(Set<Long> angebotPositionIds);
}
