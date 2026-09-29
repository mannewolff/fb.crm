package org.mwolff.fbcrm.angebot.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.mwolff.fbcrm.angebot.application.AngebotMitFirma;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;

/**
 * Die Uebersicht aller Angebote (Issue #127, Kriterium 8).
 *
 * <p>Ein Objekt mit einer Liste und kein nacktes Array, aus demselben Grund wie bei {@link
 * FirmaAngeboteResponse}.
 *
 * @param angebote die Angebote, neueste zuerst
 */
public record AngeboteUebersichtResponse(List<Zeile> angebote) {

  /** Die Sicht der Oberflaeche auf die Uebersicht. */
  static AngeboteUebersichtResponse of(final List<AngebotMitFirma> zeilen) {
    return new AngeboteUebersichtResponse(zeilen.stream().map(Zeile::of).toList());
  }

  /**
   * Eine Zeile der Uebersicht.
   *
   * @param id Kennung des Angebots
   * @param firmaId Kennung der Firma
   * @param firmaName Name der Firma
   * @param angebotDatum Datum des Angebots
   * @param status Status des Angebots
   * @param summe Netto-Summe, gerechnet und nicht gespeichert (E5)
   */
  public record Zeile(
      long id,
      long firmaId,
      String firmaName,
      LocalDate angebotDatum,
      Angebotsstatus status,
      BigDecimal summe) {

    static Zeile of(final AngebotMitFirma zeile) {
      final Angebot angebot = zeile.angebot();
      return new Zeile(
          angebot.requireId(),
          angebot.firmaId(),
          zeile.firmaName(),
          angebot.angebotDatum(),
          angebot.status(),
          angebot.summe());
    }
  }
}
