package org.mwolff.fbcrm.auftrag.domain;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Einheit;
import org.mwolff.fbcrm.common.Geldrechnung;

/**
 * Eine Position eines Auftrags (Kriterium 4).
 *
 * <p>Der Betrag wird gerechnet und nicht gespeichert (E11): {@link #betrag()} ist Menge mal
 * Einzelpreis nach der einen Regel in {@link Geldrechnung} — derselben, mit der das Angebot rechnet
 * (E5). Nur so trifft der Auftrag die Summe seines Angebots auf den Cent, und genau das muss er
 * gegenueber dem Kunden erklaeren koennen.
 *
 * <p>Die Position traegt ihren Platz in der Reihenfolge nicht: Die Reihenfolge ist die der Liste am
 * Auftrag, und der Bestand vergibt sie beim Schreiben lueckenlos ab 1 (E24 in Plan #87). Eine
 * Nummer im Record waere eine zweite Wahrheit, die beim Umstellen nachgezogen werden muesste.
 *
 * @param bezeichnung die Leistung, uebernommen aus der Angebotsposition
 * @param abrechnungsmodus nach Aufwand oder zum Festpreis
 * @param menge die vereinbarte Menge in der angegebenen Einheit, nicht negativ
 * @param einheit Einheit der Menge
 * @param einzelpreis Netto-Preis je Einheit, nicht negativ
 * @param stundenJePersonentag der Umrechnungsfaktor fuer die Zeiterfassung — gesetzt und groesser
 *     als null genau bei {@link Abrechnungsmodus#AUFWAND}, sonst {@code null} (E10). Er haengt am
 *     Abrechnungsmodus und nicht an der Einheit: Eine Aufwandsposition in Stunden traegt ihn ebenso
 *     wie eine in Personentagen.
 */
public record Auftragsposition(
    String bezeichnung,
    Abrechnungsmodus abrechnungsmodus,
    BigDecimal menge,
    Einheit einheit,
    BigDecimal einzelpreis,
    @Nullable BigDecimal stundenJePersonentag) {

  /** Der Netto-Betrag der Position, kaufmaennisch auf den Cent gerundet (Kriterium 5). */
  public BigDecimal betrag() {
    return Geldrechnung.betrag(menge, einzelpreis);
  }
}
