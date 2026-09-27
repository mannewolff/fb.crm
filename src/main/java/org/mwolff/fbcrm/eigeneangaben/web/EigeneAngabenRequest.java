package org.mwolff.fbcrm.eigeneangaben.web;

import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngaben;

/**
 * Die Eingaben der Maske „Eigene Angaben".
 *
 * <p>Keine Pflichtangabe: Die Angaben werden nach und nach vervollstaendigt, und eine Instanz ohne
 * Steuernummer soll trotzdem einen Namen speichern koennen. Die Laengengrenzen sind dieselben wie
 * in {@code V5__eigene_angaben.sql}; ohne sie antwortete die Anwendung auf eine zu lange Eingabe
 * mit einem Datenbankfehler statt mit einer Meldung am Feld.
 *
 * <p>Die Zahlungsbedingungen tragen keine Grenze — sie sind ein Fliesstext in einer {@code
 * text}-Spalte, wie der Text eines Historieneintrags. Gegen einen uebergrossen Rumpf steht der
 * Riegel des Containers (E10).
 *
 * @param name Name, oder {@code null}
 * @param strasse Strasse und Hausnummer, oder {@code null}
 * @param plz Postleitzahl, oder {@code null}
 * @param ort Ort, oder {@code null}
 * @param land Land, oder {@code null}
 * @param email E-Mail-Adresse, oder {@code null}
 * @param telefon Telefonnummer, oder {@code null}
 * @param steuernummer Steuernummer, oder {@code null}
 * @param umsatzsteuerId Umsatzsteuer-Identifikationsnummer, oder {@code null}
 * @param bankverbindung Bankverbindung, oder {@code null}
 * @param zahlungsbedingungen Standardtext fuer Zahlungsbedingungen, oder {@code null}
 */
public record EigeneAngabenRequest(
    @Size(max = 200) @Nullable String name,
    @Size(max = 200) @Nullable String strasse,
    @Size(max = 20) @Nullable String plz,
    @Size(max = 200) @Nullable String ort,
    @Size(max = 100) @Nullable String land,
    @Size(max = 320) @Nullable String email,
    @Size(max = 50) @Nullable String telefon,
    @Size(max = 50) @Nullable String steuernummer,
    @Size(max = 50) @Nullable String umsatzsteuerId,
    @Size(max = 200) @Nullable String bankverbindung,
    @Nullable String zahlungsbedingungen) {

  /** Dieselben Angaben in der Sprache der Fachschicht. */
  public EigeneAngaben angaben() {
    return new EigeneAngaben(
        name,
        new Anschrift(strasse, plz, ort, land),
        email,
        telefon,
        steuernummer,
        umsatzsteuerId,
        bankverbindung,
        zahlungsbedingungen);
  }
}
