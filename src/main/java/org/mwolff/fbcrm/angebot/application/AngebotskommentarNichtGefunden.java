package org.mwolff.fbcrm.angebot.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Zu der angefragten Kennung gibt es an diesem Angebot keinen Kommentar.
 *
 * <p>Ein Kommentar eines <b>anderen</b> Angebots fuehrt zur selben Antwort: Beide Kennungen stehen
 * im Pfad, und die angefragte Ressource ist der Kommentar unter diesem Angebot — den gibt es dann
 * nicht (Plan #141, E5). 404 und nicht 403, weil es keine Rechte je Kommentar gibt (E11) und ein
 * eigener Statuscode nur verraten wuerde, dass die Kennung anderswo vergeben ist.
 */
@ResponseStatus(code = HttpStatus.NOT_FOUND, reason = "Diesen Kommentar gibt es nicht.")
public final class AngebotskommentarNichtGefunden extends RuntimeException {}
