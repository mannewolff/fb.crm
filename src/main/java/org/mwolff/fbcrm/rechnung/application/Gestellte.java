package org.mwolff.fbcrm.rechnung.application;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/**
 * Die Auskuenfte ueber die gestellten Rechnungen aus einem Durchlauf (Plan #208, E5).
 *
 * <p>Sie stehen zusammen in einer Antwort, weil sie dieselben Rechnungen lesen: Die Startseite
 * braucht alle drei — die Abrechnung je Monat fuer „Abgerechnet" (#206, Kriterium 7), die Mengen
 * fuer „Noch nicht abgerechnet" (Kriterium 5) und die offenen Rechnungen fuer die Kachel „Offene
 * Rechnungen" (Issue #285). Drei Aufrufe waeren drei Durchlaeufe durch denselben Bestand.
 *
 * <p><b>Die drei Teile reichen ueber alle Monate</b>, aber verschieden: {@link #jeMonat()} haelt
 * die Abrechnung je Monat des Rechnungsdatums getrennt, und welchen Zeitraum sie daraus braucht,
 * entscheidet der Aufrufer (Plan #274, E4). {@link #mengenJePosition()} kennt keinen Monat — eine
 * Rechnung haelt nicht fest, aus welchem Monat ihre Stunden stammen (#206, Antwort 2). {@link
 * #offene()} ist der Stand von heute: Was offen ist, ist es unabhaengig vom Rechnungsdatum, und
 * eine Teilmenge nach Zeitraum waere eine andere Frage als „worauf fehlt noch Geld".
 *
 * @param jeMonat je Monat Netto, Brutto und Anzahl der in ihm gestellten Rechnungen samt dem, was
 *     davon noch offen ist (Issue #284); ein Monat ohne gestellte Rechnung fehlt darin
 * @param mengenJePosition je Angebotsposition die auf gestellten Rechnungen abgerechnete Menge,
 *     ueber alle Monate; eine Position ohne Rechnungszeile fehlt darin
 * @param offene die noch offenen Rechnungen beider Arten ueber alle Monate, aelteste zuerst (Issue
 *     #285); leer, wo nichts offen ist
 */
public record Gestellte(
    Map<YearMonth, Monatsabrechnung> jeMonat,
    Map<Long, BigDecimal> mengenJePosition,
    List<OffeneRechnung> offene) {

  /** Nimmt Abbildungen und Liste als Kopie: Der Aufrufer darf seine danach weiterverwenden. */
  public Gestellte {
    jeMonat = Map.copyOf(jeMonat);
    mengenJePosition = Map.copyOf(mengenJePosition);
    offene = List.copyOf(offene);
  }
}
