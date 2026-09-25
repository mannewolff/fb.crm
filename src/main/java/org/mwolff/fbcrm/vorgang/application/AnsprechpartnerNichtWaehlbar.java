package org.mwolff.fbcrm.vorgang.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Der eingereichte Ansprechpartner steht fuer diesen Vorgang nicht zur Wahl (E19, Kriterium 6).
 *
 * <p>Das gilt fuer eine unbekannte Kennung, fuer einen stillgelegten Ansprechpartner und fuer
 * einen, der zu einer anderen Firma gehoert als der gewaehlten. Beim Aendern bleibt allein der
 * bereits zugeordnete, inzwischen stillgelegte Ansprechpartner zulaessig (Kriterium 23) — die
 * Zugehoerigkeit zur Firma dagegen ohne Ausnahme.
 *
 * <p>400 und nicht 404, aus demselben Grund wie bei {@link FirmaNichtWaehlbar}.
 */
@ResponseStatus(
    code = HttpStatus.BAD_REQUEST,
    reason = "Dieser Ansprechpartner steht nicht zur Wahl.")
public final class AnsprechpartnerNichtWaehlbar extends RuntimeException {}
