package org.mwolff.fbcrm.rechnung.application;

import java.math.BigDecimal;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.common.Einheit;

/**
 * Eine Zeile der Entwurfsmaske: eine Position des Angebots mit ihrem Stand (#160, Kriterium 4).
 *
 * <p>Die Maske zeigt <b>jede</b> Position des Angebots, auch eine, die dieser Entwurf nicht traegt
 * — dann mit {@code jetzt} gleich 0. So sieht der Freiberufler beim Wiederoeffnen, was er beim
 * ersten Mal weggelassen hat (Plan #169, E5).
 *
 * <p>{@code abgerechnet} und {@code offen} rechnen <b>ohne</b> diese Rechnung: Ihre eigene Menge
 * ist keine fremde Abrechnung, sondern das, was gerade eingetragen wird. Die {@code
 * ueberschreitung} dagegen zaehlt sie mit — sie ist der Hinweis an der Position, dass mit dieser
 * Menge zusammen mehr abgerechnet wird als angeboten war (Kriterium 8).
 *
 * <p><b>Text und Einzelpreis stehen neben der Angebotsposition</b> und werden nicht aus ihr
 * gelesen: Traegt der Entwurf die Position bereits, gelten die Werte, die er beim Anlegen
 * festgehalten hat — eine spaetere Preisaenderung oder Umbenennung am Angebot erreicht sie nicht
 * (Kriterium 9, Frage 15). Nur eine Position, die der Entwurf noch nicht traegt, zeigt Text und
 * Preis des Angebots von jetzt; dieselbe Regel wendet {@code RechnungAendernUseCase} beim Schreiben
 * an. Die Maske muss denselben Preis zeigen, mit dem gerechnet wird.
 *
 * @param position die Angebotsposition, um die es geht
 * @param bezeichnung die Leistung, wie diese Rechnung sie nennt
 * @param einzelpreis der Netto-Preis je Einheit, mit dem diese Rechnung rechnet
 * @param abgerechnet die Menge aus allen <b>anderen</b> Rechnungen dieses Angebots
 * @param offen die noch offene Menge ohne diese Rechnung, nie kleiner als 0
 * @param ueberschreitung was mit der Menge dieser Rechnung zusammen zu viel waere, sonst 0
 * @param jetzt die Menge, die diese Rechnung abrechnet — 0, wenn sie die Position nicht traegt
 */
public record Abrechnungszeile(
    Angebotsposition position,
    String bezeichnung,
    BigDecimal einzelpreis,
    BigDecimal abgerechnet,
    BigDecimal offen,
    BigDecimal ueberschreitung,
    BigDecimal jetzt) {

  /** Die angebotene Menge — die Menge am Angebot. */
  public BigDecimal angeboten() {
    return position.menge();
  }

  /** Die Einheit der Menge; sie ist an der Rechnung nicht aenderbar und kommt vom Angebot. */
  public Einheit einheit() {
    return position.einheit();
  }
}
