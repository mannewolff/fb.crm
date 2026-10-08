package org.mwolff.fbcrm.rechnung.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.mwolff.fbcrm.rechnung.application.Nachtragsdaten;

/**
 * Die Eckdaten einer nachgetragenen Rechnung, wie die Maske sie beim Anlegen und Aendern schickt
 * (#254, Kriterien 2 und 3; Plan #259, E9, E10, E11).
 *
 * <p><b>Hier steht nur die Form</b>, und sie antwortet mit 400 am jeweiligen Feld: Pflicht, die
 * Nummer hoechstens 50 Zeichen, beide Betraege mindestens 0,00 mit hoechstens zwei Nachkommastellen
 * — mehr Stellen werden abgewiesen, nicht gerundet (Kriterium 3). Die Paarregel „Brutto nicht unter
 * Netto", die Datumsgrenze und die vergebene Nummer prueft der Anwendungsfall (E9, E23).
 *
 * <p>Getrimmt wird die Nummer in {@link Nachtragsdaten}, nicht hier (E10); {@code @NotBlank} weist
 * eine Nummer aus lauter Leerzeichen trotzdem schon am Rand ab.
 *
 * <p>Kein Feld fuer das Original: Es geht als eigener Aufruf hinaus (E11).
 *
 * @param firmaId Kennung der Firma, an die die Rechnung ging
 * @param nummer die frei erfasste Rechnungsnummer
 * @param rechnungDatum Datum der Rechnung
 * @param netto der Nettobetrag, wie erfasst
 * @param brutto der Bruttobetrag, wie erfasst
 */
public record NachtragRequest(
    @NotNull Long firmaId,
    @NotBlank @Size(max = 50) String nummer,
    @NotNull LocalDate rechnungDatum,
    @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal netto,
    @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal brutto) {

  /** Dieselben Angaben in der Sprache der Anwendungsschicht. */
  Nachtragsdaten daten() {
    return new Nachtragsdaten(firmaId, nummer, rechnungDatum, netto, brutto);
  }
}
