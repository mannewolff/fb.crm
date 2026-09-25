package org.mwolff.fbcrm.vorgang.web;

import java.util.List;
import org.mwolff.fbcrm.vorgang.application.VorgaengeDerFirma;
import org.mwolff.fbcrm.vorgang.application.VorgangZeile;

/**
 * Die Vorgaenge einer Firma, getrennt nach offen und abgeschlossen (Kriterium 12).
 *
 * <p>Zwei Listen und kein Kennzeichen in einer: Die Detailansicht der Firma zeigt die
 * abgeschlossenen abgesetzt unter den offenen. Zwei leere Listen heissen „diese Firma hat keinen
 * Vorgang".
 *
 * @param offene die offenen Vorgaenge in der Reihenfolge der Uebersicht (E16)
 * @param abgeschlossene die abgeschlossenen, in derselben Reihenfolge
 */
public record VorgaengeDerFirmaResponse(
    List<VorgangZeileResponse> offene, List<VorgangZeileResponse> abgeschlossene) {

  /** Die Sicht der Oberflaeche auf die Vorgaenge einer Firma. */
  static VorgaengeDerFirmaResponse of(final VorgaengeDerFirma vorgaenge) {
    return new VorgaengeDerFirmaResponse(
        zeilen(vorgaenge.offene()), zeilen(vorgaenge.abgeschlossene()));
  }

  private static List<VorgangZeileResponse> zeilen(final List<VorgangZeile> zeilen) {
    return zeilen.stream().map(VorgangZeileResponse::of).toList();
  }
}
