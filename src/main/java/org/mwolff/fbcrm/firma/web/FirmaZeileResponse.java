package org.mwolff.fbcrm.firma.web;

import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.firma.application.FirmaZeile;

/**
 * Eine Zeile der Firmenuebersicht, so wie die Liste sie zeigt.
 *
 * @param id technische Id der Firma
 * @param name Name der Firma
 * @param ort Ort aus der Anschrift, oder {@code null}
 * @param aktiveAnsprechpartner Zahl der aktiven Ansprechpartner dieser Firma
 * @param aktiv {@code false}, solange die Firma stillgelegt ist — daran haengt das Schild
 *     „stillgelegt" (E15)
 */
public record FirmaZeileResponse(
    long id, String name, @Nullable String ort, long aktiveAnsprechpartner, boolean aktiv) {

  /** Die Sicht der Oberflaeche auf eine Zeile der Uebersicht. */
  static FirmaZeileResponse of(final FirmaZeile zeile) {
    return new FirmaZeileResponse(
        zeile.id(), zeile.name(), zeile.ort(), zeile.aktiveAnsprechpartner(), zeile.aktiv());
  }
}
