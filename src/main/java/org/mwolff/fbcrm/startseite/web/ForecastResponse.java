package org.mwolff.fbcrm.startseite.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.mwolff.fbcrm.angebot.application.AngebotMitFirma;
import org.mwolff.fbcrm.startseite.application.Forecast;
import org.mwolff.fbcrm.startseite.application.Forecastjahr;
import org.mwolff.fbcrm.startseite.application.Forecastposition;

/**
 * Der Forecast der Startseite, wie die Ansicht ihn zeigt (#302, Kriterien 1, 5 und 6; Issue #308).
 *
 * <p>Die Antwort traegt fertig, was die Ansicht darstellt: je Jahr die drei Betraege und die
 * Positionen darunter. Auch die Summe steht als Feld darin, obwohl sie gerechnet ist — die
 * Oberflaeche rechnet nichts (#216).
 *
 * <p><b>Das Jahr ist Text</b> {@code JJJJ} (Plan #303, E9): Ein {@code Year} schriebe Jackson als
 * Zahl, und die Startseite fuehrt Jahre schon in {@link StartseiteResponse} als Text.
 *
 * <p><b>Von Angebot und Position kommen fuenf Angaben mit, nicht beide ganz</b> — aus demselben
 * Grund wie bei {@link StartseiteResponse.Angebotszeile}: Die Zeile zeigt Firma, Angebot, Position
 * und Restbetrag, und die Kennung ist der Weg zum Angebot.
 *
 * @param jahre die Jahre mit mindestens einer offenen Position, aeltestes zuerst; leer ohne jede
 *     offene Position
 */
public record ForecastResponse(List<Jahreszeile> jahre) {

  /** Derselbe Forecast in der Sprache der Schnittstelle. */
  static ForecastResponse of(final Forecast forecast) {
    return new ForecastResponse(forecast.jahre().stream().map(Jahreszeile::of).toList());
  }

  /**
   * Ein Jahr des Forecasts (#302, Kriterium 1).
   *
   * @param jahr das Jahr der Leistung als Text {@code JJJJ} (E9)
   * @param zugesagtNetto die Restbetraege aus Angeboten auf „bestellt", „erledigt" und
   *     „abgerechnet", netto
   * @param angebotenNetto die Restbetraege aus Angeboten auf „angelegt" und „abgegeben", netto
   * @param summeNetto die Summe beider, netto
   * @param positionen die offenen Positionen dieses Jahres
   */
  public record Jahreszeile(
      String jahr,
      BigDecimal zugesagtNetto,
      BigDecimal angebotenNetto,
      BigDecimal summeNetto,
      List<Positionszeile> positionen) {

    static Jahreszeile of(final Forecastjahr jahr) {
      return new Jahreszeile(
          jahr.jahr().toString(),
          jahr.zugesagt(),
          jahr.angeboten(),
          jahr.summe(),
          jahr.positionen().stream().map(Positionszeile::of).toList());
    }
  }

  /**
   * Eine offene Position unter einem Jahr (#302, Kriterium 5).
   *
   * @param angebotId Kennung des Angebots — der Weg dorthin
   * @param firmaName Name der Firma, an die das Angebot geht
   * @param angebotDatum Datum des Angebots
   * @param bezeichnung Bezeichnung der Position
   * @param netto ihr Restbetrag, netto und auf den Cent
   */
  public record Positionszeile(
      long angebotId,
      String firmaName,
      LocalDate angebotDatum,
      String bezeichnung,
      BigDecimal netto) {

    static Positionszeile of(final Forecastposition position) {
      final AngebotMitFirma zeile = position.angebot();
      return new Positionszeile(
          zeile.angebot().requireId(),
          zeile.firmaName(),
          zeile.angebot().angebotDatum(),
          position.position().bezeichnung(),
          position.netto());
    }
  }
}
