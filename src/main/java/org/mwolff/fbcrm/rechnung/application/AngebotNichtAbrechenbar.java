package org.mwolff.fbcrm.rechnung.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Aus diesem Angebot entsteht keine Rechnung (#160, Kriterium 2; Plan #169, E11).
 *
 * <p>Zwei Lagen, eine Meldung: Das Angebot steht vor {@code BESTELLT} — dann gibt es noch nichts
 * abzurechnen —, oder an keiner seiner Positionen ist noch etwas offen. Beide erfaehrt der Anwender
 * gleich; welche von beiden es war, sieht er an der Ansicht des Angebots.
 *
 * <p>409 und nicht 422: Die Anfrage ist wohlgeformt, nur der Stand des Angebots laesst den Schritt
 * nicht zu — und er kann sich hinter dem Ruecken des Anwenders geaendert haben, etwa weil eine
 * zweite Sitzung den Rest schon abgerechnet hat. <b>Der Server entscheidet</b>, nicht die
 * Auswahlliste der Oberflaeche.
 */
@ResponseStatus(code = HttpStatus.CONFLICT, reason = AngebotNichtAbrechenbar.MELDUNG)
public final class AngebotNichtAbrechenbar extends RuntimeException {

  /** Die Meldung an der Schnittstelle. */
  public static final String MELDUNG = "An diesem Angebot ist nichts abzurechnen.";
}
