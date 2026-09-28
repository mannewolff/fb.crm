package org.mwolff.fbcrm.auftrag.application;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * Eine uebernommene Angebotsposition, wie der Anwender sie waehlt (Plan E7, Kriterium 2).
 *
 * <p><b>Drei Angaben und kein Preis.</b> Bezeichnung, Abrechnungsmodus, Einheit und Einzelpreis
 * liest der Anwendungsfall aus dem Angebot; sie tauchen hier nicht auf. Einer Anfrage den Preis zu
 * glauben, hiesse, genau die Angabe vom Absender zu nehmen, die Kriterium 2 schuetzt.
 *
 * @param platz der Platz der Position im Angebot — {@code index + 1} der Liste in {@code
 *     AngebotResponse.positionen}, weil die Positionen selbst keinen Platz tragen (E24 in Plan #87)
 * @param menge die vereinbarte Menge; sie darf die des Angebots nicht uebersteigen (F2)
 * @param stundenJePersonentag der Umrechnungsfaktor der Zeiterfassung — gesetzt genau bei einer
 *     Aufwandsposition, sonst {@code null} (E10)
 */
public record AuftragPositionwahl(
    int platz, BigDecimal menge, @Nullable BigDecimal stundenJePersonentag) {}
