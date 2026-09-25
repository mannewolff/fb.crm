package org.mwolff.fbcrm.firma.web;

import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.firma.domain.Ansprechpartner;

/**
 * Ein Ansprechpartner, so wie die Oberflaeche ihn zeigt.
 *
 * <p>Die Firma steht nicht drin: Der Ansprechpartner kommt ausschliesslich eingebettet in der
 * Antwort seiner Firma, und eine Kennung, die dort ohnehin feststeht, waere nur eine zweite
 * Wahrheit (E7).
 *
 * @param id technische Id
 * @param vorname Vorname, oder {@code null}
 * @param nachname Nachname
 * @param rolle Rolle oder Funktion in der Firma, oder {@code null}
 * @param email E-Mail-Adresse, oder {@code null}
 * @param telefonFestnetz Festnetznummer, oder {@code null}
 * @param telefonMobil Mobilnummer, oder {@code null}
 * @param aktiv {@code false}, solange der Ansprechpartner stillgelegt ist
 */
public record AnsprechpartnerResponse(
    long id,
    @Nullable String vorname,
    String nachname,
    @Nullable String rolle,
    @Nullable String email,
    @Nullable String telefonFestnetz,
    @Nullable String telefonMobil,
    boolean aktiv) {

  /** Die Sicht der Oberflaeche auf einen Ansprechpartner. */
  static AnsprechpartnerResponse of(final Ansprechpartner partner) {
    return new AnsprechpartnerResponse(
        partner.requireId(),
        partner.vorname(),
        partner.nachname(),
        partner.rolle(),
        partner.email(),
        partner.telefonFestnetz(),
        partner.telefonMobil(),
        partner.aktiv());
  }
}
