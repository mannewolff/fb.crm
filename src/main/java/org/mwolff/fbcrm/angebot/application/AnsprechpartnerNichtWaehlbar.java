package org.mwolff.fbcrm.angebot.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Der genannte Ansprechpartner steht fuer dieses Angebot nicht zur Wahl (Issue #126).
 *
 * <p>Das gilt fuer eine unbekannte Kennung ebenso wie fuer einen Ansprechpartner einer anderen
 * Firma oder einen stillgelegten: Der Anwender soll alle drei Faelle gleich erfahren, und keiner
 * davon verraet etwas ueber fremde Firmen.
 *
 * <p>422 und nicht 404: Die Kennung kommt im Rumpf und nicht im Pfad — die angefragte Ressource ist
 * die Angebotsliste der Firma, und die Eingabe zu ihr ist wohlgeformt, aber fachlich nicht
 * verarbeitbar.
 */
@ResponseStatus(
    code = HttpStatus.UNPROCESSABLE_ENTITY,
    reason = "Dieser Ansprechpartner steht für das Angebot nicht zur Wahl.")
public final class AnsprechpartnerNichtWaehlbar extends RuntimeException {}
