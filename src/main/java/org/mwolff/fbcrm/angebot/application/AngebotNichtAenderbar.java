package org.mwolff.fbcrm.angebot.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Der Zustand des Angebots laesst den verlangten Uebergang nicht zu (Kriterium 17).
 *
 * <p>Geworfen wird sie in der Domaene — in {@code Angebot} selbst —, damit kein Weg an der
 * Zustandsmaschine vorbeifuehrt. Sie liegt trotzdem hier: Ihr Statuscode ist eine Aussage der
 * Schnittstelle, und {@code @ResponseStatus} ist eine Spring-Annotation, die das Domaenenmodell
 * nicht tragen darf (CLAUDE-java.md §6.1, {@code ArchitectureTest}).
 *
 * <p>409 und nicht 422: Die Anfrage ist in Ordnung, nur der Zustand des Angebots passt nicht — und
 * er kann sich, anders als eine ungueltige Eingabe, hinter dem Ruecken des Anwenders geaendert
 * haben.
 */
@ResponseStatus(code = HttpStatus.CONFLICT, reason = AngebotNichtAenderbar.MELDUNG)
public final class AngebotNichtAenderbar extends RuntimeException {

  /** Die Meldung an der Schnittstelle. */
  public static final String MELDUNG =
      "In diesem Zustand laesst sich das Angebot nicht mehr aendern.";
}
