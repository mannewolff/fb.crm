package org.mwolff.fbcrm.rechnung.application;

/**
 * Die Seite, an der ein Text seinem {@code x} anliegt.
 *
 * <p>Zwei Werte genuegen dem Beleg: Beschriftungen laufen nach rechts, Betraege stehen an einer
 * gemeinsamen rechten Kante, damit ihre Stellen untereinander liegen. Wie breit ein Text dabei ist,
 * weiss nur der Drucker — er hat die Schrift.
 */
public enum Ausrichtung {

  /** {@code x} ist die linke Kante des Textes. */
  LINKS,

  /** {@code x} ist die rechte Kante des Textes. */
  RECHTS
}
