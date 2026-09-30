package org.mwolff.fbcrm.angebot.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Zu der angefragten Kennung gibt es an diesem Angebot keine Anlage.
 *
 * <p>Drei Lagen fuehren zur selben Antwort: Die Kennung ist unbekannt, die Anlage gehoert zu einem
 * <b>anderen</b> Angebot, oder die Zeile steht, aber der Objektspeicher kennt ihren Schluessel
 * nicht. Beide Kennungen stehen im Pfad, und die angefragte Ressource ist die Anlage unter diesem
 * Angebot — die gibt es in allen drei Faellen nicht (Plan #150, E10).
 *
 * <p>404 und nicht 403, weil es keine Rechte je Anlage gibt (E13), und im dritten Fall nicht 500:
 * Eine Zeile ohne Objekt kann nach einem abgebrochenen Loeschen zurueckbleiben, und der Abrufer
 * soll dasselbe erfahren wie bei einer bereits weggeraeumten Anlage.
 */
@ResponseStatus(code = HttpStatus.NOT_FOUND, reason = "Diese Anlage gibt es nicht.")
public final class AngebotsanlageNichtGefunden extends RuntimeException {}
