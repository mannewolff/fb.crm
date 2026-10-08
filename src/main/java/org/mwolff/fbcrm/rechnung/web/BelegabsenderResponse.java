package org.mwolff.fbcrm.rechnung.web;

import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.rechnung.domain.Belegabsender;

/**
 * Die eigenen Angaben, wie sie beim Stellen der Rechnung galten (#160, Kriterium 14).
 *
 * <p>Die Anschrift steht flach, aus demselben Grund wie bei {@link BelegempfaengerResponse}. Eine
 * neue Bankverbindung oder ein Umzug aendern diese Kopie nicht mehr — genau darum steht sie hier
 * und nicht als Verweis auf die eigenen Angaben von heute.
 *
 * @param name Name, unter dem der Beleg hinausging
 * @param berufsbezeichnung Berufsbezeichnung ueber dem Namen, oder {@code null}
 * @param strasse Strasse und Hausnummer, oder {@code null}
 * @param plz Postleitzahl, oder {@code null}
 * @param ort Ort, oder {@code null}
 * @param land Land, oder {@code null}
 * @param email E-Mail-Adresse, oder {@code null}
 * @param telefon Telefonnummer, oder {@code null}
 * @param steuernummer Steuernummer, oder {@code null}
 * @param umsatzsteuerId Umsatzsteuer-Identifikationsnummer, oder {@code null}
 * @param bankverbindung Bankverbindung als Text, oder {@code null}
 * @param webadresse Adresse des eigenen Webauftritts, oder {@code null}
 */
public record BelegabsenderResponse(
    String name,
    @Nullable String berufsbezeichnung,
    @Nullable String strasse,
    @Nullable String plz,
    @Nullable String ort,
    @Nullable String land,
    @Nullable String email,
    @Nullable String telefon,
    @Nullable String steuernummer,
    @Nullable String umsatzsteuerId,
    @Nullable String bankverbindung,
    @Nullable String webadresse) {

  /**
   * Die Sicht der Oberflaeche auf die Kopie der eigenen Angaben.
   *
   * @param absender die Kopie, oder {@code null} — dann ist die Rechnung noch Entwurf
   */
  static @Nullable BelegabsenderResponse of(final @Nullable Belegabsender absender) {
    if (absender == null) {
      return null;
    }
    final Anschrift anschrift = absender.anschrift();
    return new BelegabsenderResponse(
        absender.name(),
        absender.berufsbezeichnung(),
        anschrift.strasse(),
        anschrift.plz(),
        anschrift.ort(),
        anschrift.land(),
        absender.email(),
        absender.telefon(),
        absender.steuernummer(),
        absender.umsatzsteuerId(),
        absender.bankverbindung(),
        absender.webadresse());
  }
}
