package org.mwolff.fbcrm.angebot.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * An eine stillgelegte Firma geht kein neues Angebot (Issue #126).
 *
 * <p>409 und nicht 404: Die Firma gibt es, nur ihr Zustand passt nicht — und er kann sich hinter
 * dem Ruecken des Anwenders geaendert haben, wie bei {@link AngebotNichtAenderbar}. Bestehende
 * Angebote einer stillgelegten Firma bleiben lesbar und pflegbar; gesperrt ist allein das Anlegen.
 */
@ResponseStatus(
    code = HttpStatus.CONFLICT,
    reason = "Diese Firma ist stillgelegt; ein neues Angebot entsteht daran nicht.")
public final class FirmaStillgelegt extends RuntimeException {}
