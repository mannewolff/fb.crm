package org.mwolff.fbcrm.startseite.application;

import java.util.List;

/**
 * Der Forecast der Startseite: was aus Angeboten noch an Umsatz bevorsteht, je Jahr der Leistung
 * (#302).
 *
 * <p>Der Stand von heute und kein Zeitraum (Kriterium 7). Ein Jahr ohne offene Position steht nicht
 * darin (Kriterium 6) — ohne jede offene Position ist die Liste leer.
 *
 * @param jahre die Jahre mit mindestens einer offenen Position, aeltestes zuerst (Plan #303, E6)
 */
public record Forecast(List<Forecastjahr> jahre) {

  /** Nimmt die Jahre als Kopie: Der Aufrufer darf seine Liste danach weiterverwenden. */
  public Forecast {
    jahre = List.copyOf(jahre);
  }
}
