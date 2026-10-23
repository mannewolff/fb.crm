package org.mwolff.fbcrm.auth.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Das letzte aktive C-Level-Konto soll stillgelegt oder herabgestuft werden (Plan #414, A6;
 * fachliche Quelle #409, Kriterium 8).
 *
 * <p>409: Die Anfrage ist richtig gebaut, sie widerspricht nur dem Stand — gaebe es einen zweiten
 * aktiven C-Level, ginge sie durch. Der Antworttext ist der eine Satz, mit dem fb.crm sagt, warum.
 */
@ResponseStatus(code = HttpStatus.CONFLICT, reason = LetzterCLevel.MELDUNG)
public final class LetzterCLevel extends RuntimeException {

  /** Der eine Satz, den die Oberflaeche zeigt. */
  public static final String MELDUNG = "Es muss mindestens ein aktiver C-Level bleiben.";
}
