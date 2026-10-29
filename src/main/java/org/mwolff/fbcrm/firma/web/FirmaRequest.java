package org.mwolff.fbcrm.firma.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.firma.application.FirmaDaten;

/**
 * Die Eingaben der Firmenmaske — fuer das Anlegen und das Aendern dieselben.
 *
 * <p>Pflicht sind der Name und das Land: Eine Firma wird oft mit nichts als ihrem Namen angelegt
 * und spaeter vervollstaendigt (Kriterium 4); das Land ist ein Kuerzel der Laenderliste, und die
 * Maske legt {@code DE} vor (Plan #461, E11). Ein unbekanntes Kuerzel weist {@link
 * LaenderkuerzelConstraint} am Feld {@code land} ab. Die Laengengrenzen sind dieselben wie in
 * {@code V2__firma_und_ansprechpartner.sql}; ohne sie antwortete die Anwendung auf eine zu lange
 * Eingabe mit einem Datenbankfehler statt mit einer Meldung am Feld.
 *
 * <p>{@code @NotBlank} weist eine Eingabe aus lauter Leerraum ab — dieselbe Regel, die {@link
 * FirmaDaten#normalisiert()} an der Transaktionsgrenze ein zweites Mal zieht.
 *
 * @param name Name der Firma
 * @param strasse Strasse und Hausnummer, oder {@code null}
 * @param plz Postleitzahl, oder {@code null}
 * @param ort Ort, oder {@code null}
 * @param land Land als Kuerzel nach ISO 3166-1, etwa {@code DE}
 * @param steuernummer Steuernummer, oder {@code null}
 * @param umsatzsteuerId Umsatzsteuer-Identifikationsnummer, oder {@code null}
 */
public record FirmaRequest(
    @NotBlank @Size(max = 200) String name,
    @Size(max = 200) @Nullable String strasse,
    @Size(max = 20) @Nullable String plz,
    @Size(max = 200) @Nullable String ort,
    @NotBlank @LaenderkuerzelConstraint String land,
    @Size(max = 50) @Nullable String steuernummer,
    @Size(max = 50) @Nullable String umsatzsteuerId) {

  /** Dieselben Angaben in der Sprache der Anwendungsschicht. */
  public FirmaDaten daten() {
    return new FirmaDaten(
        name, new Anschrift(strasse, plz, ort, land), steuernummer, umsatzsteuerId);
  }
}
