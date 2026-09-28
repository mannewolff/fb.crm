package org.mwolff.fbcrm.angebot.domain;

import java.math.BigDecimal;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Einheit;
import org.mwolff.fbcrm.common.Geldrechnung;

/**
 * Eine Position eines Angebots (Kriterium 4).
 *
 * <p>Der Betrag wird gerechnet und nicht gespeichert (E5): {@link #betrag()} ist Menge mal
 * Einzelpreis nach der einen Regel in {@link Geldrechnung} — dort steht auch, warum kaufmaennisch
 * und nicht mathematisch gerundet wird. Dieselbe Regel rechnet jeder weitere Beleg, damit ein
 * Auftrag die Summe seines Angebots auf den Cent trifft.
 *
 * <p>Die Position traegt ihren Platz in der Reihenfolge nicht: Die Reihenfolge ist die der Liste am
 * Angebot, und der Bestand vergibt sie beim Schreiben lueckenlos ab 1 (E24). Eine Nummer im Record
 * waere eine zweite Wahrheit, die beim Umstellen nachgezogen werden muesste.
 *
 * @param bezeichnung die Leistung; darf im Entwurf leer sein (E27)
 * @param abrechnungsmodus nach Aufwand oder zum Festpreis
 * @param menge Menge in der angegebenen Einheit, nicht negativ
 * @param einheit Einheit der Menge
 * @param einzelpreis Netto-Preis je Einheit, nicht negativ
 */
public record Angebotsposition(
    String bezeichnung,
    Abrechnungsmodus abrechnungsmodus,
    BigDecimal menge,
    Einheit einheit,
    BigDecimal einzelpreis) {

  /** Der Netto-Betrag der Position, kaufmaennisch auf den Cent gerundet (Kriterium 5). */
  public BigDecimal betrag() {
    return Geldrechnung.betrag(menge, einzelpreis);
  }
}
