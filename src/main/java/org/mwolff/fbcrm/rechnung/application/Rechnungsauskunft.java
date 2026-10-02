package org.mwolff.fbcrm.rechnung.application;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.mwolff.fbcrm.common.Geldrechnung;
import org.mwolff.fbcrm.rechnung.domain.Abrechnungsstand;
import org.mwolff.fbcrm.rechnung.domain.Rechnung;
import org.mwolff.fbcrm.rechnung.domain.RechnungRepository;
import org.mwolff.fbcrm.rechnung.domain.RechnungseinstellungenRepository;
import org.mwolff.fbcrm.rechnung.domain.Rechnungsposition;
import org.mwolff.fbcrm.rechnung.domain.Rechnungszustand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Was die Rechnung anderen Modulen ueber ihre gestellten Rechnungen sagt (Plan #208, E5).
 *
 * <p><b>Die eine Tuer nach draussen.</b> Die Startseite (#206) braucht zweierlei: die Summe der im
 * Monat gestellten Rechnungen (Kriterium 7) und die schon abgerechneten Mengen je Angebotsposition
 * (Kriterium 5). Beides geht ueber diese Klasse und nicht ueber {@link RechnungRepository}: Der
 * Port gehoert diesem Modul, und ein fremdes Modul, das ihn selbst aufruft, muesste den Zustand
 * einer Rechnung, den geltenden Steuersatz und die Rundungsregel kennen.
 *
 * <p><b>Nur gestellte Rechnungen</b> — anders als {@link Abrechnungsstand}, der Entwuerfe
 * mitzaehlt, damit ein zweiter Entwurf dieselbe Menge nicht noch einmal als offen zeigt (#160,
 * Kriterium 6). Hier geht es um das Gegenteil: Was abgerechnet <b>ist</b>, ist das, was draussen
 * ist (#206, Antwort 6).
 *
 * <p><b>Ein Durchlauf, zwei Antworten.</b> {@link #gestellte(YearMonth)} liest {@link
 * RechnungRepository#findAlle()} genau einmal und rechnet beides daraus; zwei Methoden waeren zwei
 * Zuege durch dieselben Daten (Plan #208, E5).
 *
 * <p><b>Der Monat ist der des Rechnungsdatums</b> und nicht der des Stellens (#206, Antwort 4). Die
 * Mengen dagegen zaehlen ueber alle Monate: Eine Rechnung haelt nicht fest, aus welchem Monat ihre
 * Stunden stammen (Antwort 2).
 */
@Service
@Transactional(readOnly = true)
public class Rechnungsauskunft {

  private final RechnungRepository bestand;
  private final RechnungseinstellungenRepository einstellungen;

  Rechnungsauskunft(
      final RechnungRepository rechnungen, final RechnungseinstellungenRepository einstellungen) {
    this.bestand = rechnungen;
    this.einstellungen = einstellungen;
  }

  /**
   * Die gestellten Rechnungen: die Abrechnung des Monats und die Mengen je Angebotsposition.
   *
   * <p>Brutto entsteht je Rechnung mit dem Satz, der fuer sie gilt ({@link GeltenderSteuersatz}) —
   * demselben, mit dem die Rechnungsliste rechnet. Darum wird der Satz der aktuellen Einstellungen
   * einmal je Aufruf gelesen, auch wenn ihn im Regelfall keine gestellte Rechnung braucht: Eine
   * Rechnung ohne eigenen Satz waere sonst nicht zu rechnen (#206, Kriterium 9).
   *
   * @param monat der Monat, dessen Rechnungen in die Monatsabrechnung eingehen
   * @return die Monatsabrechnung dieses Monats und die abgerechneten Mengen ueber alle Monate
   */
  public Gestellte gestellte(final YearMonth monat) {
    final BigDecimal aktuellerSatz = einstellungen.lies().steuersatz();
    final List<BigDecimal> nettoWerte = new ArrayList<>();
    final List<BigDecimal> bruttoWerte = new ArrayList<>();
    final Map<Long, BigDecimal> mengen = new HashMap<>();
    for (final Rechnung rechnung : bestand.findAlle()) {
      if (rechnung.zustand() != Rechnungszustand.GESTELLT) {
        continue;
      }
      if (monat.equals(YearMonth.from(rechnung.rechnungDatum()))) {
        nettoWerte.add(rechnung.netto());
        bruttoWerte.add(rechnung.brutto(GeltenderSteuersatz.fuer(rechnung, aktuellerSatz)));
      }
      for (final Rechnungsposition position : rechnung.positionen()) {
        mengen.merge(position.angebotPositionId(), position.menge(), BigDecimal::add);
      }
    }
    return new Gestellte(
        new Monatsabrechnung(
            Geldrechnung.summe(nettoWerte.stream()),
            Geldrechnung.summe(bruttoWerte.stream()),
            nettoWerte.size()),
        mengen);
  }
}
