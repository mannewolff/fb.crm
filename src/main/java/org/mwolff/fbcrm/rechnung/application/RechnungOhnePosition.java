package org.mwolff.fbcrm.rechnung.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Der Entwurf traegt keine Position und wird darum nicht gestellt (#160, Kriterium 5).
 *
 * <p>Eine Rechnung ueber nichts ist keine Rechnung: Sie verbrauchte eine Nummer, nennte keine
 * Leistung und forderte 0,00 €. Dass ein <b>Entwurf</b> ohne Position erlaubt bleibt, ist kein
 * Widerspruch — er ist ein Zwischenstand, und wer alle Mengen auf 0 setzt, hat ihn noch nicht
 * aufgegeben (Kriterium 12).
 *
 * <p>422 und nicht 409: Die Rechnung ist im richtigen Zustand, nur ihr Inhalt traegt den Schritt
 * nicht. Ein Feldfehler ist das nicht — die Maske hat fuer „keine Position" kein Feld, die Meldung
 * gehoert an die Rechnung als Ganzes.
 */
@ResponseStatus(code = HttpStatus.UNPROCESSABLE_ENTITY, reason = RechnungOhnePosition.MELDUNG)
public final class RechnungOhnePosition extends RuntimeException {

  /** Die Meldung an der Schnittstelle. */
  public static final String MELDUNG = "Die Rechnung hat keine Position";
}
