package org.mwolff.fbcrm.angebot.web;

import java.util.List;
import org.mwolff.fbcrm.angebot.domain.Angebotsanlage;

/**
 * Die Anlagen eines Angebots (Issue #148, Kriterium 4).
 *
 * <p>Ein Objekt mit einer Liste und kein nacktes Array, wie {@link AngebotKommentareResponse}: So
 * bekommt die Antwort spaeter Platz fuer das, was der Bereich sonst noch zeigt, ohne dass die
 * Oberflaeche ihren Parser wechselt. Eine leere Liste heisst „noch keine Anlage" (Kriterium 11).
 *
 * @param anlagen die Anlagen, neueste zuerst
 */
public record AngebotAnlagenResponse(List<AngebotAnlageResponse> anlagen) {

  /** Die Sicht der Oberflaeche auf die Anlagen eines Angebots. */
  static AngebotAnlagenResponse of(final List<Angebotsanlage> anlagen) {
    return new AngebotAnlagenResponse(anlagen.stream().map(AngebotAnlageResponse::of).toList());
  }
}
