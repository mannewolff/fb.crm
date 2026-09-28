package org.mwolff.fbcrm.auftrag.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Zu der angefragten Kennung gibt es keinen Auftrag.
 *
 * <p>404 und nicht 409: Die Kennung steht im Pfad, die angefragte Ressource ist also der Auftrag
 * selbst. Sie kann auch veraltet sein — ein Auftrag laesst sich loeschen (Kriterium 15), und „gibt
 * es nicht" ist dann die genaue Auskunft.
 *
 * <p>Eine eigene Ausnahme und nicht die des Angebots: {@code
 * angebot.application.AngebotNichtGefunden} spricht vom Angebot, und ihre Meldung stuende auf dem
 * falschen Gegenstand.
 */
@ResponseStatus(code = HttpStatus.NOT_FOUND, reason = "Diesen Auftrag gibt es nicht.")
public final class AuftragNichtGefunden extends RuntimeException {}
