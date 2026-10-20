package org.mwolff.fbcrm.common;

import java.time.Duration;

/**
 * Eine Bremse hat gegriffen; die Anfrage darf erst nach einer Wartezeit wiederkommen.
 *
 * <p>Abgebildet wird der Vertrag in {@code common.web.GlobalExceptionHandler}: Status 429, eine
 * Meldung, die die Wartezeit in ganzen Minuten nennt, und der Kopf {@code Retry-After} (E14 des
 * Plans #395). Die Meldung entsteht dort allein aus der Wartezeit — welche Bremse gegriffen hat und
 * fuer welche Adresse, traegt dieser Vertrag gar nicht erst und kann es darum auch nicht verraten
 * (Kriterium 5 der fachlichen Quelle #392).
 *
 * <p><b>Warum der Vertrag hier steht und nicht im Fachmodul.</b> Aus demselben Grund wie {@link
 * Feldfehler}: Kennte die Abbildung die Ausnahme des Fachmoduls, zeigte {@code common} auf das
 * Fachmodul und das Fachmodul auf {@code common} — ein Paketzyklus ({@code ArchitectureTest},
 * {@code modules_thenFreeOfCycles}).
 */
public abstract class ZuVieleVersuche extends RuntimeException {

  private final Duration wartezeit;

  /**
   * Eine gebremste Lage mit ihrer Wartezeit.
   *
   * @param restwartezeit wie lange es dauert, bis die Bremse wieder zulaesst, auf ganze Minuten
   *     aufgerundet
   */
  protected ZuVieleVersuche(final Duration restwartezeit) {
    super();
    this.wartezeit = restwartezeit;
  }

  /** Wie lange es dauert, bis die Bremse wieder zulaesst, in ganzen Minuten. */
  public Duration restwartezeit() {
    return wartezeit;
  }
}
