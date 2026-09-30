package org.mwolff.fbcrm.angebot.domain;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Einheit;
import org.mwolff.fbcrm.common.Geldrechnung;

/**
 * Eine Position eines Angebots (Kriterium 4).
 *
 * <p>Der Betrag wird gerechnet und nicht gespeichert (E5): {@link #betrag()} ist Menge mal
 * Einzelpreis nach der einen Regel in {@link Geldrechnung} — dort steht auch, warum kaufmaennisch
 * und nicht mathematisch gerundet wird. Dieselbe Regel rechnet jeder weitere Beleg, damit eine
 * Rechnung die Summe ihres Angebots auf den Cent trifft.
 *
 * <p><b>Die Kennung bleibt.</b> Eine Rechnung soll sich auf eine Position ihres Angebots berufen
 * koennen und ihr zugeordnet bleiben, auch wenn das Angebot danach umgeordnet oder umbenannt wird
 * (Plan #169, E2). Darum traegt die Position ihre technische Kennung mit: Der Bestand schreibt eine
 * Zeile mit bekannter Kennung fort, statt sie zu ersetzen. Wer eine Kennung mitschickt, sagt damit
 * „dieselbe Position wie vorher"; wer keine mitschickt, legt eine neue an.
 *
 * <p>Die Position traegt ihren Platz in der Reihenfolge nicht: Die Reihenfolge ist die der Liste am
 * Angebot, und der Bestand vergibt sie beim Schreiben lueckenlos ab 1 (E24). Eine Nummer im Record
 * waere eine zweite Wahrheit, die beim Umstellen nachgezogen werden muesste.
 *
 * @param id technische Kennung — {@code null}, solange die Position nicht gespeichert ist
 * @param bezeichnung die Leistung; nie leer, das prueft der Eingang der Maske
 * @param abrechnungsmodus nach Aufwand oder zum Festpreis
 * @param menge Menge in der angegebenen Einheit, nicht negativ
 * @param einheit Einheit der Menge
 * @param einzelpreis Netto-Preis je Einheit, nicht negativ
 */
public record Angebotsposition(
    @Nullable Long id,
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
