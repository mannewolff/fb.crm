package org.mwolff.fbcrm.startseite.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.mwolff.fbcrm.angebot.application.AngebotMitFirma;
import org.mwolff.fbcrm.angebot.application.AngeboteUebersichtUseCase;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;
import org.mwolff.fbcrm.arbeitszeit.application.Arbeitszeitauskunft;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Einheit;
import org.mwolff.fbcrm.common.Geldrechnung;
import org.mwolff.fbcrm.common.Geschaeftszone;
import org.mwolff.fbcrm.rechnung.application.Gestellte;
import org.mwolff.fbcrm.rechnung.application.Rechnungsauskunft;
import org.mwolff.fbcrm.rechnung.domain.Positionsstand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Die drei Kennzahlen der Startseite (#206; Plan #208, E1).
 *
 * <p><b>Vier Auskuenfte und keine je Angebot.</b> Die Angebote mit den Namen ihrer Firmen kommen in
 * einem Zug ({@link AngeboteUebersichtUseCase#angebote(Optional)}, E2) — mit allen Status, weil
 * Kennzahl 2 sie alle braucht und Kennzahl 1 daraus filtert. Aus der Zeiterfassung kommen drei
 * Zuege ueber alle Angebote ({@link Arbeitszeitauskunft#alleAngefallen()}, {@link
 * Arbeitszeitauskunft#alleImZeitraum(LocalDate, LocalDate)} und {@link
 * Arbeitszeitauskunft#monateMitEintragImZeitraum(LocalDate, LocalDate)}; Plan #208, E3; Plan #274,
 * E7), die gestellten Rechnungen je Monat in einem ({@link Rechnungsauskunft#gestellte()}, Plan
 * #274, E4). Je Angebot zu fragen waere die Abfragelawine, die diese Tueren gerade vermeiden.
 *
 * <p><b>Welcher Zeitraum gilt, entscheidet der Server</b> an der injizierten {@link Clock} in der
 * {@link Geschaeftszone} (E8): Am 1. des Monats um 00:30 Ortszeit ist am Nullmeridian noch der
 * Vormonat. Welche Jahre und Monate zur Wahl stehen, entsteht aus dem Bestand ({@link
 * WaehlbareZeitraeume}, #273, Kriterien 1 und 2). Wer keinen Zeitraum nennt oder einen, der nicht
 * zur Wahl steht, bekommt das laufende Jahr — ein unbekannter Zeitraum ist kein Fehler, sondern ein
 * fehlender (E18; Issue #283). Die Verdichtung der Rechnungen zu „Abgerechnet" steht daneben in
 * {@link Abrechnungsblick} (Plan #274, E16).
 *
 * <p><b>Was „in Arbeit" heisst, steht hier</b> und wird nicht mit der Zeiterfassung geteilt (E22):
 * „bestellt" oder „erledigt" ist die fachliche Kennzeichnung eines Angebots aus #206, „auf diese
 * Position darf jetzt gebucht werden" eine Regel jenes Moduls. Beide Mengen stimmen heute ueberein,
 * duerfen aber auseinanderlaufen. Dasselbe gilt fuer den Umfang der Kennzahl 2 ({@link
 * #nachAufwandInStunden}). Die Richtung {@code startseite} → {@code angebot} genuegt damit; nach
 * drueben geht nur die Frage nach Stunden.
 *
 * <p><b>Interne Angebote fallen nur an einer Kennzahl heraus</b> (#207, Kriterium 9; Plan #218,
 * E17). Der Filter steht in {@link #nichtAbgerechnet} und sonst nirgends: Kennzahl 1 schliesst die
 * interne Arbeit schon ueber ihre Statusmenge aus ({@link #inArbeit} kennt nur {@code BESTELLT} und
 * {@code ERLEDIGT}), und in Kennzahl 3 kann sie nicht stehen, weil ein internes Angebot nie eine
 * Rechnung hat. Ein zusaetzliches {@code !intern} waere dort eine Bedingung, die kein Test kippen
 * kann — und damit ein Loch in der Zweig- und Mutationsabdeckung (CLAUDE-java.md §5). Noetig ist
 * der Filter allein an Kennzahl 2: Eine nach innen umgestellte Position traegt Menge und Preis
 * weiter (Kriterium 8) und schluege sonst als Euro-Betrag auf.
 *
 * <p><b>Gerechnet wird nicht hier</b>, wo es die Regel schon gibt: Was an einer Position noch nicht
 * abgerechnet ist, rechnet {@link Positionsstand} (E6), und jeder Betrag entsteht nach {@link
 * Geldrechnung} — je Position auf den Cent, dann addiert (E23).
 */
@Service
@Transactional(readOnly = true)
public class StartseiteUseCase {

  private final AngeboteUebersichtUseCase angebote;
  private final Arbeitszeitauskunft arbeitszeit;
  private final Rechnungsauskunft rechnungen;
  private final Clock clock;

  StartseiteUseCase(
      final AngeboteUebersichtUseCase angebote,
      final Arbeitszeitauskunft arbeitszeit,
      final Rechnungsauskunft rechnungen,
      final Clock clock) {
    this.angebote = angebote;
    this.arbeitszeit = arbeitszeit;
    this.rechnungen = rechnungen;
    this.clock = clock;
  }

  /**
   * Der Stand der Startseite fuer einen Zeitraum.
   *
   * @param gewaehlt der gewuenschte Monat oder das gewuenschte Jahr, oder leer fuer das laufende
   *     Jahr; ein Zeitraum, der nicht zur Wahl steht, wirkt wie ein fehlender
   * @return die drei Kennzahlen, die internen Stunden des Zeitraums, der geltende Zeitraum und die
   *     waehlbaren
   */
  public Startseitenstand stand(final Optional<Zeitraum> gewaehlt) {
    final YearMonth laufend = YearMonth.now(clock.withZone(Geschaeftszone.ZONE));
    final Year diesesJahr = Year.from(laufend);
    final Gestellte gestellte = rechnungen.gestellte();
    final WaehlbareZeitraeume waehlbar =
        WaehlbareZeitraeume.herleiten(
            laufend,
            gestellte.jeMonat().keySet(),
            arbeitszeit.monateMitEintragImZeitraum(
                new Zeitraum.Jahr(diesesJahr.minusYears(1)).von(),
                new Zeitraum.Jahr(diesesJahr).bis()));
    final Zeitraum zeitraum =
        gewaehlt.filter(waehlbar::enthaelt).orElse(new Zeitraum.Jahr(diesesJahr));
    final List<AngebotMitFirma> alle = angebote.angebote(Optional.empty());
    final Map<Long, BigDecimal> angefallen = arbeitszeit.alleAngefallen();
    final Map<Long, BigDecimal> imZeitraum =
        arbeitszeit.alleImZeitraum(zeitraum.von(), zeitraum.bis());
    return new Startseitenstand(
        zeitraum,
        waehlbar,
        alle.stream().filter(zeile -> inArbeit(zeile.angebot().status())).toList(),
        nichtAbgerechnet(alle, angefallen, imZeitraum, gestellte.mengenJePosition()),
        Abrechnungsblick.fuer(gestellte.jeMonat(), zeitraum),
        interneStundenImZeitraum(alle, imZeitraum));
  }

  /*
   * „In Arbeit" heisst bestellt oder erledigt (#206, Begriff). Ausdruecklich aufgezaehlt und nicht
   * ueber die Ordnungszahl von Angebotsstatus bestimmt: Der Compiler verlangt bei einem neuen
   * Status eine Entscheidung, statt ihn stillschweigend einzureihen — dieselbe Ueberlegung wie in
   * Angebotsstatus selbst.
   *
   * <p>Die internen Status stehen im false-Zweig, und genau das leistet Kriterium 9 von #207 fuer
   * diese Kennzahl: Ein internes Angebot erscheint nicht unter „Angebote in Arbeit", ohne dass es
   * dafuer eine eigene Bedingung braucht (Plan #218, E17).
   */
  private static boolean inArbeit(final Angebotsstatus status) {
    return switch (status) {
      case BESTELLT, ERLEDIGT -> true;
      case ANGELEGT, ABGEGEBEN, ABGERECHNET, LAEUFT, ABGESCHLOSSEN -> false;
    };
  }

  /*
   * Ein Durchlauf durch die Angebote, zwei Summen daraus: der Hauptbetrag je Angebot (und nur
   * aufgenommen, wenn er ueber 0 liegt, E11) und der Wert der im Zeitraum erfassten Stunden ueber
   * alle. Beide entstehen je Position gerundet und werden danach addiert (E23); fuer die zweite
   * Zeile gilt dabei kein Deckel und kein Abzug (#206, Kriterium 6).
   */
  private static NichtAbgerechnet nichtAbgerechnet(
      final List<AngebotMitFirma> alle,
      final Map<Long, BigDecimal> angefallen,
      final Map<Long, BigDecimal> imZeitraum,
      final Map<Long, BigDecimal> gestellteMengen) {
    final List<Angebotsanteil> anteile = new ArrayList<>();
    final List<BigDecimal> zeitraumwerte = new ArrayList<>();
    for (final AngebotMitFirma zeile : alle) {
      // Interne Angebote tragen weder zum Hauptbetrag noch zur zweiten Zeile bei (#207, Kriterium
      // 9;
      // Plan #218, E17). Dies ist die eine Kennzahl, an der der Filter noetig ist.
      if (zeile.angebot().intern()) {
        continue;
      }
      final List<BigDecimal> betraege = new ArrayList<>();
      for (final Angebotsposition position : zeile.angebot().positionen()) {
        if (nachAufwandInStunden(position)) {
          final long id = position.requireId();
          betraege.add(
              new Positionsstand(position, gestellteMengen.getOrDefault(id, BigDecimal.ZERO))
                  .nichtAbgerechneterBetrag(angefallen.getOrDefault(id, BigDecimal.ZERO)));
          zeitraumwerte.add(
              Geldrechnung.betrag(
                  imZeitraum.getOrDefault(id, BigDecimal.ZERO), position.einzelpreis()));
        }
      }
      final BigDecimal anteil = Geldrechnung.summe(betraege.stream());
      if (anteil.signum() > 0) {
        anteile.add(new Angebotsanteil(zeile, anteil));
      }
    }
    return new NichtAbgerechnet(
        Geldrechnung.summe(anteile.stream().map(Angebotsanteil::betrag)),
        Geldrechnung.summe(zeitraumwerte.stream()),
        anteile);
  }

  /*
   * Die internen Stunden des gewaehlten Zeitraums: ueber die Positionen der internen Angebote die
   * Werte aus imZeitraum addiert. Die Karte liegt fuer Kennzahl 2 ohnehin schon vor — ein eigener Zug
   * in die Zeiterfassung waere die Abfragelawine, die diese Tueren vermeiden (Plan #208, E3).
   *
   * <p>Gezaehlt wird jede Position und nicht nur eine nach nachAufwandInStunden: An einem internen
   * Angebot ist jede Position buchbar (Issue #229, E11), und jene Regel ist der Umfang der
   * Euro-Kennzahl. Hier entsteht eine Stundenzahl, in der ein Teil der erfassten Zeit ohne Grund
   * fehlte.
   *
   * <p>Addiert ohne setScale: Die Werte kommen mit Skala 2 aus der Auskunft
   * (Zeiteintrag.stundenAus) und behalten sie beim Addieren; ohne eine einzige Buchung im Zeitraum
   * steht 0 da. Gerundet wird nichts — eine Stundenzahl ist kein Betrag, und Geldrechnung gilt hier
   * nicht.
   */
  private static BigDecimal interneStundenImZeitraum(
      final List<AngebotMitFirma> alle, final Map<Long, BigDecimal> imZeitraum) {
    return alle.stream()
        .filter(zeile -> zeile.angebot().intern())
        .flatMap(zeile -> zeile.angebot().positionen().stream())
        .map(position -> imZeitraum.getOrDefault(position.requireId(), BigDecimal.ZERO))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  /*
   * Der Umfang der Kennzahl 2: Positionen „nach Aufwand" in Stunden (#206, Kriterium 5). Stunden an
   * einer Position, die inzwischen zum Festpreis oder in einer anderen Einheit abrechnet, gehen mit
   * 0,00 in beide Betraege ein (E12) — sie bleiben am Angebot sichtbar, aber die Startseite
   * bewertet sie nicht mit einem Preis, der fuer etwas anderes gilt.
   *
   * Die Regel steht hier und wird nicht aus der Zeiterfassung geholt, aus demselben Grund wie die
   * Statusmenge in inArbeit (E22): Dort heisst sie „darf Stunden tragen" und gehoert jenem Modul,
   * hier ist sie der fachliche Umfang einer Kennzahl aus #206. Die beiden stimmen heute ueberein
   * und duerfen auseinanderlaufen; so genuegt die Richtung startseite -> angebot, und nach drueben
   * geht nur die Frage nach Stunden.
   */
  private static boolean nachAufwandInStunden(final Angebotsposition position) {
    return position.abrechnungsmodus() == Abrechnungsmodus.AUFWAND
        && position.einheit() == Einheit.STUNDE;
  }
}
