package org.mwolff.fbcrm.auftrag.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Am abgeschlossenen Vorgang entsteht kein Auftrag (Kriterium 11).
 *
 * <p><b>Eine eigene Ausnahme und nicht die des Angebot-Moduls</b> (Plan E14): Deren Meldung spricht
 * vom Angebot, und dieselbe Klasse fuer beide zu nehmen waere eine Abhaengigkeit zwischen zwei
 * Anwendungsschichten fuer einen Meldungstext. Gleicher Statuscode, eigener Satz.
 *
 * <p>Die Sperre reicht genau so weit, wie Kriterium 11 sie zieht: Ein bestehender Auftrag laesst
 * sich am abgeschlossenen Vorgang weiter pflegen, und jeder Leseweg ohnehin.
 *
 * <p>409 und nicht 403: Die Anfrage ist in Ordnung, nur der Zustand des Vorgangs passt nicht — und
 * er kann sich hinter dem Ruecken des Anwenders geaendert haben.
 */
@ResponseStatus(
    code = HttpStatus.CONFLICT,
    reason = "Dieser Vorgang ist abgeschlossen; ein Auftrag entsteht daran nicht mehr.")
public final class VorgangAbgeschlossen extends RuntimeException {}
