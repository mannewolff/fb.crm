package org.mwolff.fbcrm.auth.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Die Einrichtung ist abgewiesen — ohne zu sagen, woran.
 *
 * <p>Ein falscher Einmal-Schluessel, eine bereits eingerichtete Instanz und der unterlegene Aufruf
 * eines Wettrennens werfen <b>dieselbe</b> Exception ohne eigene Meldung; den Text liefert {@code
 * reason} der Annotation, fuer jeden Fall denselben. Jede Unterscheidung waere eine Auskunft
 * darueber, welcher der drei Riegel griff — und damit darueber, ob der geratene Schluessel richtig
 * war.
 *
 * <p>403 statt 401: Es geht nicht um eine fehlende Sitzung, sondern um einen Vorgang, der so nicht
 * stattfinden darf. Die Oberflaeche soll niemanden zur Anmeldung schicken, sondern sagen, dass die
 * Einrichtung nicht offen steht.
 */
@ResponseStatus(code = HttpStatus.FORBIDDEN, reason = "Die Einrichtung ist nicht moeglich.")
public final class SetupRefused extends RuntimeException {

  /** Abgewiesen ohne technische Ursache: falscher Schluessel oder Konto existiert bereits. */
  public SetupRefused() {
    // Ohne Meldung und ohne Ursache — den Antworttext liefert die Annotation.
    super();
  }

  /**
   * Abgewiesen, weil die Datenbank das zweite Konto verhindert hat.
   *
   * <p>Die Ursache bleibt <b>im Haus</b>: {@code super(null, cause)} setzt bewusst keine Meldung,
   * denn {@code GlobalExceptionHandler} gibt {@code getMessage()} nach aussen. Mit {@code
   * super(cause)} stuende der Text der Datenbank — Tabelle, Index, mitunter Werte — im
   * Antwortrumpf.
   *
   * @param cause die Verletzung von {@code account_single_admin}
   */
  public SetupRefused(final Throwable cause) {
    super(null, cause);
  }
}
