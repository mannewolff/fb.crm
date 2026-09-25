package org.mwolff.fbcrm.firma.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.firma.application.FirmaDaten;
import org.mwolff.fbcrm.firma.domain.Anschrift;

/**
 * Die Eingaben der Firmenmaske — fuer das Anlegen und das Aendern dieselben.
 *
 * <p>Nur der Name ist Pflicht: Eine Firma wird oft mit nichts als ihrem Namen angelegt und spaeter
 * vervollstaendigt (Kriterium 4). Die Laengengrenzen sind dieselben wie in {@code
 * V2__firma_und_ansprechpartner.sql}; ohne sie antwortete die Anwendung auf eine zu lange Eingabe
 * mit einem Datenbankfehler statt mit einer Meldung am Feld.
 *
 * <p>{@code @NotBlank} weist eine Eingabe aus lauter Leerraum ab — dieselbe Regel, die {@link
 * FirmaDaten#normalisiert()} an der Transaktionsgrenze ein zweites Mal zieht.
 *
 * @param name Name der Firma
 * @param strasse Strasse und Hausnummer, oder {@code null}
 * @param plz Postleitzahl, oder {@code null}
 * @param ort Ort, oder {@code null}
 * @param land Land, oder {@code null}
 * @param steuernummer Steuernummer, oder {@code null}
 * @param umsatzsteuerId Umsatzsteuer-Identifikationsnummer, oder {@code null}
 */
public record FirmaRequest(
    @NotBlank @Size(max = 200) String name,
    @Size(max = 200) @Nullable String strasse,
    @Size(max = 20) @Nullable String plz,
    @Size(max = 200) @Nullable String ort,
    @Size(max = 100) @Nullable String land,
    @Size(max = 50) @Nullable String steuernummer,
    @Size(max = 50) @Nullable String umsatzsteuerId) {

  /** Dieselben Angaben in der Sprache der Anwendungsschicht. */
  public FirmaDaten daten() {
    return new FirmaDaten(
        name, new Anschrift(strasse, plz, ort, land), steuernummer, umsatzsteuerId);
  }
}
