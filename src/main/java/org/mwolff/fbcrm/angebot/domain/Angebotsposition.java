package org.mwolff.fbcrm.angebot.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Eine Position eines Angebots (Kriterium 4).
 *
 * <p>Der Betrag wird gerechnet und nicht gespeichert (E5): {@link #betrag()} ist Menge mal
 * Einzelpreis, kaufmaennisch auf den Cent gerundet. Kriterium 5 nennt dafuer ein Paar, das genau
 * auf der halben Einheit liegt — 2,5 Personentage zu 1.000,01 € ergeben 2.500,025 € und damit
 * 2.500,03 €. {@link RoundingMode#HALF_EVEN} gaebe hier 2.500,02 € und ist deshalb falsch.
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
    return menge.multiply(einzelpreis).setScale(2, RoundingMode.HALF_UP);
  }
}
