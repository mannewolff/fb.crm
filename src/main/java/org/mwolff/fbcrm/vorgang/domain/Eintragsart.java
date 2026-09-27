package org.mwolff.fbcrm.vorgang.domain;

/**
 * Die Art eines Eintrags in der Historie eines Vorgangs.
 *
 * <p>Alle Arten liegen in derselben Tabelle und werden als eine Folge gelesen (E6); welche Angaben
 * eine Art verlangt, halten die Fabriken in {@link Eintrag} und die Checks der Migration an je
 * einer Stelle fest.
 */
public enum Eintragsart {

  /** Freier Text ohne Datei. */
  KOMMENTAR,

  /** Eine abgelegte Datei, wahlweise mit beschreibendem Text. */
  ANHANG,

  /**
   * Ein Zustandswechsel eines Dokuments, von der Anwendung vermerkt (Kriterium 19).
   *
   * <p>Diese Art kommt nie von aussen: Die Bean Validation der Eintragsanfrage weist sie am Feld
   * {@code art} ab, und geschrieben wird sie allein durch {@code EreignisVermerkenUseCase}. Aendern
   * laesst sie sich nicht — sonst waere die Historie kein Nachweis mehr.
   */
  EREIGNIS
}
