package org.mwolff.fbcrm.vorgang.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Der Eintrag traegt die Art {@code EREIGNIS} und laesst sich darum nicht aendern (Kriterium 19).
 *
 * <p>Ein Ereignis ist der Nachweis eines Zustandswechsels. Waere es aenderbar, waere es kein
 * Nachweis mehr — anders als ein eigener Kommentar, der ein Nachtrag von Hand ist und korrigiert
 * werden darf.
 *
 * <p>{@code 409 Conflict} und nicht {@code 400}: Die Anfrage ist in sich richtig, sie widerspricht
 * dem Zustand des angesprochenen Eintrags. Nach demselben Muster wie {@link AnhangZuGross} haengt
 * der Status an der Ausnahme und nicht am Controller.
 */
@ResponseStatus(
    code = HttpStatus.CONFLICT,
    reason = "Ein Ereignis der Historie laesst sich nicht aendern.")
public final class EintragNichtAenderbar extends RuntimeException {}
