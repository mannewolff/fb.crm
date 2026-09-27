package org.mwolff.fbcrm.angebot.domain;

/**
 * Die Schreibweise der Angebotsnummer: {@code A-<jahr>-<laufende Nummer>} (Kriterium 11).
 *
 * <p>Die laufende Nummer wird auf drei Stellen aufgefuellt. Drei Stellen sind das Mindeste und
 * keine Obergrenze: Das tausendste Angebot eines Jahres traegt vier Stellen, statt wieder bei 000
 * zu beginnen.
 *
 * <p>Der Jahresteil kommt vom Aufrufer und wird hier nicht aus einer Uhr geholt — die Domaene kennt
 * keine (CLAUDE-java.md §6.2). Welches Jahr gilt, entscheidet {@code common.Geschaeftszone}.
 */
public final class Angebotsnummer {

  private Angebotsnummer() {}

  /**
   * Die Nummer als Text.
   *
   * @param jahr Kalenderjahr des Versendens
   * @param laufend laufende Nummer innerhalb des Jahres, ab 1
   */
  public static String formatiere(final int jahr, final long laufend) {
    return "A-%d-%03d".formatted(jahr, laufend);
  }
}
