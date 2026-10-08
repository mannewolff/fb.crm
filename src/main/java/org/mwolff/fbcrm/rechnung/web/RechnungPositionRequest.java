package org.mwolff.fbcrm.rechnung.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import org.mwolff.fbcrm.rechnung.application.Abrechnungsangabe;

/**
 * Eine Angabe der Entwurfsmaske zu genau einer Angebotsposition (#160, Kriterien 4 und 5).
 *
 * <p><b>Kein Einzelpreis und keine Einheit.</b> Beide sind an der Rechnung nicht aenderbar (Frage
 * 5): Der Preis ist der, den der Entwurf beim Anlegen festgehalten hat, die Einheit kommt vom
 * Angebot. Sie hier entgegenzunehmen hiesse, dem Absender eine Aussage zu glauben, die die
 * Anwendung selbst kennt.
 *
 * <p>Die Menge darf 0 sein — dann faellt die Position aus der Rechnung heraus (Kriterium 5). Sie
 * darf nicht negativ sein und traegt hoechstens zwei Nachkommastellen, dieselben Grenzen wie {@code
 * numeric(12,2)} in {@code V18__rechnung.sql}; ohne sie antwortete die Anwendung auf eine dritte
 * Nachkommastelle mit einem stillen Rundungsfehler oder einem Datenbankfehler.
 *
 * @param angebotPositionId Kennung der Angebotsposition, um die es geht
 * @param bezeichnung die Leistung, wie sie auf der Rechnung stehen soll; nicht leer
 * @param menge die Menge, die jetzt abgerechnet wird; nicht negativ
 */
public record RechnungPositionRequest(
    @NotNull Long angebotPositionId,
    @NotBlank(message = BEZEICHNUNG_FEHLT) @Size(max = 300) String bezeichnung,
    @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal menge) {

  /** Die Meldung am Feld, wenn die Bezeichnung fehlt oder nur aus Leerzeichen besteht. */
  static final String BEZEICHNUNG_FEHLT = "Jede Position braucht eine Bezeichnung.";

  /** Dieselbe Angabe in der Sprache der Anwendungsschicht. */
  Abrechnungsangabe angabe() {
    return new Abrechnungsangabe(angebotPositionId, bezeichnung, menge);
  }
}
