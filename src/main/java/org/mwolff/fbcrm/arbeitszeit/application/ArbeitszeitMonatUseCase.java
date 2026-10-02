package org.mwolff.fbcrm.arbeitszeit.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.mwolff.fbcrm.angebot.application.AngebotNichtGefunden;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.arbeitszeit.domain.Zeiteintrag;
import org.mwolff.fbcrm.arbeitszeit.domain.ZeiteintragRepository;
import org.mwolff.fbcrm.common.Geschaeftszone;
import org.mwolff.fbcrm.firma.application.FirmaNichtGefunden;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Die Monatsliste der Ansicht „Arbeitszeit" (Issue #193, Kriterium 5; Plan #194, A20).
 *
 * <p><b>Die Reihenfolge liest sich wie ein Kalender:</b> Tage aufsteigend, darin die Eintraege nach
 * ihrem Beginn aufsteigend. Sortiert wird hier und nicht im Bestand — die Ordnung ist eine Aussage
 * der Ansicht und keine Eigenschaft der Zeilen (dieselbe Begruendung wie {@code
 * angebot.application.Angebotsreihenfolge}).
 *
 * <p><b>Drei Zuege in den Bestand und keiner je Zeile:</b> die Eintraege des Monats, die Angebote
 * und die Namen aller beteiligten Firmen auf einmal ({@link Buchungspositionen}). Ein leerer Monat
 * fragt gar nicht weiter.
 *
 * <p>Welcher Monat laeuft, entscheidet die injizierte {@link Clock} in der {@link Geschaeftszone}
 * (E4): Am 1. November um 00:30 Ortszeit ist am Nullmeridian noch Oktober, und die Ansicht zeigte
 * sonst den falschen Monat.
 */
@Service
@Transactional(readOnly = true)
public class ArbeitszeitMonatUseCase {

  private final ZeiteintragRepository zeiten;
  private final AngebotRepository angebote;
  private final FirmaRepository firmen;
  private final Clock clock;

  ArbeitszeitMonatUseCase(
      final ZeiteintragRepository zeiten,
      final AngebotRepository angebote,
      final FirmaRepository firmen,
      final Clock clock) {
    this.zeiten = zeiten;
    this.angebote = angebote;
    this.firmen = firmen;
    this.clock = clock;
  }

  /**
   * Die Eintraege eines Monats nach Tagen, jeder mit seiner Summe, dazu die des Monats.
   *
   * <p>Die Monatssumme kommt zusaetzlich aufgeteilt in den Teil fuer Kunden und den internen (Issue
   * #230, Kriterium 10 von #207). Beide entstehen wie die Gesamtsumme aus addierten Minuten; die
   * Tagessummen bleiben ungeteilt.
   *
   * @param gewaehlt der gesuchte Monat, oder leer fuer den laufenden
   * @throws AngebotNichtGefunden wenn ein gebuchtes Angebot im Bestand fehlt
   * @throws FirmaNichtGefunden wenn die Firma eines beteiligten Angebots fehlt
   */
  public Arbeitsmonat monat(final Optional<YearMonth> gewaehlt) {
    final YearMonth monat =
        gewaehlt.orElseGet(() -> YearMonth.now(clock.withZone(Geschaeftszone.ZONE)));
    final List<Zeiteintrag> eintraege = zeiten.findImZeitraum(monat.atDay(1), monat.atEndOfMonth());
    if (eintraege.isEmpty()) {
      final BigDecimal nichts = Zeiteintrag.stundenAus(0L);
      return new Arbeitsmonat(monat, List.of(), nichts, nichts, nichts);
    }
    final Map<Long, Buchungsposition> positionen =
        Buchungspositionen.beschreibe(
            angebote.findAlle(Optional.empty()), kennungen(eintraege), firmen);
    return new Arbeitsmonat(
        monat,
        tage(eintraege, positionen),
        summe(eintraege),
        summeDerArt(eintraege, positionen, false),
        summeDerArt(eintraege, positionen, true));
  }

  /*
   * Je Teil ein eigener Durchlauf und nicht eine Aufteilung in zwei Listen: Das Ergebnis ist
   * dasselbe, und die Summe bleibt die eine Rechnung aus summe(...) ohne Zwischenstand, der leer
   * sein koennte.
   */
  private static BigDecimal summeDerArt(
      final List<Zeiteintrag> eintraege,
      final Map<Long, Buchungsposition> positionen,
      final boolean intern) {
    return summe(
        eintraege.stream().filter(eintrag -> istIntern(eintrag, positionen) == intern).toList());
  }

  /*
   * Dieselbe Zusage wie in buchungen(): Buchungspositionen.beschreibe liefert jede angefragte
   * Kennung oder wirft, und requireNonNull schreibt das an der Nahtstelle hin.
   */
  private static boolean istIntern(
      final Zeiteintrag eintrag, final Map<Long, Buchungsposition> positionen) {
    return Objects.requireNonNull(positionen.get(eintrag.angebotPositionId())).intern();
  }

  private static Set<Long> kennungen(final List<Zeiteintrag> eintraege) {
    return eintraege.stream().map(Zeiteintrag::angebotPositionId).collect(Collectors.toSet());
  }

  /*
   * Erst sortieren, dann gruppieren: Der LinkedHashMap bleibt damit die Ordnung des Stroms, und die
   * ist der Kalender — Tage aufsteigend, innerhalb eines Tages nach Beginn. Zweimal zu sortieren,
   * aussen die Tage und innen die Zeilen, waere dieselbe Aussage an zwei Stellen.
   */
  private static List<Arbeitstag> tage(
      final List<Zeiteintrag> eintraege, final Map<Long, Buchungsposition> positionen) {
    return eintraege.stream()
        .sorted(Comparator.comparing(Zeiteintrag::tag).thenComparing(Zeiteintrag::von))
        .collect(Collectors.groupingBy(Zeiteintrag::tag, LinkedHashMap::new, Collectors.toList()))
        .entrySet()
        .stream()
        .map(
            tag ->
                new Arbeitstag(
                    tag.getKey(), buchungen(tag.getValue(), positionen), summe(tag.getValue())))
        .toList();
  }

  /*
   * Die Beschreibung ist da: Buchungspositionen.beschreibe liefert jede angefragte Kennung oder
   * wirft. Dass sie nie fehlt, sagt hier requireNonNull — nicht als Pruefung, sondern damit die
   * Zusage des Aufgerufenen an der Nahtstelle steht und nicht nur in seinem Javadoc.
   */
  private static List<Zeitbuchung> buchungen(
      final List<Zeiteintrag> eintraege, final Map<Long, Buchungsposition> positionen) {
    return eintraege.stream()
        .map(
            eintrag ->
                new Zeitbuchung(
                    eintrag, Objects.requireNonNull(positionen.get(eintrag.angebotPositionId()))))
        .toList();
  }

  /*
   * Die Minuten werden addiert und erst danach in Stunden umgerechnet (Zeiteintrag.stundenAus): Die
   * Summe der gerundeten Einzelwerte koennte um einen Rundungsrest daneben liegen.
   */
  private static BigDecimal summe(final List<Zeiteintrag> eintraege) {
    return Zeiteintrag.stundenAus(eintraege.stream().mapToLong(Zeiteintrag::minuten).sum());
  }
}
