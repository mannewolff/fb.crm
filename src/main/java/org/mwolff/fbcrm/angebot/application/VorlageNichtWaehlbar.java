package org.mwolff.fbcrm.angebot.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Das als Vorlage eingereichte Angebot steht fuer diesen Vorgang nicht zur Wahl (E23, Kriterium 8).
 *
 * <p>Das gilt fuer eine unbekannte Kennung ebenso wie fuer eine, die an einem anderen Vorgang
 * haengt: Die Uebernahme kopiert Texte und Positionen eines <b>fremden</b> Geschaefts, und der
 * Anwender soll beides gleich erfahren.
 *
 * <p>422 und nicht 404: Die Kennung kommt im Rumpf und nicht im Pfad — die angefragte Ressource ist
 * die Angebotsliste des Vorgangs, und die Eingabe zu ihr ist wohlgeformt, aber fachlich nicht
 * verarbeitbar.
 */
@ResponseStatus(
    code = HttpStatus.UNPROCESSABLE_ENTITY,
    reason = "Dieses Angebot steht als Vorlage nicht zur Wahl.")
public final class VorlageNichtWaehlbar extends RuntimeException {}
