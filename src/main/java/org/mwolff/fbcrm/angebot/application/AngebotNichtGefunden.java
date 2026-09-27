package org.mwolff.fbcrm.angebot.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Zu der angefragten Kennung gibt es kein Angebot.
 *
 * <p>404 und nicht 409: Die Kennung steht im Pfad, die angefragte Ressource ist also das Angebot
 * selbst. Anders als beim Vorgang kann sie hier auch veraltet sein, weil ein Entwurf nach Kriterium
 * 7 wirklich verschwindet — „gibt es nicht" ist dann die genaue Auskunft.
 */
@ResponseStatus(code = HttpStatus.NOT_FOUND, reason = "Dieses Angebot gibt es nicht.")
public final class AngebotNichtGefunden extends RuntimeException {}
