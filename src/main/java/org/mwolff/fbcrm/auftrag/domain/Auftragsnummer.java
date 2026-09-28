package org.mwolff.fbcrm.auftrag.domain;

/**
 * Die Schreibweise der Auftragsnummer: {@code AU-<jahr>-<laufende Nummer>} (Kriterium 3).
 *
 * <p>Eine eigene kleine Klasse neben {@code angebot.domain.Angebotsnummer} und keine gemeinsame
 * (E6): Die beiden Kreise gehoeren verschiedenen Modulen, und ein geteilter Formatierer haette
 * keinen Eigentuemer. Die laufende Nummer wird auf drei Stellen aufgefuellt; drei Stellen sind das
 * Mindeste und keine Obergrenze — der tausendste Auftrag eines Jahres traegt vier, statt wieder bei
 * 000 zu beginnen.
 *
 * <p>Der Jahresteil kommt vom Aufrufer und wird hier nicht aus einer Uhr geholt — die Domaene kennt
 * keine (CLAUDE-java.md §6.2). Gemeint ist das Kalenderjahr des <b>Anlegens</b> in {@code
 * common.Geschaeftszone} und nicht das frei setzbare Auftragsdatum.
 */
public final class Auftragsnummer {

  private Auftragsnummer() {}

  /**
   * Die Nummer als Text.
   *
   * @param jahr Kalenderjahr des Anlegens
   * @param laufend laufende Nummer innerhalb des Jahres, ab 1
   */
  public static String formatiere(final int jahr, final long laufend) {
    return "AU-%d-%03d".formatted(jahr, laufend);
  }
}
