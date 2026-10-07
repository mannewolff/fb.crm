package org.mwolff.fbcrm.jahresabschluss.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Year;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.mwolff.fbcrm.angebot.application.AngebotMitFirma;
import org.mwolff.fbcrm.angebot.application.AngeboteUebersichtUseCase;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.common.Geldrechnung;
import org.mwolff.fbcrm.common.Geschaeftszone;
import org.mwolff.fbcrm.rechnung.application.GestellteRechnung;
import org.mwolff.fbcrm.rechnung.application.Rechnungsauskunft;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Der Jahresabschluss (#287; Plan #288, E1): die Uebersicht aller Jahre mit Daten.
 *
 * <p><b>Zwei Auskuenfte, je einmal gefragt.</b> Die gestellten Rechnungen kommen einzeln aus {@link
 * Rechnungsauskunft#gestellteRechnungen()} (E4), die Angebote mit allen Status aus {@link
 * AngeboteUebersichtUseCase#angebote(Optional)}. Je Jahr zu fragen waere die Abfragelawine, die
 * diese Tueren gerade vermeiden. Dass dabei Angebote und Firmen zweimal geladen werden — einmal
 * fuer den Firmennamen an der Rechnung, einmal hier fuer die Angebotsbilanz —, ist der Preis
 * dafuer, dass der Weg zum Firmennamen im Modul {@code rechnung} bleibt (E6).
 *
 * <p><b>Welche Jahre erscheinen</b> (#287, Kriterium 1): jedes mit mindestens einer gestellten
 * Rechnung oder einem abgegebenen Angebot, das juengste zuerst (E21). Fuer das laufende Jahr gilt
 * dieselbe Bedingung — ohne eigene Daten erscheint es nicht. Ein Angebot gehoert zum Jahr seines
 * Angebotsdatums, eine Rechnung zum Jahr ihres Rechnungsdatums.
 *
 * <p><b>Welches Jahr noch laeuft, entscheidet der Server</b> an der injizierten {@link Clock} in
 * der {@link Geschaeftszone} (E19): Am 1. Januar um 00:30 Ortszeit ist am Nullmeridian noch das
 * alte Jahr.
 *
 * <p><b>Gerechnet wird nicht hier</b>, wo es die Regel schon gibt: Die Einnahmen sind die Summe der
 * schon je Rechnung gerundeten Nettobetraege nach {@link Geldrechnung}; die Annahmequote rundet
 * {@link Quote}, und welche Angebote abgegeben und angenommen sind, sagt {@link Angebotsblick}.
 */
@Service
@Transactional(readOnly = true)
public class JahresabschlussUseCase {

  private final Rechnungsauskunft rechnungen;
  private final AngeboteUebersichtUseCase angebote;
  private final Clock clock;

  JahresabschlussUseCase(
      final Rechnungsauskunft rechnungen,
      final AngeboteUebersichtUseCase angebote,
      final Clock clock) {
    this.rechnungen = rechnungen;
    this.angebote = angebote;
    this.clock = clock;
  }

  /**
   * Die Uebersicht aller Jahre mit Daten.
   *
   * @return je Jahr mit mindestens einer gestellten Rechnung oder einem abgegebenen Angebot seine
   *     Zeile, das juengste zuerst; leer, wenn es kein solches Jahr gibt
   */
  public List<Jahreszeile> jahre() {
    final Year laufend = Year.now(clock.withZone(Geschaeftszone.ZONE));
    final Map<Year, List<GestellteRechnung>> rechnungenJeJahr =
        rechnungen.gestellteRechnungen().stream()
            .collect(Collectors.groupingBy(rechnung -> Year.from(rechnung.rechnungDatum())));
    final Map<Year, List<Angebot>> abgegebeneJeJahr =
        angebote.angebote(Optional.empty()).stream()
            .map(AngebotMitFirma::angebot)
            .filter(angebot -> Angebotsblick.abgegeben(angebot.status()))
            .collect(Collectors.groupingBy(Angebotsblick::jahr));
    final SortedSet<Year> jahre = new TreeSet<>(Comparator.reverseOrder());
    jahre.addAll(rechnungenJeJahr.keySet());
    jahre.addAll(abgegebeneJeJahr.keySet());
    return jahre.stream()
        .map(
            jahr ->
                zeile(
                    jahr,
                    jahr.equals(laufend),
                    rechnungenJeJahr.getOrDefault(jahr, List.of()),
                    abgegebeneJeJahr.getOrDefault(jahr, List.of())))
        .toList();
  }

  /* Die drei Hauptzahlen eines Jahres aus seinen Rechnungen und seinen abgegebenen Angeboten. */
  private static Jahreszeile zeile(
      final Year jahr,
      final boolean laeuftNoch,
      final List<GestellteRechnung> rechnungen,
      final List<Angebot> abgegeben) {
    final long angenommen =
        abgegeben.stream().filter(angebot -> Angebotsblick.angenommen(angebot.status())).count();
    return new Jahreszeile(
        jahr,
        laeuftNoch,
        Geldrechnung.summe(rechnungen.stream().map(GestellteRechnung::netto)),
        rechnungen.size(),
        Quote.prozent(BigDecimal.valueOf(angenommen), BigDecimal.valueOf(abgegeben.size())));
  }
}
