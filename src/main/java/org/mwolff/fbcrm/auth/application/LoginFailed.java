package org.mwolff.fbcrm.auth.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Die Anmeldung mit dem Code ist gescheitert — ohne zu sagen, woran.
 *
 * <p>Ein falscher, ein abgelaufener und ein verbrauchter Code und eine unbekannte Adresse werfen
 * <b>dieselbe</b> Exception ohne eigene Meldung; den Text liefert {@code reason} der Annotation,
 * fuer jeden Fall denselben (Kriterium 3). Jede Unterscheidung — anderer Status, anderer Text,
 * anderes Feld — waere eine Auskunft darueber, welche Adressen auf dieser Instanz ein Konto haben.
 *
 * <p>Der Text sagt, was zu tun ist (Kriterium 4): Fuer jeden der Faelle hilft derselbe Schritt, ein
 * neuer Code.
 */
@ResponseStatus(code = HttpStatus.UNAUTHORIZED, reason = LoginFailed.MELDUNG)
public final class LoginFailed extends RuntimeException {

  /** Der Text der Abweisung, fuer jeden Fall derselbe. */
  public static final String MELDUNG =
      "Der Code ist falsch, abgelaufen oder schon benutzt. Bitte einen neuen Code anfordern.";
}
