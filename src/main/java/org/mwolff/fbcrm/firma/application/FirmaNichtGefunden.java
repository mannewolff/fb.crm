package org.mwolff.fbcrm.firma.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Zu der angefragten Kennung gibt es keine Firma.
 *
 * <p>404 und nicht 403: Eine Firma wird nie geloescht (E3), also ist eine unbekannte Kennung
 * entweder erfunden oder veraltet — in beiden Faellen ist die Ressource schlicht nicht da. Ein
 * Unterschied zwischen „gab es nie" und „gibt es nicht mehr" waere hier ohne Wert.
 */
@ResponseStatus(code = HttpStatus.NOT_FOUND, reason = "Diese Firma gibt es nicht.")
public final class FirmaNichtGefunden extends RuntimeException {}
