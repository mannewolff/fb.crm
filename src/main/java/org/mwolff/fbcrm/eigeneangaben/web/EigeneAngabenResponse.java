package org.mwolff.fbcrm.eigeneangaben.web;

import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngaben;

/**
 * Die eigenen Angaben mit allen ihren Feldern.
 *
 * <p>Die Anschrift steht flach und nicht geschachtelt, wie bei der Firma: Die Maske hat fuer jede
 * ihrer Angaben ein eigenes Feld, und eine Schachtelung braechte der Oberflaeche nur eine Ebene
 * mehr.
 *
 * <p>Der Zeitpunkt der letzten Aenderung fehlt bewusst — die Maske zeigt ihn nicht.
 *
 * @param name Name, oder {@code null}
 * @param berufsbezeichnung Berufsbezeichnung, oder {@code null}
 * @param strasse Strasse und Hausnummer, oder {@code null}
 * @param plz Postleitzahl, oder {@code null}
 * @param ort Ort, oder {@code null}
 * @param land Land, oder {@code null}
 * @param email E-Mail-Adresse, oder {@code null}
 * @param telefon Telefonnummer, oder {@code null}
 * @param webadresse Webadresse als Text, oder {@code null}
 * @param steuernummer Steuernummer, oder {@code null}
 * @param umsatzsteuerId Umsatzsteuer-Identifikationsnummer, oder {@code null}
 * @param bankverbindung Bankverbindung, oder {@code null}
 */
public record EigeneAngabenResponse(
    @Nullable String name,
    @Nullable String berufsbezeichnung,
    @Nullable String strasse,
    @Nullable String plz,
    @Nullable String ort,
    @Nullable String land,
    @Nullable String email,
    @Nullable String telefon,
    @Nullable String webadresse,
    @Nullable String steuernummer,
    @Nullable String umsatzsteuerId,
    @Nullable String bankverbindung) {

  /** Die Sicht der Oberflaeche auf die eigenen Angaben. */
  static EigeneAngabenResponse of(final EigeneAngaben angaben) {
    return new EigeneAngabenResponse(
        angaben.name(),
        angaben.berufsbezeichnung(),
        angaben.anschrift().strasse(),
        angaben.anschrift().plz(),
        angaben.anschrift().ort(),
        angaben.anschrift().land(),
        angaben.email(),
        angaben.telefon(),
        angaben.webadresse(),
        angaben.steuernummer(),
        angaben.umsatzsteuerId(),
        angaben.bankverbindung());
  }
}
