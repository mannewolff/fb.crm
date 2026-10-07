package org.mwolff.fbcrm.rechnung.application;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Map;

/**
 * Die beiden Auskuenfte ueber die gestellten Rechnungen aus einem Durchlauf (Plan #208, E5).
 *
 * <p>Sie stehen zusammen in einer Antwort, weil sie dieselben Rechnungen lesen: Die Startseite
 * braucht beides — die Abrechnung je Monat fuer „Abgerechnet" (#206, Kriterium 7) und die Mengen
 * fuer „Noch nicht abgerechnet" (Kriterium 5). Zwei Aufrufe waeren zwei Durchlaeufe durch denselben
 * Bestand.
 *
 * <p><b>Beide Teile reichen ueber alle Monate</b>, aber verschieden: {@link #jeMonat()} haelt die
 * Abrechnung je Monat des Rechnungsdatums getrennt, und welchen Zeitraum sie daraus braucht,
 * entscheidet der Aufrufer (Plan #274, E4). {@link #mengenJePosition()} kennt keinen Monat — eine
 * Rechnung haelt nicht fest, aus welchem Monat ihre Stunden stammen (#206, Antwort 2).
 *
 * @param jeMonat je Monat Netto, Brutto und Anzahl der in ihm gestellten Rechnungen samt dem, was
 *     davon noch offen ist (Issue #284); ein Monat ohne gestellte Rechnung fehlt darin
 * @param mengenJePosition je Angebotsposition die auf gestellten Rechnungen abgerechnete Menge,
 *     ueber alle Monate; eine Position ohne Rechnungszeile fehlt darin
 */
public record Gestellte(
    Map<YearMonth, Monatsabrechnung> jeMonat, Map<Long, BigDecimal> mengenJePosition) {

  /** Nimmt die Abbildungen als Kopie: Der Aufrufer darf seine Maps danach weiterverwenden. */
  public Gestellte {
    jeMonat = Map.copyOf(jeMonat);
    mengenJePosition = Map.copyOf(mengenJePosition);
  }
}
