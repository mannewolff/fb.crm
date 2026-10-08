package org.mwolff.fbcrm.firma.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.firma.application.AnsprechpartnerDaten;

/**
 * Die Eingaben der Ansprechpartnermaske — fuer das Anlegen und das Aendern dieselben.
 *
 * <p><b>Kein Feld fuer die Firma.</b> Sie steht im Pfad und wird gegen den gespeicherten Satz
 * geprueft; ein Feld im Rumpf waere ein zweiter, widersprechbarer Ort fuer dieselbe Angabe und
 * damit ein Weg, einen Ansprechpartner umzuhaengen (E7, Kriterium 12).
 *
 * <p>Nur der Nachname ist Pflicht. Die Laengengrenzen sind dieselben wie in {@code
 * V2__firma_und_ansprechpartner.sql}; ohne sie antwortete die Anwendung auf eine zu lange Eingabe
 * mit einem Datenbankfehler statt mit einer Meldung am Feld.
 *
 * @param vorname Vorname, oder {@code null}
 * @param nachname Nachname
 * @param rolle Rolle oder Funktion in der Firma, oder {@code null}
 * @param email E-Mail-Adresse, oder {@code null}
 * @param telefonFestnetz Festnetznummer, oder {@code null}
 * @param telefonMobil Mobilnummer, oder {@code null}
 */
public record AnsprechpartnerRequest(
    @Size(max = 200) @Nullable String vorname,
    @NotBlank @Size(max = 200) String nachname,
    @Size(max = 200) @Nullable String rolle,
    @Size(max = 320) @AnsprechpartnerEmailConstraint @Nullable String email,
    @Size(max = 50) @Nullable String telefonFestnetz,
    @Size(max = 50) @Nullable String telefonMobil) {

  /** Dieselben Angaben in der Sprache der Anwendungsschicht. */
  public AnsprechpartnerDaten daten() {
    return new AnsprechpartnerDaten(vorname, nachname, rolle, email, telefonFestnetz, telefonMobil);
  }
}
