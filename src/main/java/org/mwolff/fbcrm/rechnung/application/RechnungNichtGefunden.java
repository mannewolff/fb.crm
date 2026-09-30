package org.mwolff.fbcrm.rechnung.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Zu der angefragten Kennung gibt es keine Rechnung.
 *
 * <p>404 und nicht 409, aus demselben Grund wie bei {@code AngebotNichtGefunden}: Die Kennung steht
 * im Pfad, die angefragte Ressource ist also die Rechnung selbst.
 */
@ResponseStatus(code = HttpStatus.NOT_FOUND, reason = "Diese Rechnung gibt es nicht.")
public final class RechnungNichtGefunden extends RuntimeException {}
