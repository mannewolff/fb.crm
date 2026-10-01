package org.mwolff.fbcrm.rechnung.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.common.Einheit;
import org.mwolff.fbcrm.rechnung.application.Angebotsabrechnung;
import org.mwolff.fbcrm.rechnung.application.Positionsabrechnung;
import org.mwolff.fbcrm.rechnung.application.RechnungMitBetrag;
import org.mwolff.fbcrm.rechnung.domain.Positionsstand;
import org.mwolff.fbcrm.rechnung.domain.Rechnung;
import org.mwolff.fbcrm.rechnung.domain.Rechnungszustand;

/**
 * Der Abrechnungsstand eines Angebots, wie seine Ansicht ihn zeigt (#160, Kriterium 26).
 *
 * <p>Zwei Listen: je Position des Angebots, was angeboten, abgerechnet, offen und angefallen ist,
 * und die Rechnungen, aus denen das entstanden ist. Entwuerfe zaehlen dabei mit (Kriterium 6).
 *
 * @param positionen je Angebotsposition eine Zeile, in der Reihenfolge des Angebots
 * @param rechnungen die Rechnungen dieses Angebots, neueste zuerst
 */
public record AngebotAbrechnungResponse(
    List<Positionszeile> positionen, List<Rechnungszeile> rechnungen) {

  /** Die Sicht der Oberflaeche auf den Abrechnungsstand. */
  static AngebotAbrechnungResponse of(final Angebotsabrechnung abrechnung) {
    return new AngebotAbrechnungResponse(
        abrechnung.positionen().stream().map(Positionszeile::of).toList(),
        abrechnung.rechnungen().stream().map(Rechnungszeile::of).toList());
  }

  /**
   * Eine Position des Angebots mit ihrem Stand.
   *
   * <p>{@code offen} ist nie kleiner als 0, und was darueber hinaus abgerechnet wurde, steht als
   * {@code ueberschreitung} daneben (Kriterium 7) — zwei Angaben und nicht eine mit Vorzeichen.
   *
   * <p>{@code buchbar} und {@code angefallen} kommen aus der Zeiterfassung (Issue #193, Kriterien
   * 7, 8, 11). Die Oberflaeche zeigt die Spalte „Angefallen" an {@code buchbar}, nicht am Status
   * des Angebots und nicht daran, ob schon eine Rechnung besteht — sonst verschwaenden die Stunden
   * genau dann, wenn das Angebot auf „abgerechnet" gesprungen ist.
   *
   * @param angebotPositionId Kennung der Angebotsposition
   * @param bezeichnung die Leistung, wie das Angebot sie nennt
   * @param einheit Einheit der Mengen
   * @param angeboten die Menge am Angebot
   * @param abgerechnet die Summe aller Rechnungen dieses Angebots zu dieser Position
   * @param offen die noch offene Menge, nie kleiner als 0
   * @param ueberschreitung was ueber die angebotene Menge hinaus abgerechnet wurde, sonst 0
   * @param buchbar ob auf die Position Arbeitszeit gebucht werden kann
   * @param angefallen die insgesamt erfassten Stunden, 0 an einer nicht buchbaren Position
   */
  public record Positionszeile(
      long angebotPositionId,
      String bezeichnung,
      Einheit einheit,
      BigDecimal angeboten,
      BigDecimal abgerechnet,
      BigDecimal offen,
      BigDecimal ueberschreitung,
      boolean buchbar,
      BigDecimal angefallen) {

    static Positionszeile of(final Positionsabrechnung zeile) {
      final Positionsstand stand = zeile.stand();
      final Angebotsposition position = stand.position();
      return new Positionszeile(
          position.requireId(),
          position.bezeichnung(),
          position.einheit(),
          stand.angeboten(),
          stand.abgerechnet(),
          stand.offen(),
          stand.ueberschreitung(),
          zeile.buchbar(),
          zeile.angefallen());
    }
  }

  /**
   * Eine Rechnung dieses Angebots.
   *
   * @param id Kennung der Rechnung
   * @param nummer die Rechnungsnummer, oder {@code null} im Entwurf
   * @param rechnungDatum Datum der Rechnung
   * @param brutto der Bruttobetrag, gerechnet
   * @param zustand Entwurf oder gestellt
   */
  public record Rechnungszeile(
      long id,
      @Nullable String nummer,
      LocalDate rechnungDatum,
      BigDecimal brutto,
      Rechnungszustand zustand) {

    static Rechnungszeile of(final RechnungMitBetrag zeile) {
      final Rechnung rechnung = zeile.rechnung();
      return new Rechnungszeile(
          rechnung.requireId(),
          rechnung.nummer(),
          rechnung.rechnungDatum(),
          zeile.brutto(),
          rechnung.zustand());
    }
  }
}
