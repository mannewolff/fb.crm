package org.mwolff.fbcrm.rechnung.web;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import org.mwolff.fbcrm.rechnung.domain.Nummernmuster;
import org.mwolff.fbcrm.rechnung.domain.Rechnungseinstellungen;

/**
 * Die Eingaben des Bereichs „Rechnung" unter Administration (#159, Kriterien 7 bis 9).
 *
 * <p>Jeder der vier Werte ist Pflicht: Die Maske zeigt sie alle auf einmal und schickt sie alle
 * zurueck, und jeder hat eine Vorbelegung — „nicht angegeben" gibt es hier nicht.
 *
 * <p>Die Grenzen sind dieselben wie in {@code V14__rechnung_einstellungen.sql}; ohne sie antwortete
 * die Anwendung auf einen Wert ausserhalb mit einem Datenbankfehler statt mit einer Meldung am
 * Feld. Der Steuersatz kommt als <b>Dezimaltext</b> („19.50") — so schickt die Maske ihn, und
 * Jackson wandelt ihn ohne Zwischenschritt ueber {@code double}, der die zweite Nachkommastelle
 * verschieben koennte.
 *
 * @param nummerMuster Schreibweise der Rechnungsnummer
 * @param naechsteNummer die naechste laufende Nummer, ab 1
 * @param steuersatz Mehrwertsteuersatz in Prozent, 0 bis 100, hoechstens zwei Nachkommastellen
 * @param zahlungszielTage Zahlungsziel in Tagen, ab 0
 */
public record RechnungseinstellungenRequest(
    @NummernmusterConstraint String nummerMuster,
    @NotNull @Min(1) Integer naechsteNummer,
    @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2)
        BigDecimal steuersatz,
    @NotNull @Min(0) Integer zahlungszielTage) {

  /** Dieselben Einstellungen in der Sprache der Fachschicht. */
  Rechnungseinstellungen einstellungen() {
    return new Rechnungseinstellungen(
        new Nummernmuster(nummerMuster), naechsteNummer, steuersatz, zahlungszielTage);
  }
}
