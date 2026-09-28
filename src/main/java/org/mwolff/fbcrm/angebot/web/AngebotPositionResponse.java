package org.mwolff.fbcrm.angebot.web;

import java.math.BigDecimal;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Einheit;

/**
 * Eine Position, wie die Ansicht sie zeigt (Kriterien 4, 5).
 *
 * <p>Der {@code betrag} kommt gerechnet mit und steht in keiner Spalte (E5): Menge mal Einzelpreis,
 * kaufmaennisch auf den Cent gerundet. Die Oberflaeche rechnet beim Tippen mit — nach dem Speichern
 * gilt der Wert von hier.
 *
 * <p>Ohne Platz: Die Reihenfolge ist die der Liste (E24), und eine Nummer daneben waere eine zweite
 * Wahrheit, die beim Umstellen nachgezogen werden muesste.
 *
 * @param bezeichnung die Leistung
 * @param abrechnungsmodus nach Aufwand oder zum Festpreis
 * @param menge Menge in der angegebenen Einheit
 * @param einheit Einheit der Menge
 * @param einzelpreis Netto-Preis je Einheit
 * @param betrag Netto-Betrag der Position, gerechnet
 */
public record AngebotPositionResponse(
    String bezeichnung,
    Abrechnungsmodus abrechnungsmodus,
    BigDecimal menge,
    Einheit einheit,
    BigDecimal einzelpreis,
    BigDecimal betrag) {

  /** Die Sicht der Oberflaeche auf eine Position. */
  static AngebotPositionResponse of(final Angebotsposition position) {
    return new AngebotPositionResponse(
        position.bezeichnung(),
        position.abrechnungsmodus(),
        position.menge(),
        position.einheit(),
        position.einzelpreis(),
        position.betrag());
  }
}
