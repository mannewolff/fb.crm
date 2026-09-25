package org.mwolff.fbcrm.vorgang.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Der eingetragene Zeitpunkt des Geschehens liegt weiter in der Zukunft, als die Toleranz aus E15
 * zulaesst (Kriterium 14).
 *
 * <p>400 und nicht 422: Die Anwendung kennt nur Problem Details mit {@code detail}, und die Eingabe
 * ist schlicht ungueltig. Die Ausnahme ist die Schranke hinter der Bean Validation — was die Maske
 * am Feld zeigen soll, entsteht dort als {@code fieldErrors} und nicht hier.
 */
@ResponseStatus(
    code = HttpStatus.BAD_REQUEST,
    reason = "Der Zeitpunkt darf nicht in der Zukunft liegen.")
public final class ZeitpunktInDerZukunft extends RuntimeException {}
