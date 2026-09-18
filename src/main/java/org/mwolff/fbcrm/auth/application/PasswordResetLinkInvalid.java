package org.mwolff.fbcrm.auth.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Der Reset-Link gilt nicht (mehr) — ohne zu sagen, woran es liegt.
 *
 * <p>Ein Token, den es nie gab, ein abgelaufener und ein bereits benutzter werfen <b>dieselbe</b>
 * Exception ohne eigene Meldung; den Text liefert {@code reason} der Annotation, fuer jeden Fall
 * denselben. Jede Unterscheidung waere eine Auskunft darueber, ob ein geratener Link je existiert
 * hat — und damit eine Einladung, weiter zu raten.
 *
 * <p>410 statt 404: Der Link hat existiert, er ist verbraucht. Die Oberflaeche unterscheidet daran
 * den Fall „Meldung statt Formular" (K7) von einem Tippfehler in der Adresse.
 */
@ResponseStatus(code = HttpStatus.GONE, reason = "Der Link gilt nicht mehr.")
public final class PasswordResetLinkInvalid extends RuntimeException {}
