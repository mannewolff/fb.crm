package org.mwolff.fbcrm.auth.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Die Anmeldung ist gescheitert — ohne zu sagen, woran.
 *
 * <p>Eine unbekannte Adresse und ein falsches Passwort werfen <b>dieselbe</b> Exception ohne eigene
 * Meldung; den Text liefert {@code reason} der Annotation, fuer jeden Fall denselben (K5). Jede
 * Unterscheidung — anderer Status, anderer Text, anderes Feld — waere eine Auskunft darueber,
 * welche Adressen auf dieser Instanz ein Konto haben.
 */
@ResponseStatus(code = HttpStatus.UNAUTHORIZED, reason = "E-Mail-Adresse oder Passwort ist falsch.")
public final class LoginFailed extends RuntimeException {}
