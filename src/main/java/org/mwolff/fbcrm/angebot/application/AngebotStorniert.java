package org.mwolff.fbcrm.angebot.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Das Angebot ist storniert, und der verlangte Schritt wuerde daran etwas aendern (Issue #333, Plan
 * #332 E2 und E16).
 *
 * <p>Solange ein Angebot storniert ist, steht seine Statuskette still: Weder „Status weiter" noch
 * „Status zurueck" noch der Sprung auf „abgerechnet" gehen durch. Sonst stuende das Angebot nach
 * der Ruecknahme nicht mehr in dem Zustand, aus dem es storniert wurde (#330, Kriterium 9).
 *
 * <p>Geworfen wird sie in der Domaene — in {@code Angebot} selbst —, damit kein Weg an der Regel
 * vorbeifuehrt. Sie liegt trotzdem hier, aus demselben Grund wie {@link StatusGrenzeErreicht}: Ihr
 * Statuscode ist eine Aussage der Schnittstelle, und {@code @ResponseStatus} ist eine
 * Spring-Annotation, die das Domaenenmodell nicht tragen darf (CLAUDE-java.md §6.1, {@code
 * ArchitectureTest}).
 *
 * <p>409 und nicht 422: Die Anfrage ist wohlgeformt, nur der Stand des Angebots laesst den Schritt
 * nicht zu.
 */
@ResponseStatus(code = HttpStatus.CONFLICT, reason = AngebotStorniert.MELDUNG)
public final class AngebotStorniert extends RuntimeException {

  /** Die Meldung an der Schnittstelle (E16). */
  public static final String MELDUNG =
      "Am stornierten Angebot ändert sich nichts; nimm zuerst das Storno zurück.";
}
