package org.mwolff.fbcrm.rechnung.web;

import java.math.BigDecimal;
import org.mwolff.fbcrm.common.Einheit;
import org.mwolff.fbcrm.rechnung.application.Abrechnungszeile;

/**
 * Eine Zeile der Entwurfsmaske: eine Position des Angebots mit ihrem Stand (#160, Kriterium 4).
 *
 * <p>Die Maske zeigt <b>jede</b> Position des Angebots, auch eine, die dieser Entwurf nicht traegt
 * — dann mit {@code menge} gleich 0. {@code abgerechnet} und {@code offen} rechnen ohne diese
 * Rechnung; die {@code ueberschreitung} zaehlt ihre Menge mit (Kriterien 4, 7, 8).
 *
 * <p>Die Kennung ist die der <b>Angebotsposition</b>: Sie ist der Griff, an dem die Maske ihre
 * Angaben zurueckschickt (Plan #169, E2). Eine eigene Kennung der Rechnungsposition gibt es nicht.
 *
 * @param angebotPositionId Kennung der Angebotsposition
 * @param bezeichnung die Leistung, wie diese Rechnung sie nennt
 * @param einheit Einheit der Menge; sie kommt vom Angebot und ist hier nicht aenderbar
 * @param einzelpreis der Netto-Preis je Einheit, mit dem diese Rechnung rechnet
 * @param angeboten die Menge am Angebot
 * @param abgerechnet die Menge aus allen anderen Rechnungen dieses Angebots
 * @param offen die noch offene Menge ohne diese Rechnung, nie kleiner als 0
 * @param menge die Menge, die diese Rechnung abrechnet
 * @param ueberschreitung was mit dieser Menge zusammen zu viel waere, sonst 0
 */
public record RechnungZeileResponse(
    long angebotPositionId,
    String bezeichnung,
    Einheit einheit,
    BigDecimal einzelpreis,
    BigDecimal angeboten,
    BigDecimal abgerechnet,
    BigDecimal offen,
    BigDecimal menge,
    BigDecimal ueberschreitung) {

  /** Die Sicht der Oberflaeche auf eine Zeile der Maske. */
  static RechnungZeileResponse of(final Abrechnungszeile zeile) {
    return new RechnungZeileResponse(
        zeile.position().requireId(),
        zeile.bezeichnung(),
        zeile.einheit(),
        zeile.einzelpreis(),
        zeile.angeboten(),
        zeile.abgerechnet(),
        zeile.offen(),
        zeile.jetzt(),
        zeile.ueberschreitung());
  }
}
