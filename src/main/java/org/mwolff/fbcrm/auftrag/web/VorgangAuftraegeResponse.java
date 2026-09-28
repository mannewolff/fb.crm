package org.mwolff.fbcrm.auftrag.web;

import java.util.List;
import org.mwolff.fbcrm.auftrag.domain.Auftrag;

/**
 * Die Auftraege eines Vorgangs (Kriterium 9).
 *
 * <p>Ein Objekt mit einer Liste und kein nacktes Array, wie in {@code
 * angebot.web.VorgangAngeboteResponse}: So bekommt die Antwort spaeter Platz fuer das, was die
 * Karte sonst noch zeigt, ohne dass die Oberflaeche ihren Parser wechselt. Eine leere Liste heisst
 * „dieser Vorgang hat noch keinen Auftrag".
 *
 * @param auftraege die Auftraege in der Reihenfolge aus Kriterium 9 — der juengste oben
 */
public record VorgangAuftraegeResponse(List<AuftragZeileResponse> auftraege) {

  /** Die Sicht der Oberflaeche auf die Auftraege eines Vorgangs. */
  static VorgangAuftraegeResponse of(final List<Auftrag> auftraege) {
    return new VorgangAuftraegeResponse(auftraege.stream().map(AuftragZeileResponse::of).toList());
  }
}
