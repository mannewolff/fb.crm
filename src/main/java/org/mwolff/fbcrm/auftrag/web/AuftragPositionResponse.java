package org.mwolff.fbcrm.auftrag.web;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.auftrag.domain.Auftragsposition;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Einheit;

/**
 * Eine Auftragsposition, wie die Ansicht sie zeigt (Kriterien 4, 5).
 *
 * <p>Der {@code betrag} kommt gerechnet mit und steht in keiner Spalte (E11): Menge mal
 * Einzelpreis, kaufmaennisch auf den Cent gerundet — nach derselben Regel, mit der das Angebot
 * rechnet (E5). Nur so trifft der Auftrag die Summe seiner Quelle auf den Cent.
 *
 * <p>Ohne Platz: Die Reihenfolge ist die der Liste (E24), und eine Nummer daneben waere eine zweite
 * Wahrheit, die beim Umstellen nachgezogen werden muesste.
 *
 * @param bezeichnung die Leistung, uebernommen aus dem Angebot
 * @param abrechnungsmodus nach Aufwand oder zum Festpreis
 * @param menge die vereinbarte Menge
 * @param einheit Einheit der Menge
 * @param einzelpreis Netto-Preis je Einheit, uebernommen aus dem Angebot
 * @param stundenJePersonentag Stunden je Personentag, oder {@code null} beim Festpreis (E10)
 * @param betrag Netto-Betrag der Position, gerechnet
 */
public record AuftragPositionResponse(
    String bezeichnung,
    Abrechnungsmodus abrechnungsmodus,
    BigDecimal menge,
    Einheit einheit,
    BigDecimal einzelpreis,
    @Nullable BigDecimal stundenJePersonentag,
    BigDecimal betrag) {

  /** Die Sicht der Oberflaeche auf eine Position. */
  static AuftragPositionResponse of(final Auftragsposition position) {
    return new AuftragPositionResponse(
        position.bezeichnung(),
        position.abrechnungsmodus(),
        position.menge(),
        position.einheit(),
        position.einzelpreis(),
        position.stundenJePersonentag(),
        position.betrag());
  }
}
