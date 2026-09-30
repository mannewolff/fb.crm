package org.mwolff.fbcrm.rechnung.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.rechnung.application.AbrechenbaresAngebot;

/**
 * Die Wahl „Neue Rechnung": die Angebote, aus denen eine Rechnung entstehen darf (#160, Kriterium
 * 2).
 *
 * <p>Die Liste fuehrt, der Server entscheidet: Dass ein Angebot hier steht, ist eine Auskunft und
 * keine Zusage — beim Anlegen prueft {@code RechnungAnlegenUseCase} denselben Stand noch einmal.
 *
 * @param angebote die abrechenbaren Angebote
 */
public record AbrechenbareAngeboteResponse(List<Zeile> angebote) {

  /** Die Sicht der Oberflaeche auf die Wahl. */
  static AbrechenbareAngeboteResponse of(final List<AbrechenbaresAngebot> zeilen) {
    return new AbrechenbareAngeboteResponse(zeilen.stream().map(Zeile::of).toList());
  }

  /**
   * Eine Zeile der Wahl.
   *
   * @param angebotId Kennung des Angebots
   * @param firmaId Kennung der Firma, an die das Angebot geht
   * @param firmaName Name dieser Firma
   * @param angebotDatum Datum des Angebots
   * @param offenerBetrag Summe der offenen Mengen mal Einzelpreis, gerechnet
   */
  public record Zeile(
      long angebotId,
      long firmaId,
      String firmaName,
      LocalDate angebotDatum,
      BigDecimal offenerBetrag) {

    static Zeile of(final AbrechenbaresAngebot zeile) {
      final Angebot angebot = zeile.angebot();
      return new Zeile(
          angebot.requireId(),
          angebot.firmaId(),
          zeile.firmaName(),
          angebot.angebotDatum(),
          zeile.offenerBetrag());
    }
  }
}
