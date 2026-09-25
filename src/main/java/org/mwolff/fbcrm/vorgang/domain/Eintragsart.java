package org.mwolff.fbcrm.vorgang.domain;

/**
 * Die Art eines Eintrags in der Historie eines Vorgangs.
 *
 * <p>Beide Arten liegen in derselben Tabelle und werden als eine Folge gelesen (E6); welche Angaben
 * eine Art verlangt, halten die Fabriken in {@link Eintrag} und die Checks der Migration an je
 * einer Stelle fest.
 */
public enum Eintragsart {

  /** Freier Text ohne Datei. */
  KOMMENTAR,

  /** Eine abgelegte Datei, wahlweise mit beschreibendem Text. */
  ANHANG
}
