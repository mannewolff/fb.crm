package org.mwolff.fbcrm.vorgang.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Die eingereichte Firma steht fuer diesen Vorgang nicht zur Wahl (E19, Kriterium 6).
 *
 * <p>Das gilt fuer eine unbekannte wie fuer eine stillgelegte Kennung. Beim Aendern bleibt allein
 * die bereits zugeordnete, inzwischen stillgelegte Firma zulaessig (Kriterium 23).
 *
 * <p>400 und nicht 404: Die Firma kommt im Rumpf und nicht im Pfad — die angefragte Ressource ist
 * der Vorgang, und die Eingabe zu ihm ist ungueltig. Ein 404 sagte, es gaebe den Weg nicht.
 */
@ResponseStatus(code = HttpStatus.BAD_REQUEST, reason = "Diese Firma steht nicht zur Wahl.")
public final class FirmaNichtWaehlbar extends RuntimeException {}
