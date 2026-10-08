package org.mwolff.fbcrm.rechnung.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Zu der angefragten Kennung gibt es keine nachgetragene Rechnung (Plan #259).
 *
 * <p>404 aus demselben Grund wie bei {@link RechnungNichtGefunden}: Die Kennung steht im Pfad, die
 * angefragte Ressource ist also die Rechnung selbst. Eine eigene Ausnahme, weil beide Arten von
 * Rechnungen eigene Kennungen fuehren — dieselbe Zahl kann hier fehlen und dort vorkommen.
 */
@ResponseStatus(code = HttpStatus.NOT_FOUND, reason = "Diese nachgetragene Rechnung gibt es nicht.")
public final class NachtragNichtGefunden extends RuntimeException {}
