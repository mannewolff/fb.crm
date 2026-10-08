package org.mwolff.fbcrm.auth.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Die Sitzung verweist auf ein Konto, das es nicht mehr gibt.
 *
 * <p>401 statt 404: Aus Sicht des Aufrufers ist seine Sitzung nichts mehr wert, und die Oberflaeche
 * soll ihn zur Anmeldung fuehren — nicht eine fehlende Ressource melden.
 */
@ResponseStatus(code = HttpStatus.UNAUTHORIZED, reason = "Die Sitzung gilt nicht mehr.")
public final class UnknownAccount extends RuntimeException {}
