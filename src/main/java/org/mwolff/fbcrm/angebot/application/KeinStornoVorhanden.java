package org.mwolff.fbcrm.angebot.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Das Storno soll zurueckgenommen werden, aber das Angebot ist nicht storniert (Issue #334, Plan
 * #332 E16, E17).
 *
 * <p>Eine eigene Ausnahme und nicht {@link StornoNichtMoeglich} fuer beide Richtungen: Dieselbe
 * Ausnahme truege sonst zwei entgegengesetzte Aussagen (E16). Die Oberflaeche bietet die Taste am
 * nicht stornierten Angebot gar nicht an; kommt der Aufruf trotzdem, antwortet die Schnittstelle
 * mit 409.
 */
@ResponseStatus(code = HttpStatus.CONFLICT, reason = KeinStornoVorhanden.MELDUNG)
public final class KeinStornoVorhanden extends RuntimeException {

  /** Die Meldung an der Schnittstelle (E16). */
  public static final String MELDUNG = "Dieses Angebot ist nicht storniert.";
}
