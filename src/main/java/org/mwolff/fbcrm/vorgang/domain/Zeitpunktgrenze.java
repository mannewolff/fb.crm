package org.mwolff.fbcrm.vorgang.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * Wann ein eingetragener Zeitpunkt als „in der Zukunft" gilt (Kriterium 14, E15).
 *
 * <p>Eine reine Funktion in der Domaene, nach dem Muster von {@link Dateiname}: Die Regel wird an
 * drei Stellen gebraucht — von der Bean Validation der Anfrage, die daraus eine Meldung am Feld
 * macht, und von beiden Schreibwegen als Schranke dahinter. Stuende sie dreimal da, koennte eine
 * Kopie eine andere Toleranz fahren als die anderen.
 *
 * <p>Die Toleranz von einer Minute gibt es, weil die Vorbelegung „jetzt" aus der Uhr des Browsers
 * kommt: Eine um eine halbe Minute vorgehende Uhr wiese sonst die unveraenderte Vorbelegung ab. Ein
 * wirklich zukuenftiger Zeitpunkt — naechste Stunde, morgen — bleibt abgewiesen.
 */
public final class Zeitpunktgrenze {

  /** Die Nachsicht gegenueber einer vorgehenden Browser-Uhr (E15). */
  public static final Duration TOLERANZ = Duration.ofSeconds(60);

  private Zeitpunktgrenze() {}

  /**
   * Ob der Zeitpunkt weiter als die Toleranz hinter der Gegenwart liegt.
   *
   * @param geschehenAm der eingetragene Zeitpunkt des Geschehens
   * @param jetzt die Gegenwart aus der Uhr der Anwendung
   */
  public static boolean inDerZukunft(final Instant geschehenAm, final Instant jetzt) {
    return geschehenAm.isAfter(jetzt.plus(TOLERANZ));
  }
}
