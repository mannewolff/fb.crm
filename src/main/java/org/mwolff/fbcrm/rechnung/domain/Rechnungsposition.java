package org.mwolff.fbcrm.rechnung.domain;

import java.math.BigDecimal;
import org.mwolff.fbcrm.common.Einheit;
import org.mwolff.fbcrm.common.Geldrechnung;

/**
 * Eine Position einer Rechnung — die Abrechnung genau einer Angebotsposition (Plan #169, E3).
 *
 * <p>Der Betrag wird gerechnet und nicht gespeichert (E5 am Angebot): {@link #betrag()} ist Menge
 * mal Einzelpreis nach der einen Regel in {@link Geldrechnung}, kaufmaennisch auf den Cent.
 * Dieselbe Regel rechnet das Angebot, damit eine Rechnung dessen Summe auf den Cent trifft.
 *
 * <p><b>Die Angebotsposition ist die Kennung.</b> Eine Rechnung fuehrt je Angebotsposition
 * hoechstens eine Zeile ({@code rechnung_position_je_angebotsposition}), und die Position bleibt
 * ihrer Angebotsposition zugeordnet, auch wenn das Angebot danach umgeordnet oder umbenannt wird
 * (Plan #169, E2). Eine eigene technische Kennung traegt der Record deshalb nicht — sie waere eine
 * zweite Wahrheit ueber dieselbe Zuordnung.
 *
 * <p>Den Platz in der Reihenfolge traegt die Position aus demselben Grund nicht wie am Angebot: Die
 * Reihenfolge ist die der Liste an der Rechnung, und der Bestand vergibt sie beim Schreiben
 * lueckenlos ab 1 (E24).
 *
 * <p>Bezeichnung, Einheit und Einzelpreis sind Kopien aus der Angebotsposition zum Zeitpunkt des
 * Anlegens und keine Verweise: Was abgerechnet wurde, darf sich nicht rueckwirkend aendern, wenn
 * das Angebot spaeter umbenannt oder umpreist wird (#160, Kriterium 9).
 *
 * @param angebotPositionId Kennung der abgerechneten Angebotsposition
 * @param bezeichnung die Leistung, wie sie auf der Rechnung steht
 * @param menge abgerechnete Menge in der angegebenen Einheit; groesser 0
 * @param einheit Einheit der Menge
 * @param einzelpreis Netto-Preis je Einheit, nicht negativ
 */
public record Rechnungsposition(
    long angebotPositionId,
    String bezeichnung,
    BigDecimal menge,
    Einheit einheit,
    BigDecimal einzelpreis) {

  /** Der Netto-Betrag der Position, kaufmaennisch auf den Cent gerundet. */
  public BigDecimal betrag() {
    return Geldrechnung.betrag(menge, einzelpreis);
  }
}
