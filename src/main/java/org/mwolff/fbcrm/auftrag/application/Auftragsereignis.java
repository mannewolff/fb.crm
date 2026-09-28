package org.mwolff.fbcrm.auftrag.application;

import org.mwolff.fbcrm.auftrag.domain.Auftragsstatus;

/**
 * Die Texte, die der Auftrag in die Historie des Vorgangs schreibt (Kriterium 8, R2).
 *
 * <p><b>An einer Stelle, und die liegt hier</b> (Fund 14 der Plan-Pruefung): Das Wort je Status
 * gehoert zur Meldung und nicht zum Wert — {@link Auftragsstatus} traegt keines, und das Wort der
 * Oberflaeche steht spaeter in {@code frontend/src/lib/auftragsstatus.ts}. Waere es am
 * Aufzaehlungstyp oeffentlich, waere es eine Einladung, es in der Antwort mitzuschicken, und dann
 * stuende dasselbe Wort im Backend und im Frontend.
 *
 * <p>Paketprivat: Ereignisse entstehen ausschliesslich in den Anwendungsfaellen dieses Pakets.
 *
 * <p>Der Status steht in Anfuehrungszeichen, weil „in Arbeit" zwei Woerter hat und der Satz sonst
 * an der falschen Stelle auseinanderfiele.
 */
final class Auftragsereignis {

  private Auftragsereignis() {}

  /** „Auftrag AU-2026-001 angelegt" (Kriterium 8). */
  static String angelegt(final String nummer) {
    return "Auftrag %s angelegt".formatted(nummer);
  }

  /** „Auftrag AU-2026-001 geloescht" (Kriterium 15). */
  static String geloescht(final String nummer) {
    return "Auftrag %s geloescht".formatted(nummer);
  }

  /** „Auftrag AU-2026-001 auf „in Arbeit" gesetzt" (Kriterium 7). */
  static String statusGesetzt(final String nummer, final Auftragsstatus status) {
    final String wort =
        switch (status) {
          case OFFEN -> "offen";
          case IN_ARBEIT -> "in Arbeit";
          case ABGESCHLOSSEN -> "abgeschlossen";
        };
    return "Auftrag %s auf „%s\" gesetzt".formatted(nummer, wort);
  }
}
