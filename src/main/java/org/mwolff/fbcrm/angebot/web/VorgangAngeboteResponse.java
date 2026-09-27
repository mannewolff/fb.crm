package org.mwolff.fbcrm.angebot.web;

import java.util.List;
import org.mwolff.fbcrm.angebot.application.AngebotAnsicht;

/**
 * Die Angebote eines Vorgangs (Kriterium 20).
 *
 * <p>Ein Objekt mit einer Liste und kein nacktes Array: So bekommt die Antwort spaeter Platz fuer
 * das, was die Karte sonst noch zeigt, ohne dass die Oberflaeche ihren Parser wechselt. Eine leere
 * Liste heisst „dieser Vorgang hat noch kein Angebot".
 *
 * @param angebote die Angebote in der Reihenfolge aus E25 — Entwuerfe zuerst
 */
public record VorgangAngeboteResponse(List<AngebotZeileResponse> angebote) {

  /** Die Sicht der Oberflaeche auf die Angebote eines Vorgangs. */
  static VorgangAngeboteResponse of(final List<AngebotAnsicht> ansichten) {
    return new VorgangAngeboteResponse(ansichten.stream().map(AngebotZeileResponse::of).toList());
  }
}
