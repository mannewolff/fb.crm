package org.mwolff.fbcrm.vorgang.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.vorgang.application.VorgangDaten;

/**
 * Die Eingaben der Vorgangsmaske — fuer das Anlegen und das Aendern dieselben.
 *
 * <p><b>Kein Feld fuer die Nummer und keines fuer den Abschlussstand.</b> Die Nummer kommt aus dem
 * Nummernkreis und aendert sich nie (Kriterium 8), der Abschluss hat seine eigenen Wege (Kriterium
 * 20); ein Feld dafuer waere ein zweiter, widersprechbarer Ort fuer dieselbe Angabe.
 *
 * <p>Titel und Firma sind Pflicht, der Ansprechpartner ist optional (Kriterium 5). Beide
 * Pflichtangaben tragen ihre Bedingung hier, damit eine fehlende Angabe als {@code fieldErrors} am
 * Feld hinausgeht und nicht als Serverfehler: Die Maske nennt den Grund am Feld.
 *
 * <p>Die Laengengrenze des Titels ist dieselbe wie in {@code V3__vorgang_und_historie.sql}; ohne
 * sie antwortete die Anwendung auf eine zu lange Eingabe mit einem Datenbankfehler.
 *
 * @param titel Titel des Vorgangs
 * @param firmaId Kennung der gewaehlten Firma
 * @param ansprechpartnerId Kennung des gewaehlten Ansprechpartners, oder {@code null}
 */
public record VorgangRequest(
    @NotBlank @Size(max = 300) String titel,
    @NotNull Long firmaId,
    @Nullable Long ansprechpartnerId) {

  /** Dieselben Angaben in der Sprache der Anwendungsschicht. */
  public VorgangDaten daten() {
    return new VorgangDaten(titel, firmaId, ansprechpartnerId);
  }
}
