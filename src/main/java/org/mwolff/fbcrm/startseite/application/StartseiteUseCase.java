package org.mwolff.fbcrm.startseite.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
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
 * Kennzahl 2 sie alle braucht und Kennzahl 1 daraus filtert. Die Stunden kommen in zwei Zuegen
 * ueber alle Angebote ({@link Arbeitszeitauskunft#alleAngefallen()} und {@link
 * Arbeitszeitauskunft#alleImMonat(YearMonth)}, E3), die gestellten Rechnungen in einem ({@link
 * Rechnungsauskunft#gestellte(YearMonth)}, E5). Je Angebot zu fragen waere die Abfragelawine, die
 * diese Tueren gerade vermeiden.
 *
 * <p><b>Welcher Monat gilt, entscheidet der Server</b> an der injizierten {@link Clock} in der
 * {@link Geschaeftszone} (E8): Am 1. des Monats um 00:30 Ortszeit ist am Nullmeridian noch der
 * Vormonat. Wer keinen Monat nennt oder einen ausserhalb der zwoelf waehlbaren, bekommt den
 * laufenden — ein unbekannter Monat ist kein Fehler, sondern ein fehlender (E18).
 *
 * <p><b>Was „in Arbeit" heisst, steht hier</b> und wird nicht mit der Zeiterfassung geteilt (E22):
 * „bestellt" oder „erledigt" ist die fachliche Kennzeichnung eines Angebots aus #206, „auf diese
 * Position darf jetzt gebucht werden" eine Regel jenes Moduls. Beide Mengen stimmen heute ueberein,
 * duerfen aber auseinanderlaufen. Dasselbe gilt fuer den Umfang der Kennzahl 2 ({@link
 * #nachAufwandInStunden}). Die Richtung {@code startseite} → {@code angebot} genuegt damit; nach
 * drueben geht nur die Frage nach Stunden.
 *
 * <p><b>Gerechnet wird nicht hier</b>, wo es die Regel schon gibt: Was an einer Position noch nicht
 * abgerechnet ist, rechnet {@link Positionsstand} (E6), und jeder Betrag entsteht nach {@link
 * Geldrechnung} — je Position auf den Cent, dann addiert (E23).
 */
@Service
@Transactional(readOnly = true)
public class StartseiteUseCase {

  /** Die Zahl der waehlbaren Monate: der laufende und die elf davor (#206, Kriterium 3). */
  private static final int WAEHLBARE_MONATE = 12;

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
   * Der Stand der Startseite fuer einen Monat.
   *
   * @param gewaehlt der gewuenschte Monat, oder leer fuer den laufenden; ein Monat ausserhalb der
   *     zwoelf waehlbaren wirkt wie ein fehlender
   * @return die drei Kennzahlen, der geltende Monat und die zwoelf waehlbaren
   */
  public Startseitenstand stand(final Optional<YearMonth> gewaehlt) {
    final YearMonth laufend = YearMonth.now(clock.withZone(Geschaeftszone.ZONE));
    final List<YearMonth> monate =
        IntStream.range(0, WAEHLBARE_MONATE).mapToObj(laufend::minusMonths).toList();
    final YearMonth monat = gewaehlt.filter(monate::contains).orElse(laufend);
    final List<AngebotMitFirma> alle = angebote.angebote(Optional.empty());
    final Map<Long, BigDecimal> angefallen = arbeitszeit.alleAngefallen();
    final Map<Long, BigDecimal> imMonat = arbeitszeit.alleImMonat(monat);
    final Gestellte gestellte = rechnungen.gestellte(monat);
    return new Startseitenstand(
        monat,
        monate,
        alle.stream().filter(zeile -> inArbeit(zeile.angebot().status())).toList(),
        nichtAbgerechnet(alle, angefallen, imMonat, gestellte.mengenJePosition()),
        gestellte.imMonat());
  }

  /*
   * „In Arbeit" heisst bestellt oder erledigt (#206, Begriff). Ausdruecklich aufgezaehlt und nicht
   * ueber die Ordnungszahl von Angebotsstatus bestimmt: Der Compiler verlangt bei einem neuen
   * Status eine Entscheidung, statt ihn stillschweigend einzureihen — dieselbe Ueberlegung wie in
   * Angebotsstatus selbst.
   *
   * <p>Die internen Status stehen vorerst auf false: Die Startseite nimmt die interne Arbeit erst
   * mit ihrem eigenen Paket auf (#226, Plan #218, E18). Hier stehen sie, weil der erschoepfende
   * switch sonst nicht uebersetzt.
   */
  private static boolean inArbeit(final Angebotsstatus status) {
    return switch (status) {
      case BESTELLT, ERLEDIGT -> true;
      case ANGELEGT, ABGEGEBEN, ABGERECHNET, LAEUFT, ABGESCHLOSSEN -> false;
    };
  }

  /*
   * Ein Durchlauf durch die Angebote, zwei Summen daraus: der Hauptbetrag je Angebot (und nur
   * aufgenommen, wenn er ueber 0 liegt, E11) und der Wert der im Monat erfassten Stunden ueber
   * alle. Beide entstehen je Position gerundet und werden danach addiert (E23); fuer die
   * Monatszeile gilt dabei kein Deckel und kein Abzug (#206, Kriterium 6).
   */
  private static NichtAbgerechnet nichtAbgerechnet(
      final List<AngebotMitFirma> alle,
      final Map<Long, BigDecimal> angefallen,
      final Map<Long, BigDecimal> imMonat,
      final Map<Long, BigDecimal> gestellteMengen) {
    final List<Angebotsanteil> anteile = new ArrayList<>();
    final List<BigDecimal> monatswerte = new ArrayList<>();
    for (final AngebotMitFirma zeile : alle) {
      final List<BigDecimal> betraege = new ArrayList<>();
      for (final Angebotsposition position : zeile.angebot().positionen()) {
        if (nachAufwandInStunden(position)) {
          final long id = position.requireId();
          betraege.add(
              new Positionsstand(position, gestellteMengen.getOrDefault(id, BigDecimal.ZERO))
                  .nichtAbgerechneterBetrag(angefallen.getOrDefault(id, BigDecimal.ZERO)));
          monatswerte.add(
              Geldrechnung.betrag(
                  imMonat.getOrDefault(id, BigDecimal.ZERO), position.einzelpreis()));
        }
      }
      final BigDecimal anteil = Geldrechnung.summe(betraege.stream());
      if (anteil.signum() > 0) {
        anteile.add(new Angebotsanteil(zeile, anteil));
      }
    }
    return new NichtAbgerechnet(
        Geldrechnung.summe(anteile.stream().map(Angebotsanteil::betrag)),
        Geldrechnung.summe(monatswerte.stream()),
        anteile);
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
