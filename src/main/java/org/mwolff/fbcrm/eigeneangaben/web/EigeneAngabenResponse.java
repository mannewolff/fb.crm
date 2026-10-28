package org.mwolff.fbcrm.eigeneangaben.web;

import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.eigeneangaben.application.GespeicherteEigeneAngaben;
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
 * @param iban IBAN ohne Leerzeichen, oder {@code null}
 * @param bic BIC, oder {@code null}
 * @param kontoinhaber Inhaber des Kontos, oder {@code null}
 * @param bisherigeBankverbindung die Bankverbindung als Freitext, nur zum Lesen, oder {@code null},
 *     sobald IBAN und Kontoinhaber gefuellt sind (Plan #461, E13)
 * @param version der Stand der Zeile, den das Speichern als {@code If-Match} vorlegt (Plan #415,
 *     E2)
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
    @Nullable String iban,
    @Nullable String bic,
    @Nullable String kontoinhaber,
    @Nullable String bisherigeBankverbindung,
    long version) {

  /** Die Sicht der Oberflaeche auf die eigenen Angaben samt ihrem Stand. */
  static EigeneAngabenResponse of(final GespeicherteEigeneAngaben gespeichert) {
    final EigeneAngaben angaben = gespeichert.angaben();
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
        angaben.iban(),
        angaben.bic(),
        angaben.kontoinhaber(),
        angaben.bisherigeBankverbindung(),
        gespeichert.version());
  }
}
