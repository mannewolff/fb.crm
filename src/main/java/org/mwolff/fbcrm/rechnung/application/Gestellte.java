package org.mwolff.fbcrm.rechnung.application;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Die beiden Auskuenfte ueber die gestellten Rechnungen aus einem Durchlauf (Plan #208, E5).
 *
 * <p>Sie stehen zusammen in einer Antwort, weil sie dieselben Rechnungen lesen: Die Startseite
 * braucht beides — die Monatsabrechnung fuer „Abgerechnet" (#206, Kriterium 7) und die Mengen fuer
 * „Noch nicht abgerechnet" (Kriterium 5). Zwei Aufrufe waeren zwei Durchlaeufe durch denselben
 * Bestand.
 *
 * <p><b>Die beiden Teile haben verschiedene Zeitraeume</b>, und das ist Absicht: {@link #imMonat()}
 * kennt nur den gefragten Monat, {@link #mengenJePosition()} alle Monate. Was schon abgerechnet
 * ist, haengt nicht am Monat — eine Rechnung haelt nicht fest, aus welchem Monat ihre Stunden
 * stammen (#206, Antwort 2).
 *
 * @param imMonat Netto, Brutto und Anzahl der im gefragten Monat gestellten Rechnungen
 * @param mengenJePosition je Angebotsposition die auf gestellten Rechnungen abgerechnete Menge,
 *     ueber alle Monate; eine Position ohne Rechnungszeile fehlt darin
 */
public record Gestellte(Monatsabrechnung imMonat, Map<Long, BigDecimal> mengenJePosition) {

  /** Nimmt die Abbildung als Kopie: Der Aufrufer darf seine Map danach weiterverwenden. */
  public Gestellte {
    mengenJePosition = Map.copyOf(mengenJePosition);
  }
}
