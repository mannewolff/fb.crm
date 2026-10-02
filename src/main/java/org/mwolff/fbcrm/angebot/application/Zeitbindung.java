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
 * <p>Ohne eigene Transaktionsgrenze: Gelesen wird in der Transaktion des Anwendungsfalls.
 */
/*
 * Genau eine Methode, aber bewusst kein @FunctionalInterface: Der Port beschreibt eine Auskunft,
 * die ein Modul gibt, und wird nie als Lambda geschrieben — dieselbe Ueberlegung wie bei
 * Positionsverwendung.
 */
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface Zeitbindung {

  /**
   * Welche der genannten Positionen erfasste Arbeitszeit tragen.
   *
   * @param angebotPositionIds die Kennungen der Positionen, nach denen gefragt wird
   * @return die Teilmenge mit erfasster Zeit; leer, wenn keine davon Zeit traegt
   */
  Set<Long> mitZeit(Set<Long> angebotPositionIds);
}
