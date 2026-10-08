package org.mwolff.fbcrm.rechnung.web;

import java.util.regex.Pattern;

/**
 * Der Name, unter dem das Dokument einer Rechnung beim Empfaenger landet (#160, Kriterium 24; Plan
 * #169, E11).
 *
 * <p>{@code Rechnung-<Nummer>.pdf} — der Name soll im Ordner des Freiberuflers und beim Kunden
 * sagen, welche Rechnung das ist.
 *
 * <p><b>Warum die Nummer dabei gesaeubert wird.</b> Welche Form eine Rechnungsnummer hat, bestimmt
 * das Nummernmuster der Einstellungen, und das erlaubt freien Text zwischen den Platzhaltern — auch
 * {@code /}, etwa fuer „2026/3". Ein {@code /} im Dateinamen ist bei jedem Empfaenger ein
 * Pfadtrenner. Darum wird hier jedes Zeichen ausserhalb von Buchstaben, Ziffern, {@code -}, {@code
 * _} und {@code .} zu {@code -}.
 *
 * <p>Die Saeuberung ersetzt {@link org.mwolff.fbcrm.common.web.Anlagekopf} nicht und wird von ihm
 * nicht ersetzt: Dort geht es um die HTTP-Kopfzeile, die den Namen transportiert, hier um den Namen
 * selbst. Beides greift hintereinander.
 */
final class Rechnungsdateiname {

  /** Alles, was in einem Dateinamen nichts zu suchen hat — Pfadtrenner eingeschlossen. */
  private static final Pattern UNERWUENSCHT = Pattern.compile("[^A-Za-z0-9._-]");

  private Rechnungsdateiname() {}

  /**
   * Der Dateiname zu einer Rechnungsnummer.
   *
   * @param nummer die Rechnungsnummer, wie die gestellte Rechnung sie traegt
   */
  static String fuer(final String nummer) {
    return "Rechnung-" + UNERWUENSCHT.matcher(nummer).replaceAll("-") + ".pdf";
  }
}
