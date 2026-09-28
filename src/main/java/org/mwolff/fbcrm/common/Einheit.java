package org.mwolff.fbcrm.common;

/**
 * Die Einheit, in der die Menge einer Belegposition gezaehlt wird (Kriterium 4).
 *
 * <p>Drei Werte genuegen der Arbeit eines Freiberuflers; eine freie Texteingabe waere in der
 * Auswertung nicht mehr zusammenfuehrbar.
 */
public enum Einheit {

  /** Stunden. */
  STUNDE,

  /** Personentage. */
  PERSONENTAG,

  /** Eine Pauschale ohne Zeitbezug. */
  PAUSCHAL
}
