package org.mwolff.fbcrm.rechnung.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.mwolff.fbcrm.rechnung.application.RechnungDaten;

/**
 * Die Eingaben der Entwurfsmaske — die Rechnung als Ganzes (#160, Kriterien 4, 10).
 *
 * <p>Ein {@code PUT} mit Datum, Leistungszeitraum und der vollstaendigen Angabenliste, dieselbe
 * Form wie am Angebot: Hinzufuegen, Aendern und Loeschen einzelner Positionen fallen daraus heraus.
 * Was nicht in der Liste steht, steht danach nicht auf der Rechnung.
 *
 * <p><b>Kein Feld fuer Angebot, Zustand, Nummer und Steuersatz.</b> Das Angebot bleibt das, zu dem
 * der Entwurf angelegt wurde; die uebrigen drei entstehen erst mit dem Stellen (Kriterium 14).
 *
 * <p>Der Leistungszeitraum ist Pflicht und hoechstens 100 Zeichen lang — dieselbe Grenze wie {@code
 * varchar(100)} in {@code V18__rechnung.sql}. Das Anlegen belegt ihn mit dem laufenden Monat vor;
 * ihn zu leeren waere ein Rueckschritt hinter diese Vorbelegung.
 *
 * @param rechnungDatum Datum der Rechnung
 * @param leistungszeitraum Zeitraum der Leistung als Text; nicht leer
 * @param positionen die vollstaendige Angabenliste, je Angebotsposition hoechstens eine
 */
public record RechnungRequest(
    @NotNull LocalDate rechnungDatum,
    @NotBlank(message = ZEITRAUM_FEHLT) @Size(max = 100) String leistungszeitraum,
    @NotNull @Valid List<RechnungPositionRequest> positionen) {

  /** Die Meldung am Feld, wenn der Leistungszeitraum fehlt oder nur aus Leerzeichen besteht. */
  static final String ZEITRAUM_FEHLT = "Die Rechnung braucht einen Leistungszeitraum.";

  /** Dieselben Angaben in der Sprache der Anwendungsschicht. */
  RechnungDaten daten() {
    return new RechnungDaten(
        rechnungDatum,
        leistungszeitraum,
        positionen.stream().map(RechnungPositionRequest::angabe).toList());
  }
}
