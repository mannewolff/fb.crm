package org.mwolff.fbcrm.rechnung.application;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.mwolff.fbcrm.angebot.application.AngebotNichtGefunden;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.firma.application.FirmaNichtGefunden;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
import org.mwolff.fbcrm.rechnung.domain.Abrechnungsstand;
import org.mwolff.fbcrm.rechnung.domain.NachgetrageneRechnung;
import org.mwolff.fbcrm.rechnung.domain.NachgetrageneRechnungRepository;
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
 * <p><b>Die eine Tuer nach draussen.</b> Die Startseite (#206) braucht dreierlei: die Summe der
 * gestellten Rechnungen je Monat (Kriterium 7; Plan #274, E4), die schon abgerechneten Mengen je
 * Angebotsposition (Kriterium 5) und die noch offenen Rechnungen selbst (Issue #285). Alles geht
 * ueber diese Klasse und nicht ueber {@link RechnungRepository}: Der Port gehoert diesem Modul, und
 * ein fremdes Modul, das ihn selbst aufriefe, muesste den Zustand einer Rechnung, den geltenden
 * Steuersatz, die Rundungsregel und den Weg zum Firmennamen kennen.
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
 * <p><b>Was noch offen ist, steht an zwei Stellen</b>: als Betrag neben der Monatsabrechnung
 * ({@link Monatsabrechnung#offenNetto()}, Issue #284) und als Liste der Rechnungen selbst ({@link
 * Gestellte#offene()}, Issue #285). Dieselbe Rechnung zaehlt in den Umsatz ihres Monats und,
 * solange sie {@link org.mwolff.fbcrm.rechnung.domain.Rechnungszustand#istOffen() offen} ist, in
 * beide Formen des Offenen. Der Betrag wird je Monat gruppiert, die Liste nicht: Sie ist der Stand
 * von heute ueber alle Monate.
 *
 * <p><b>Nachgetragene Rechnungen zaehlen in den Monat</b> ihres Rechnungsdatums, mit Netto und
 * Brutto wie erfasst und jede als eine Rechnung (Plan #259, E20; #254, Kriterium 9). Jeder Zustand
 * zaehlt, denn eine nachgetragene Rechnung ist nie Entwurf. Die Mengen je Angebotsposition beruehrt
 * sie nicht: Sie gehoert zu keinem Angebot (Kriterium 11). In der Liste der offenen Rechnungen
 * steht sie neben der geschriebenen und nur durch ihre Art unterschieden.
 *
 * <p><b>Ein Durchlauf, drei Antworten.</b> {@link #gestellte()} liest {@link
 * RechnungRepository#findAlle()} genau einmal und rechnet alles daraus; drei Methoden waeren drei
 * Zuege durch dieselben Daten (Plan #208, E5).
 *
 * <p><b>Die Zeilen der offenen Rechnungen entstehen daneben</b> ({@link OffenePosten}): Dort steht,
 * wie der Firmenname dazukommt und warum er in zwei Zuegen kommt.
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
  private final AngebotRepository angebote;
  private final FirmaRepository firmen;

  Rechnungsauskunft(
      final RechnungRepository rechnungen,
      final NachgetrageneRechnungRepository nachtraege,
      final RechnungseinstellungenRepository einstellungen,
      final AngebotRepository angebote,
      final FirmaRepository firmen) {
    this.bestand = rechnungen;
    this.nachtraege = nachtraege;
    this.einstellungen = einstellungen;
    this.angebote = angebote;
    this.firmen = firmen;
  }

  /**
   * Die gestellten Rechnungen: die Abrechnung je Monat, die Mengen je Angebotsposition und die noch
   * offenen Rechnungen.
   *
   * <p>Jede Rechnung geht mit Netto, Brutto und der Anzahl 1 in den Monat ihres Rechnungsdatums
   * ein; die Abrechnung eines Monats ist die {@link Monatsabrechnung#summe Summe} seiner
   * Rechnungen. Die nachgetragenen Rechnungen gehen nur in die Abrechnung je Monat ein.
   *
   * <p>Ist eine Rechnung noch offen, traegt sie dasselbe Netto und eine 1 zusaetzlich in die
   * offenen Felder (Issue #284) und steht mit ihren Eckdaten in der Liste der offenen Rechnungen
   * (Issue #285). Eine bezahlte oder abgeschriebene traegt dort nichts bei und bleibt in Netto,
   * Brutto und Anzahl unveraendert stehen.
   *
   * <p>Brutto entsteht je Rechnung mit dem Satz, der fuer sie gilt ({@link GeltenderSteuersatz}) —
   * demselben, mit dem die Rechnungsliste rechnet. Darum wird der Satz der aktuellen Einstellungen
   * einmal je Aufruf gelesen, auch wenn ihn im Regelfall keine gestellte Rechnung braucht: Eine
   * Rechnung ohne eigenen Satz waere sonst nicht zu rechnen (#206, Kriterium 9).
   *
   * @return je Monat mit mindestens einer gestellten Rechnung deren Abrechnung samt offenem Anteil,
   *     die abgerechneten Mengen ueber alle Monate und die offenen Rechnungen, aelteste zuerst
   * @throws AngebotNichtGefunden wenn es das Angebot einer offenen Rechnung nicht gibt
   * @throws FirmaNichtGefunden wenn es die Firma eines solchen Angebots oder einer offenen
   *     nachgetragenen Rechnung nicht gibt — beides waere ein Widerspruch im Bestand, denn weder
   *     Angebote noch Firmen werden geloescht
   */
  public Gestellte gestellte() {
    final BigDecimal aktuellerSatz = einstellungen.lies().steuersatz();
    final Map<YearMonth, List<Monatsabrechnung>> rechnungenJeMonat = new HashMap<>();
    final Map<Long, BigDecimal> mengen = new HashMap<>();
    final List<Rechnung> offeneEigene = new ArrayList<>();
    final List<NachgetrageneRechnung> offeneNachtraege = new ArrayList<>();
    for (final Rechnung rechnung : bestand.findAlle()) {
      if (!rechnung.zustand().istGestellt()) {
        continue;
      }
      rechnungenJeMonat
          .computeIfAbsent(YearMonth.from(rechnung.rechnungDatum()), monat -> new ArrayList<>())
          .add(
              alsAbrechnung(
                  rechnung.netto(),
                  rechnung.brutto(GeltenderSteuersatz.fuer(rechnung, aktuellerSatz)),
                  rechnung.zustand()));
      if (rechnung.zustand().istOffen()) {
        offeneEigene.add(rechnung);
      }
      for (final Rechnungsposition position : rechnung.positionen()) {
        mengen.merge(position.angebotPositionId(), position.menge(), BigDecimal::add);
      }
    }
    for (final NachgetrageneRechnung rechnung : nachtraege.findAlle()) {
      rechnungenJeMonat
          .computeIfAbsent(YearMonth.from(rechnung.rechnungDatum()), monat -> new ArrayList<>())
          .add(alsAbrechnung(rechnung.netto(), rechnung.brutto(), rechnung.zustand()));
      if (rechnung.zustand().istOffen()) {
        offeneNachtraege.add(rechnung);
      }
    }
    final Map<YearMonth, Monatsabrechnung> jeMonat = new HashMap<>();
    rechnungenJeMonat.forEach(
        (monat, rechnungen) -> jeMonat.put(monat, Monatsabrechnung.summe(rechnungen.stream())));
    return new Gestellte(
        jeMonat, mengen, OffenePosten.zeilen(offeneEigene, offeneNachtraege, angebote, firmen));
  }

  /*
   * Eine einzelne gestellte Rechnung als Abrechnung ueber einen Monat: ihre Betraege, die Anzahl 1
   * und — wenn sie noch offen ist — dasselbe Netto noch einmal als offener Posten (Issue #284).
   *
   * Die eine Stelle fuer beide Aggregate, die geschriebene Rechnung und den Nachtrag: Zwei
   * Abschriften desselben Dreisatzes liefen beim ersten Nachziehen auseinander, und ein Nachtrag,
   * der seinen Ausgang anders auswertete als eine Rechnung, waere in der Kennzahl nicht erklaerbar.
   *
   * Gefragt wird Rechnungszustand#istOffen() und nicht `== GESTELLT`: Was „offen" heisst, gehoert
   * zum Zustand und nicht zu seinen Lesern.
   */
  private static Monatsabrechnung alsAbrechnung(
      final BigDecimal netto, final BigDecimal brutto, final Rechnungszustand zustand) {
    final boolean offen = zustand.istOffen();
    return new Monatsabrechnung(netto, brutto, 1, offen ? netto : BigDecimal.ZERO, offen ? 1 : 0);
  }
}
