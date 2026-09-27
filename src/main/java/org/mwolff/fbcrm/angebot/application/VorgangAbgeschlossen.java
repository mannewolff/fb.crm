package org.mwolff.fbcrm.angebot.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Am abgeschlossenen Vorgang entsteht kein Angebot und wird keines versendet (Kriterium 9, E13).
 *
 * <p>Die Sperre reicht genau so weit, wie Kriterium 9 sie zieht: Einen bestehenden Entwurf zu
 * aendern oder zu verwerfen bleibt erlaubt, und jeder Leseweg ohnehin. Das ist die ausdrueckliche
 * Ausnahme von E26 des Vorgang-Moduls („der Abschlussstand ist keine Schreibsperre") — ihr Anlass
 * ist die Festschreibung eines Belegs, nicht die Pflege eines Entwurfs.
 *
 * <p>409 und nicht 403: Die Anfrage ist in Ordnung, nur der Zustand des Vorgangs passt nicht — und
 * er kann sich, wie bei {@link AngebotNichtAenderbar}, hinter dem Ruecken des Anwenders geaendert
 * haben.
 */
@ResponseStatus(
    code = HttpStatus.CONFLICT,
    reason = "Dieser Vorgang ist abgeschlossen; ein Angebot entsteht daran nicht mehr.")
public final class VorgangAbgeschlossen extends RuntimeException {}
