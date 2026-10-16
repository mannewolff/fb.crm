package org.mwolff.fbcrm.startseite.application;

import java.math.BigDecimal;
import java.time.Year;
import java.util.List;

/**
 * Eine Zeile des Forecasts: ein Jahr mit seinen offenen Restbetraegen (#302, Kriterium 1).
 *
 * <p>„Zugesagt" und „angeboten" sind Felder, die Summe beider nicht: Sie ist ein gerechneter Wert
 * ({@link #summe()}) und kein zweiter Wahrheitsort, der beim Nachziehen auseinanderlaufen koennte.
 * Eine Gesamtsumme ueber alle Jahre gibt es nicht (Plan #303, E13).
 *
 * @param jahr das Jahr der Leistung
 * @param zugesagt die Summe der Restbetraege aus Angeboten auf „bestellt", „erledigt" und
 *     „abgerechnet", netto
 * @param angeboten die Summe der Restbetraege aus Angeboten auf „angelegt" und „abgegeben", netto
 * @param positionen die Positionen dieses Jahres mit ihrem Restbetrag
 */
public record Forecastjahr(
    Year jahr, BigDecimal zugesagt, BigDecimal angeboten, List<Forecastposition> positionen) {

  /** Nimmt die Positionen als Kopie: Der Aufrufer darf seine Liste danach weiterverwenden. */
  public Forecastjahr {
    positionen = List.copyOf(positionen);
  }

  /** Die Summe beider Spalten, netto. */
  public BigDecimal summe() {
    return zugesagt.add(angeboten);
  }
}
