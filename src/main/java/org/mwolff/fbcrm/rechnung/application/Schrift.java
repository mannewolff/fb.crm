package org.mwolff.fbcrm.rechnung.application;

/**
 * Der Schnitt, in dem ein Text des Belegs gesetzt wird.
 *
 * <p>Zwei Werte genuegen dem schlichten Beleg aus E10: der Lauftext und die Hervorhebung von Titel,
 * Spaltenkopf und Summe. Welche Schriftfamilie daraus wird, entscheidet der Drucker — das Layout
 * kennt keine Schriftdatei.
 */
public enum Schrift {

  /** Lauftext. */
  NORMAL,

  /** Hervorgehoben: Titel, Spaltenkopf, Summe. */
  FETT
}
