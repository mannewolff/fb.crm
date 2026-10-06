package org.mwolff.fbcrm.rechnung.application;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.mwolff.fbcrm.rechnung.domain.Abrechnungsstand;
import org.mwolff.fbcrm.rechnung.domain.NachgetrageneRechnung;
import org.mwolff.fbcrm.rechnung.domain.NachgetrageneRechnungRepository;
import org.mwolff.fbcrm.rechnung.domain.Rechnung;
import org.mwolff.fbcrm.rechnung.domain.RechnungRepository;
import org.mwolff.fbcrm.rechnung.domain.RechnungseinstellungenRepository;
import org.mwolff.fbcrm.rechnung.domain.Rechnungsposition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Was die Rechnung anderen Modulen ueber ihre gestellten Rechnungen sagt (Plan #208, E5).
 *
 * <p><b>Die eine Tuer nach draussen.</b> Die Startseite (#206) braucht zweierlei: die Summe der
 * gestellten Rechnungen je Monat (Kriterium 7; Plan #274, E4) und die schon abgerechneten Mengen je
 * Angebotsposition (Kriterium 5). Beides geht ueber diese Klasse und nicht ueber {@link
 * RechnungRepository}: Der Port gehoert diesem Modul, und ein fremdes Modul, das ihn selbst
 * aufruft, muesste den Zustand einer Rechnung, den geltenden Steuersatz und die Rundungsregel
 * kennen.
 *
 * <p><b>Nur gestellte Rechnungen</b> — anders als {@link Abrechnungsstand}, der Entwuerfe
 * mitzaehlt, damit ein zweiter Entwurf dieselbe Menge nicht noch einmal als offen zeigt (#160,
 * Kriterium 6). Hier geht es um das Gegenteil: Was abgerechnet <b>ist</b>, ist das, was draussen
 * ist (#206, Antwort 6). Gefragt wird dafuer {@link
 * org.mwolff.fbcrm.rechnung.domain.Rechnungszustand#istGestellt()}: Eine bezahlte oder
 * abgeschriebene Rechnung ist draussen und bleibt in beiden Antworten (Issue #253) — sie aus der
 * Monatsabrechnung fallen zu lassen hiesse, den Umsatz des Monats mit dem Zahlungseingang zu
 * verwechseln, und ihre Mengen freizugeben zeigte abgerechnete Leistung wieder als offen.
 *
 * <p><b>Nachgetragene Rechnungen zaehlen in den Monat</b> ihres Rechnungsdatums, mit Netto und
 * Brutto wie erfasst und jede als eine Rechnung (Plan #259, E20; #254, Kriterium 9). Jeder Zustand
 * zaehlt, denn eine nachgetragene Rechnung ist nie Entwurf. Die Mengen je Angebotsposition beruehrt
 * sie nicht: Sie gehoert zu keinem Angebot (Kriterium 11).
 *
 * <p><b>Ein Durchlauf, zwei Antworten.</b> {@link #gestellte()} liest {@link
 * RechnungRepository#findAlle()} genau einmal und rechnet beides daraus; zwei Methoden waeren zwei
 * Zuege durch dieselben Daten (Plan #208, E5).
 *
 * <p><b>Der Monat ist der des Rechnungsdatums</b> und nicht der des Stellens (#206, Antwort 4). Die
 * Betraege werden je Monat verdichtet und nicht gegen einen gefragten Zeitraum gefiltert: Welchen
 * Monat oder welches Jahr sie braucht, entscheidet die Startseite, denn ihr Zeitraum ist kein
 * Begriff der Rechnung (Plan #274, E4). Die Mengen zaehlen ohne Monat: Eine Rechnung haelt nicht
 * fest, aus welchem Monat ihre Stunden stammen (Antwort 2).
 */
@Service
@Transactional(readOnly = true)
public class Rechnungsauskunft {

  private final RechnungRepository bestand;
  private final NachgetrageneRechnungRepository nachtraege;
  private final RechnungseinstellungenRepository einstellungen;

  Rechnungsauskunft(
      final RechnungRepository rechnungen,
      final NachgetrageneRechnungRepository nachtraege,
      final RechnungseinstellungenRepository einstellungen) {
    this.bestand = rechnungen;
    this.nachtraege = nachtraege;
    this.einstellungen = einstellungen;
  }

  /**
   * Die gestellten Rechnungen: die Abrechnung je Monat und die Mengen je Angebotsposition.
   *
   * <p>Jede Rechnung geht mit Netto, Brutto und der Anzahl 1 in den Monat ihres Rechnungsdatums
   * ein; die Abrechnung eines Monats ist die {@link Monatsabrechnung#summe Summe} seiner
   * Rechnungen. Die nachgetragenen Rechnungen gehen nur in die Abrechnung je Monat ein.
   *
   * <p>Brutto entsteht je Rechnung mit dem Satz, der fuer sie gilt ({@link GeltenderSteuersatz}) —
   * demselben, mit dem die Rechnungsliste rechnet. Darum wird der Satz der aktuellen Einstellungen
   * einmal je Aufruf gelesen, auch wenn ihn im Regelfall keine gestellte Rechnung braucht: Eine
   * Rechnung ohne eigenen Satz waere sonst nicht zu rechnen (#206, Kriterium 9).
   *
   * @return je Monat mit mindestens einer gestellten Rechnung deren Abrechnung, und die
   *     abgerechneten Mengen ueber alle Monate
   */
  public Gestellte gestellte() {
    final BigDecimal aktuellerSatz = einstellungen.lies().steuersatz();
    final Map<YearMonth, List<Monatsabrechnung>> rechnungenJeMonat = new HashMap<>();
    final Map<Long, BigDecimal> mengen = new HashMap<>();
    for (final Rechnung rechnung : bestand.findAlle()) {
      if (!rechnung.zustand().istGestellt()) {
        continue;
      }
      rechnungenJeMonat
          .computeIfAbsent(YearMonth.from(rechnung.rechnungDatum()), monat -> new ArrayList<>())
          .add(
              new Monatsabrechnung(
                  rechnung.netto(),
                  rechnung.brutto(GeltenderSteuersatz.fuer(rechnung, aktuellerSatz)),
                  1));
      for (final Rechnungsposition position : rechnung.positionen()) {
        mengen.merge(position.angebotPositionId(), position.menge(), BigDecimal::add);
      }
    }
    for (final NachgetrageneRechnung rechnung : nachtraege.findAlle()) {
      rechnungenJeMonat
          .computeIfAbsent(YearMonth.from(rechnung.rechnungDatum()), monat -> new ArrayList<>())
          .add(new Monatsabrechnung(rechnung.netto(), rechnung.brutto(), 1));
    }
    final Map<YearMonth, Monatsabrechnung> jeMonat = new HashMap<>();
    rechnungenJeMonat.forEach(
        (monat, rechnungen) -> jeMonat.put(monat, Monatsabrechnung.summe(rechnungen.stream())));
    return new Gestellte(jeMonat, mengen);
  }
}
