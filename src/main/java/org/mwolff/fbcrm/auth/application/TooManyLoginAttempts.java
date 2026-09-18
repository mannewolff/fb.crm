package org.mwolff.fbcrm.auth.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Zu viele Fehlversuche im laufenden Zeitfenster (E9).
 *
 * <p>Gezaehlt wird je Absender-IP und je E-Mail-Adresse. Der Text nennt weder, welcher der beiden
 * Zaehler voll ist, noch ob es die Adresse gibt — sonst waere die Bremse selbst die Auskunft, die
 * K5 verhindern soll.
 */
@ResponseStatus(
    code = HttpStatus.TOO_MANY_REQUESTS,
    reason = "Zu viele Anmeldeversuche. Bitte spaeter erneut versuchen.")
public final class TooManyLoginAttempts extends RuntimeException {}
