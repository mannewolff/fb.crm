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
import org.mwolff.fbcrm.rechnung.domain.Rechnungszustand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Der Jahresabschluss (#287; Plan #288, E1, E2): die Uebersicht aller Jahre mit Daten und der
 * Abschluss eines Jahres.
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
 * schon je Rechnung gerundeten Betraege nach {@link Geldrechnung}; die Annahmequote rundet {@link
 * Quote}, welche Angebote abgegeben und angenommen sind, sagt {@link Angebotsblick}, und die
 * Aufteilung je Steuersatz macht {@link Steuerblick}.
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
   * Der Abschluss eines Jahres: Einnahmen, Rechnungsstand und Umsatzsteuer je Satz (#287, Kriterien
   * 4 bis 6).
   *
   * <p>Gefragt werden dieselben zwei Auskuenfte wie in {@link #jahre()}, je einmal. Gerechnet wird
   * mit den je Rechnung gerundeten Betraegen, die addiert werden (E5) — so treffen Netto und Brutto
   * den Cent der Monatsabrechnung desselben Jahres.
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
    final List<Angebot> abgegeben =
        angebote.angebote(Optional.empty()).stream()
            .map(AngebotMitFirma::angebot)
            .filter(angebot -> Angebotsblick.abgegeben(angebot.status()))
            .filter(angebot -> Angebotsblick.jahr(angebot).equals(jahr))
            .toList();
    if (imJahr.isEmpty() && abgegeben.isEmpty()) {
      throw new JahrOhneDaten();
    }
    return new Jahresabschluss(
        jahr,
        jahr.equals(laufendesJahr()),
        einnahmen(imJahr),
        rechnungsstand(imJahr),
        Steuerblick.zeilen(imJahr));
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
