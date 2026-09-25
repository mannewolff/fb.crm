package org.mwolff.fbcrm.vorgang.web;

import org.mwolff.fbcrm.vorgang.domain.Vorgang;

/**
 * Die Antwort auf ein Anlegen: Kennung und Nummer des frisch angelegten Vorgangs (E25).
 *
 * <p>Der einzige Schreibweg mit Rumpf. Die Oberflaeche braucht die Kennung fuer den Weg auf die
 * Detailansicht (Kriterium 9) und die Nummer, um sie sofort zeigen zu koennen (Kriterium 8) —
 * beides entsteht erst im Bestand. Die vollstaendige Ansicht liest sie danach ueber {@code GET
 * /api/vorgaenge/{id}}; sie hier mitzuschicken hiesse, eine leere Historie als Ergebnis auszugeben.
 *
 * @param id technische Id
 * @param nummer fortlaufende Vorgangsnummer; als Zahl, das {@code #} setzt die Oberflaeche
 */
public record VorgangAngelegtResponse(long id, long nummer) {

  /** Die Sicht der Oberflaeche auf einen frisch angelegten Vorgang. */
  static VorgangAngelegtResponse of(final Vorgang vorgang) {
    return new VorgangAngelegtResponse(vorgang.requireId(), vorgang.nummer());
  }
}
