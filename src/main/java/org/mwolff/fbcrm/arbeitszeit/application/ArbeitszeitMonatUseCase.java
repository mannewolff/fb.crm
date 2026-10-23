package org.mwolff.fbcrm.arbeitszeit.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
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
import org.mwolff.fbcrm.auth.application.UnknownAccount;
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
 * <p><b>Wessen Zeiten die Liste zeigt, entscheidet die Rolle des anfragenden Kontos</b> (Plan #426,
 * E6, E10; fachliche Quelle #410, Kriterien 2 bis 4) — hier und nicht im Browser, aus demselben
 * Grund wie beim laufenden Monat (E4): Die Vorauswahl ist eine fachliche Regel. Ohne Angabe sieht
 * ein C-Level und ein Employee die eigenen Zeiten, ReadOnly die aller. Ein Employee bekommt immer
 * nur seine eigenen, gleich was der Parameter nennt; ein C-Level und ReadOnly duerfen auf jede
 * Person umschalten, auch auf eine stillgelegte (Kriterium 9), oder auf „Alle". Die Regel steht in
 * {@link Personenauskunft#sicht}, neben der Liste der waehlbaren Personen, die an ihr haengt; die
 * Rolle kommt dort frisch aus dem Konto und nicht aus dem Token.
 *
 * <p><b>Gefiltert wird im Bestand</b> (E8): Fuer eine Person fragt die Liste nur deren Eintraege
 * ({@link ZeiteintragRepository#findImZeitraumFuerKonto}), nur fuer „Alle" alle. Ein Employee
 * bekommt so keinen fremden Eintrag in den Speicher, und kein fremdes Konto: Seine Personen sind
 * allein sein eigenes Konto, die Liste aller Konten liest erst ein C-Level oder ReadOnly.
 *
 * <p><b>Keine Zuege je Zeile:</b> das anfragende Konto, die Konten (ausser fuer einen Employee),
 * die Eintraege des Monats, die Angebote und die Namen aller beteiligten Firmen auf einmal ({@link
 * Buchungspositionen}). Ein leerer Monat fragt nach den Eintraegen nicht weiter. Der Name an jeder
 * Zeile ist der aktuelle des Kontos und keine Kopie (E5, Kriterium 9).
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
  private final Personenauskunft personenauskunft;
  private final Clock clock;

  ArbeitszeitMonatUseCase(
      final ZeiteintragRepository zeiten,
      final AngebotRepository angebote,
      final FirmaRepository firmen,
      final Personenauskunft personenauskunft,
      final Clock clock) {
    this.zeiten = zeiten;
    this.angebote = angebote;
    this.firmen = firmen;
    this.personenauskunft = personenauskunft;
    this.clock = clock;
  }

  /**
   * Die Eintraege eines Monats nach Tagen, jeder mit seiner Summe, dazu die des Monats.
   *
   * <p>Die Monatssumme kommt zusaetzlich aufgeteilt in den Teil fuer Kunden und den internen (Issue
   * #230, Kriterium 10 von #207). Beide entstehen wie die Gesamtsumme aus addierten Minuten; die
   * Tagessummen bleiben ungeteilt. Alle Summen gelten fuer die Eintraege der geltenden Auswahl.
   *
   * @param accountId Kennung des anfragenden Kontos
   * @param gewaehlt der gesuchte Monat, oder leer fuer den laufenden
   * @param person die Kennung eines Kontos oder {@code alle}, oder leer fuer die Vorauswahl der
   *     Rolle; fuer einen Employee ohne Wirkung
   * @throws UnknownAccount wenn es das anfragende Konto nicht mehr gibt
   * @throws PersonNichtWaehlbar wenn {@code person} weder {@code alle} noch ein Konto nennt
   * @throws AngebotNichtGefunden wenn ein gebuchtes Angebot im Bestand fehlt
   * @throws FirmaNichtGefunden wenn die Firma eines beteiligten Angebots fehlt
   */
  public Arbeitsmonat monat(
      final long accountId, final Optional<YearMonth> gewaehlt, final Optional<String> person) {
    final YearMonth monat =
        gewaehlt.orElseGet(() -> YearMonth.now(clock.withZone(Geschaeftszone.ZONE)));
    final Personensicht sicht = personenauskunft.sicht(accountId, person);
    final Personenauswahl auswahl = sicht.auswahl();
    final List<Zeiteintrag> eintraege = eintraege(auswahl, monat.atDay(1), monat.atEndOfMonth());
    if (eintraege.isEmpty()) {
      final BigDecimal nichts = Zeiteintrag.stundenAus(0L);
      return new Arbeitsmonat(monat, auswahl, List.of(), nichts, nichts, nichts);
    }
    final Map<Long, Buchungsposition> positionen =
        Buchungspositionen.beschreibe(
            angebote.findAlle(Optional.empty()), kennungen(eintraege), firmen);
    return new Arbeitsmonat(
        monat,
        auswahl,
        tage(eintraege, positionen, sicht),
        summe(eintraege),
        summeDerArt(eintraege, positionen, false),
        summeDerArt(eintraege, positionen, true));
  }

  private List<Zeiteintrag> eintraege(
      final Personenauswahl auswahl, final LocalDate von, final LocalDate bis) {
    return switch (auswahl) {
      case Personenauswahl.Alle _ -> zeiten.findImZeitraum(von, bis);
      case Personenauswahl.Einzeln einzeln ->
          zeiten.findImZeitraumFuerKonto(von, bis, einzeln.accountId());
    };
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
      final List<Zeiteintrag> eintraege,
      final Map<Long, Buchungsposition> positionen,
      final Personensicht sicht) {
    return eintraege.stream()
        .sorted(Comparator.comparing(Zeiteintrag::tag).thenComparing(Zeiteintrag::von))
        .collect(Collectors.groupingBy(Zeiteintrag::tag, LinkedHashMap::new, Collectors.toList()))
        .entrySet()
        .stream()
        .map(
            tag ->
                new Arbeitstag(
                    tag.getKey(),
                    buchungen(tag.getValue(), positionen, sicht),
                    summe(tag.getValue())))
        .toList();
  }

  /*
   * Die Beschreibung ist da: Buchungspositionen.beschreibe liefert jede angefragte Kennung oder
   * wirft. Dass sie nie fehlt, sagt hier requireNonNull — nicht als Pruefung, sondern damit die
   * Zusage des Aufgerufenen an der Nahtstelle steht und nicht nur in seinem Javadoc.
   * Dieselbe Zusage fuer die Person steht in Personensicht.person.
   */
  private static List<Zeitbuchung> buchungen(
      final List<Zeiteintrag> eintraege,
      final Map<Long, Buchungsposition> positionen,
      final Personensicht sicht) {
    return eintraege.stream()
        .map(
            eintrag ->
                new Zeitbuchung(
                    eintrag,
                    Objects.requireNonNull(positionen.get(eintrag.angebotPositionId())),
                    sicht.person(eintrag.accountId())))
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
