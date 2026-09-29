package org.mwolff.fbcrm.angebot.web;

import java.util.List;
import org.mwolff.fbcrm.angebot.application.AngebotAnsicht;

/**
 * Die Angebote einer Firma (Kriterium 20).
 *
 * <p>Ein Objekt mit einer Liste und kein nacktes Array: So bekommt die Antwort spaeter Platz fuer
 * das, was die Karte sonst noch zeigt, ohne dass die Oberflaeche ihren Parser wechselt. Eine leere
 * Liste heisst „diese Firma hat noch kein Angebot".
 *
 * @param angebote die Angebote in der Reihenfolge aus E25 — Entwuerfe zuerst
 */
public record FirmaAngeboteResponse(List<AngebotZeileResponse> angebote) {

  /** Die Sicht der Oberflaeche auf die Angebote einer Firma. */
  static FirmaAngeboteResponse of(final List<AngebotAnsicht> ansichten) {
    return new FirmaAngeboteResponse(ansichten.stream().map(AngebotZeileResponse::of).toList());
  }
}
