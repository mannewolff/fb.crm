package org.mwolff.fbcrm.rechnung.web;

import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.rechnung.domain.Belegempfaenger;

/**
 * Der Empfaenger, wie er beim Stellen der Rechnung galt (#160, Kriterium 14).
 *
 * <p>Die Anschrift steht flach und nicht geschachtelt, wie bei {@code FirmaResponse}: Die Ansicht
 * zeigt die Zeilen eines Briefkopfs und keine Baumstruktur.
 *
 * <p>Einen Ansprechpartner fuehrt die Kopie nicht — der Beleg geht an die Firma (siehe {@code
 * Belegempfaenger}).
 *
 * @param firma Name der Firma, wie er auf dem Beleg steht
 * @param strasse Strasse und Hausnummer, oder {@code null}
 * @param plz Postleitzahl, oder {@code null}
 * @param ort Ort, oder {@code null}
 * @param land Land, oder {@code null}
 */
public record BelegempfaengerResponse(
    String firma,
    @Nullable String strasse,
    @Nullable String plz,
    @Nullable String ort,
    @Nullable String land) {

  /**
   * Die Sicht der Oberflaeche auf die Kopie des Empfaengers.
   *
   * @param empfaenger die Kopie, oder {@code null} — dann ist die Rechnung noch Entwurf
   */
  static @Nullable BelegempfaengerResponse of(final @Nullable Belegempfaenger empfaenger) {
    if (empfaenger == null) {
      return null;
    }
    final Anschrift anschrift = empfaenger.anschrift();
    return new BelegempfaengerResponse(
        empfaenger.firma(),
        anschrift.strasse(),
        anschrift.plz(),
        anschrift.ort(),
        anschrift.land());
  }
}
