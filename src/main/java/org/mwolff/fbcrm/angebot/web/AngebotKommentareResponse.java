package org.mwolff.fbcrm.angebot.web;

import java.util.List;
import org.mwolff.fbcrm.angebot.domain.Angebotskommentar;

/**
 * Die Kommentare eines Angebots (Issue #140, Kriterium 4).
 *
 * <p>Ein Objekt mit einer Liste und kein nacktes Array, wie {@link FirmaAngeboteResponse}: So
 * bekommt die Antwort spaeter Platz fuer das, was der Bereich sonst noch zeigt, ohne dass die
 * Oberflaeche ihren Parser wechselt. Eine leere Liste heisst „noch kein Kommentar" (Kriterium 11).
 *
 * @param kommentare die Kommentare, neuester zuerst
 */
public record AngebotKommentareResponse(List<AngebotKommentarResponse> kommentare) {

  /** Die Sicht der Oberflaeche auf die Kommentare eines Angebots. */
  static AngebotKommentareResponse of(final List<Angebotskommentar> kommentare) {
    return new AngebotKommentareResponse(
        kommentare.stream().map(AngebotKommentarResponse::of).toList());
  }
}
