package org.mwolff.fbcrm.jahresabschluss.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Year;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.mwolff.fbcrm.angebot.application.AngebotMitFirma;
import org.mwolff.fbcrm.angebot.application.AngeboteUebersichtUseCase;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.arbeitszeit.application.Arbeitszeitauskunft;
import org.mwolff.fbcrm.common.Geldrechnung;
import org.mwolff.fbcrm.common.Geschaeftszone;
import org.mwolff.fbcrm.rechnung.application.GestellteRechnung;
import org.mwolff.fbcrm.rechnung.application.Rechnungsauskunft;
import org.mwolff.fbcrm.rechnung.domain.Rechnungszustand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Der Jahresabschluss (#287; Plan #288, E1, E2): die Uebersicht aller Jahre mit Daten und der
 * Abschluss eines Jahres.
 *
 * <p><b>Jede Auskunft einmal gefragt.</b> Die gestellten Rechnungen kommen einzeln aus {@link
 * Rechnungsauskunft#gestellteRechnungen()} (E4), die Angebote mit allen Status aus {@link
 * AngeboteUebersichtUseCase#angebote(Optional)}; der Abschluss eines Jahres fragt dazu die Stunden
 * des Jahres in einem Zug aus {@link Arbeitszeitauskunft#alleImZeitraum(LocalDate, LocalDate)}
 * (E9), die Uebersicht braucht keine. Je Jahr zu fragen waere die Abfragelawine, die diese Tueren
 * gerade vermeiden. Dass dabei Angebote und Firmen zweimal geladen werden — einmal fuer den
 * Firmennamen an der Rechnung, einmal hier fuer die Angebotsbilanz —, ist der Preis dafuer, dass
 * der Weg zum Firmennamen im Modul {@code rechnung} bleibt (E6).
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
 * schon je Rechnung gerundeten Betraege nach {@link Geldrechnung}; die Annahmequote rundet {@link
 * Quote}, welche Angebote abgegeben und angenommen sind, sagt {@link Angebotsblick}, das auch ihre
 * Bilanz zieht, die Aufteilung je Steuersatz macht {@link Steuerblick} und die je Kunde {@link
 * Kundenblick}; den Erloes je Stunde teilt {@link Geldrechnung#je} (E12).
 */
@Service
@Transactional(readOnly = true)
public class JahresabschlussUseCase {

  private final Rechnungsauskunft rechnungen;
  private final AngeboteUebersichtUseCase angebote;
  private final Arbeitszeitauskunft arbeitszeit;
  private final Clock clock;

  JahresabschlussUseCase(
      final Rechnungsauskunft rechnungen,
      final AngeboteUebersichtUseCase angebote,
      final Arbeitszeitauskunft arbeitszeit,
      final Clock clock) {
    this.rechnungen = rechnungen;
    this.angebote = angebote;
    this.arbeitszeit = arbeitszeit;
    this.clock = clock;
  }

  /**
   * Die Uebersicht aller Jahre mit Daten.
   *
   * @return je Jahr mit mindestens einer gestellten Rechnung oder einem abgegebenen Angebot seine
   *     Zeile, das juengste zuerst; leer, wenn es kein solches Jahr gibt
   */
  public List<Jahreszeile> jahre() {
    final Year laufend = laufendesJahr();
    final Map<Year, List<GestellteRechnung>> rechnungenJeJahr =
        gestellte().stream()
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

  /**
   * Der Abschluss eines Jahres: Einnahmen, Rechnungsstand und Umsatzsteuer je Satz, Angebotsbilanz,
   * Umsatz je Kunde und Arbeitszeit (#287, Kriterien 4 bis 12).
   *
   * <p>Gefragt werden dieselben zwei Auskuenfte wie in {@link #jahre()} und dazu die Stunden des
   * Jahres, jede einmal. Gerechnet wird mit den je Rechnung gerundeten Betraegen, die addiert
   * werden (E5) — so treffen Netto und Brutto den Cent der Monatsabrechnung desselben Jahres.
   *
   * @param jahr das Kalenderjahr
   * @return der Abschluss des Jahres; ein Jahr nur mit abgegebenen Angeboten hat Einnahmen 0,00
   * @throws JahrOhneDaten wenn das Jahr weder eine gestellte Rechnung noch ein abgegebenes Angebot
   *     kennt (E3)
   */
  public Jahresabschluss abschluss(final Year jahr) {
    final List<GestellteRechnung> imJahr =
        gestellte().stream()
            .filter(rechnung -> Year.from(rechnung.rechnungDatum()).equals(jahr))
            .toList();
    final List<Angebot> alle =
        angebote.angebote(Optional.empty()).stream().map(AngebotMitFirma::angebot).toList();
    final List<Angebot> abgegeben =
        alle.stream()
            .filter(angebot -> Angebotsblick.abgegeben(angebot.status()))
            .filter(angebot -> Angebotsblick.jahr(angebot).equals(jahr))
            .toList();
    if (imJahr.isEmpty() && abgegeben.isEmpty()) {
      throw new JahrOhneDaten();
    }
    final Einnahmen einnahmen = einnahmen(imJahr);
    return new Jahresabschluss(
        jahr,
        jahr.equals(laufendesJahr()),
        einnahmen,
        rechnungsstand(imJahr),
        Steuerblick.zeilen(imJahr),
        Angebotsblick.bilanz(abgegeben),
        Kundenblick.zeilen(imJahr, einnahmen.netto()),
        jahresarbeitszeit(jahr, alle, einnahmen.netto()));
  }

  /*
   * Die gestellten Rechnungen aus der Auskunft. Dass dort kein Entwurf steht, sagt sie zu; der
   * Abschluss fragt den Zustand trotzdem, denn „Entwuerfe zaehlen nicht" ist seine Regel (#287,
   * Kriterium 5) und soll hier pruefbar sein.
   */
  private List<GestellteRechnung> gestellte() {
    return rechnungen.gestellteRechnungen().stream()
        .filter(rechnung -> rechnung.zustand().istGestellt())
        .toList();
  }

  /* Das laufende Jahr in der Geschaeftszone (E19). */
  private Year laufendesJahr() {
    return Year.now(clock.withZone(Geschaeftszone.ZONE));
  }

  /* Netto und Brutto Cent fuer Cent summiert, die Umsatzsteuer als ihr Abstand (Kriterium 4). */
  private static Einnahmen einnahmen(final List<GestellteRechnung> rechnungen) {
    final BigDecimal netto = nettoSumme(rechnungen);
    final BigDecimal brutto =
        Geldrechnung.summe(rechnungen.stream().map(GestellteRechnung::brutto));
    return new Einnahmen(netto, brutto, brutto.subtract(netto));
  }

  /* Die Zahl der Rechnungen und davon die heute offenen und die abgeschriebenen (Kriterium 5). */
  private static Rechnungsstand rechnungsstand(final List<GestellteRechnung> rechnungen) {
    final List<GestellteRechnung> offen =
        rechnungen.stream().filter(rechnung -> rechnung.zustand().istOffen()).toList();
    final List<GestellteRechnung> abgeschrieben =
        rechnungen.stream()
            .filter(rechnung -> rechnung.zustand() == Rechnungszustand.ABGESCHRIEBEN)
            .toList();
    return new Rechnungsstand(
        rechnungen.size(),
        offen.size(),
        nettoSumme(offen),
        abgeschrieben.size(),
        nettoSumme(abgeschrieben));
  }

  /*
   * Die Stunden mit Arbeitstag im Jahr, aus einem Zug vom 1. Januar bis zum 31. Dezember (E9).
   * Ob eine Position Kundenarbeit oder ein internes Projekt ist, sagt ihr Angebot — hier, und nur
   * hier, trennt der Filter auf interne Angebote (E8). Gezaehlt wird jede Position jedes Angebots,
   * gleich aus welchem Jahr und in welchem Status: Es zaehlt der Arbeitstag, nicht das Angebot.
   */
  private Jahresarbeitszeit jahresarbeitszeit(
      final Year jahr, final List<Angebot> alle, final BigDecimal einnahmenNetto) {
    final Map<Long, BigDecimal> imJahr =
        arbeitszeit.alleImZeitraum(jahr.atDay(1), jahr.atMonth(12).atEndOfMonth());
    final BigDecimal kundenStunden =
        stunden(alle.stream().filter(angebot -> !angebot.intern()), imJahr);
    return new Jahresarbeitszeit(
        kundenStunden,
        stunden(alle.stream().filter(Angebot::intern), imJahr),
        Geldrechnung.je(einnahmenNetto, kundenStunden));
  }

  /*
   * Die Stunden der gegebenen Angebote im Jahr, ueber alle ihre Positionen addiert. Ohne setScale:
   * Die Werte kommen mit Skala 2 aus der Auskunft und behalten sie; eine Stundenzahl ist kein
   * Betrag, und ohne einen einzigen Eintrag steht 0 da.
   */
  private static BigDecimal stunden(
      final Stream<Angebot> angebote, final Map<Long, BigDecimal> imJahr) {
    return angebote
        .flatMap(angebot -> angebot.positionen().stream())
        .map(position -> imJahr.getOrDefault(position.requireId(), BigDecimal.ZERO))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private static BigDecimal nettoSumme(final List<GestellteRechnung> rechnungen) {
    return Geldrechnung.summe(rechnungen.stream().map(GestellteRechnung::netto));
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
        nettoSumme(rechnungen),
        rechnungen.size(),
        Quote.prozent(BigDecimal.valueOf(angenommen), BigDecimal.valueOf(abgegeben.size())));
  }
}
