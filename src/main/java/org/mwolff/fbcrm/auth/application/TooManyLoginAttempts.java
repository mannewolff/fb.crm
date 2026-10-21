package org.mwolff.fbcrm.auth.application;

import java.time.Duration;
import org.mwolff.fbcrm.common.ZuVieleVersuche;

/**
 * Zu viele Versuche im laufenden Zeitfenster (E9, E14 des Plans #395).
 *
 * <p>Die Ausnahme traegt nur die Restwartezeit. Status, Meldung und {@code Retry-After} setzt
 * {@code GlobalExceptionHandler} ueber den Vertrag {@link ZuVieleVersuche}; ein fester Text in
 * {@code @ResponseStatus} naennte eine Zeit, die im allgemeinen Fall falsch ist. Welche Zaehlart
 * gegriffen hat und fuer welche Adresse, steht nicht darin — sonst waere die Bremse selbst die
 * Auskunft, die K5 verhindern soll.
 */
public final class TooManyLoginAttempts extends ZuVieleVersuche {

  /**
   * Eine abgewiesene Anmeldung.
   *
   * @param restwartezeit wie lange es dauert, bis die Bremse wieder zulaesst, auf ganze Minuten
   *     aufgerundet
   */
  public TooManyLoginAttempts(final Duration restwartezeit) {
    super(restwartezeit);
  }
}
