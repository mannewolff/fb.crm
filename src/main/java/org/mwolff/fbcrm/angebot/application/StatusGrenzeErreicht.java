package org.mwolff.fbcrm.angebot.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Der verlangte Statuswechsel fuehrt ueber das Ende der Reihe hinaus (Issue #127, Kriterium 4).
 *
 * <p>„Status weiter" bei „abgerechnet" und „Status zurueck" bei „angelegt" haben kein Ziel. Die
 * Oberflaeche bietet die Taste dort gar nicht an; kommt der Aufruf trotzdem, antwortet die
 * Schnittstelle mit 409.
 *
 * <p>Geworfen wird sie in der Domaene — in {@code Angebot} selbst —, damit kein Weg an der Regel
 * vorbeifuehrt. Sie liegt trotzdem hier: Ihr Statuscode ist eine Aussage der Schnittstelle, und
 * {@code @ResponseStatus} ist eine Spring-Annotation, die das Domaenenmodell nicht tragen darf
 * (CLAUDE-java.md §6.1, {@code ArchitectureTest}).
 *
 * <p>409 und nicht 422: Die Anfrage ist wohlgeformt, nur der Status des Angebots passt nicht — und
 * er kann sich hinter dem Ruecken des Anwenders geaendert haben.
 */
@ResponseStatus(code = HttpStatus.CONFLICT, reason = StatusGrenzeErreicht.MELDUNG)
public final class StatusGrenzeErreicht extends RuntimeException {

  /** Die Meldung an der Schnittstelle. */
  public static final String MELDUNG = "In diese Richtung gibt es keinen weiteren Status.";
}
