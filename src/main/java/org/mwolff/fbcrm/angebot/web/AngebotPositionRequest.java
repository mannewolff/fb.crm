package org.mwolff.fbcrm.angebot.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Einheit;

/**
 * Eine Position, wie die Entwurfsmaske sie einreicht (Kriterium 4).
 *
 * <p><b>Kein Platz und kein Betrag.</b> Die Reihenfolge ist die der Liste (E24), und der Betrag
 * wird gerechnet (E5); beide als Feld hiesse, dem Absender eine Aussage zu glauben, die die
 * Anwendung selbst kennt.
 *
 * <p>Die Bezeichnung darf leer sein, aber nicht fehlen: Eine frisch hinzugefuegte Zeile hat noch
 * keinen Text, und was zum Versenden fehlt, nennt die Versandpruefung Feld fuer Feld (E27). Menge
 * und Einzelpreis duerfen nicht negativ sein und tragen hoechstens zwei Nachkommastellen —
 * dieselben Grenzen wie {@code numeric(12,2)} in {@code V6__angebot.sql}; ohne sie antwortete die
 * Anwendung auf eine dritte Nachkommastelle mit einem stillen Rundungsfehler oder einem
 * Datenbankfehler.
 *
 * @param bezeichnung die Leistung; darf leer sein
 * @param abrechnungsmodus nach Aufwand oder zum Festpreis
 * @param menge Menge in der angegebenen Einheit, nicht negativ
 * @param einheit Einheit der Menge
 * @param einzelpreis Netto-Preis je Einheit, nicht negativ
 */
public record AngebotPositionRequest(
    @NotNull @Size(max = 300) String bezeichnung,
    @NotNull Abrechnungsmodus abrechnungsmodus,
    @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal menge,
    @NotNull Einheit einheit,
    @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal einzelpreis) {

  /** Dieselbe Position in der Sprache der Fachschicht. */
  Angebotsposition position() {
    return new Angebotsposition(bezeichnung, abrechnungsmodus, menge, einheit, einzelpreis);
  }
}
