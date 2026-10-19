package org.mwolff.fbcrm.startseite.application;

import java.math.BigDecimal;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Predicate;
import org.mwolff.fbcrm.angebot.application.AngebotMitFirma;
import org.mwolff.fbcrm.angebot.application.AngeboteUebersichtUseCase;
import org.mwolff.fbcrm.angebot.application.Angebotsauswahl;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;
import org.mwolff.fbcrm.common.Geldrechnung;
import org.mwolff.fbcrm.rechnung.application.Rechnungsauskunft;
import org.mwolff.fbcrm.rechnung.domain.Positionsstand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Der Forecast der Startseite: je Jahr der Leistung, was aus Angeboten noch abzurechnen ist (#302;
 * Plan #303).
 *
 * <p><b>Ein eigener Anwendungsfall neben {@link StartseiteUseCase}</b> (E1): Der Forecast haengt
 * nicht an der Zeitraumwahl (Kriterium 7) und nimmt darum keinen Zeitraum. In derselben Antwort wie
 * die Kennzahlen holte ihn jeder Wechsel des Zeitraums neu.
 *
 * <p><b>Zwei Zuege und keiner je Angebot.</b> Die Angebote mit den Namen ihrer Firmen kommen in
 * einem ({@link AngeboteUebersichtUseCase#angebote(Angebotsauswahl)}), die abgerechneten Mengen je
 * Angebotsposition in einem zweiten ({@link Rechnungsauskunft#abgerechneteMengen()}). Diese Tuer
 * zaehlt Entwuerfe mit — der Forecast zeigt den Rest, der am Angebot unter „Offen" steht, und nicht
 * den Umsatz (Kriterium 3).
 *
 * <p><b>Kein Statusfilter</b> (E4): Gefiltert wird {@code !intern}, {@code !storniert} (#330,
 * Kriterium 2; Plan #332) und „an dieser Position ist noch etwas offen"; der Status entscheidet
 * allein die Spalte ({@link #zugesagt}). Nach dem {@code intern}-Filter ist die Statusmenge aus
 * Kriterium 2 genau die Menge aller Kundenstatus, und eine Aufzaehlung, die kein Test kippen kann,
 * waere ein Loch in der Zweig- und Mutationsabdeckung (CLAUDE-java.md §5).
 *
 * <p><b>Offen ist die Menge, nicht der Betrag</b> (E5): Ob eine Position im Forecast steht, sagt
 * {@link Positionsstand#offenesVorhanden()}. Eine offene Position mit Einzelpreis 0 steht darum mit
 * 0,00 darin und verschwindet nicht.
 *
 * <p><b>Gerechnet wird nicht hier</b>, wo es die Regel schon gibt: den Rest einer Position rechnet
 * {@link Positionsstand} — offene Menge mal Einzelpreis, auf den Cent, nie unter 0 —, und die
 * Spaltensummen entstehen nach {@link Geldrechnung}, je Position gerundet und dann addiert.
 *
 * <p>Die Jahre stehen aufsteigend, aeltestes zuerst (E6); ein Jahr ohne offene Position entsteht
 * nicht (Kriterium 6). Unter einem Jahr stehen die Positionen in der Folge der Uebersicht und
 * innerhalb eines Angebots in dessen Reihenfolge.
 */
@Service
@Transactional(readOnly = true)
public class ForecastUseCase {

  private final AngeboteUebersichtUseCase angebote;
  private final Rechnungsauskunft rechnungen;

  ForecastUseCase(final AngeboteUebersichtUseCase angebote, final Rechnungsauskunft rechnungen) {
    this.angebote = angebote;
    this.rechnungen = rechnungen;
  }

  /**
   * Der Forecast, Stand von heute ueber alle Jahre.
   *
   * @return je Jahr mit mindestens einer offenen Position die Restbetraege „zugesagt" und
   *     „angeboten" samt den Positionen, aeltestes Jahr zuerst; leer ohne jede offene Position
   */
  public Forecast forecast() {
    final List<AngebotMitFirma> alle = angebote.angebote(Angebotsauswahl.alle());
    final Map<Long, BigDecimal> abgerechnet = rechnungen.abgerechneteMengen();
    final SortedMap<Year, List<Forecastposition>> jeJahr = new TreeMap<>();
    for (final AngebotMitFirma zeile : alle) {
      // Interne Arbeit erscheint nie (#302, Kriterium 2), ein storniertes Angebot mit keiner seiner
      // Positionen (#330, Kriterium 2) — und ein Jahr, das nur solche truege, entsteht darum nicht.
      if (zeile.angebot().intern() || zeile.angebot().storniert()) {
        continue;
      }
      for (final Angebotsposition position : zeile.angebot().positionen()) {
        final Positionsstand stand =
            new Positionsstand(
                position, abgerechnet.getOrDefault(position.requireId(), BigDecimal.ZERO));
        if (stand.offenesVorhanden()) {
          jeJahr
              .computeIfAbsent(position.leistungsjahr(), jahr -> new ArrayList<>())
              .add(new Forecastposition(zeile, position, stand.offenerBetrag()));
        }
      }
    }
    return new Forecast(
        jeJahr.entrySet().stream()
            .map(eintrag -> jahr(eintrag.getKey(), eintrag.getValue()))
            .toList());
  }

  /* Eine Jahreszeile: die Restbetraege ihrer Positionen, nach Spalte getrennt summiert. */
  private static Forecastjahr jahr(final Year jahr, final List<Forecastposition> positionen) {
    final Predicate<Forecastposition> zugesagt =
        zeile -> zugesagt(zeile.angebot().angebot().status());
    return new Forecastjahr(
        jahr,
        Geldrechnung.summe(positionen.stream().filter(zugesagt).map(Forecastposition::netto)),
        Geldrechnung.summe(
            positionen.stream().filter(zugesagt.negate()).map(Forecastposition::netto)),
        positionen);
  }

  /*
   * „Zugesagt" heisst bestellt, erledigt oder abgerechnet (#302, Kriterium 1; E4) — ein
   * abgerechnetes Angebot mit Rest steht hier und nicht unter „angeboten". Ausdruecklich aufgezaehlt
   * wie in StartseiteUseCase.inArbeit: Der Compiler verlangt bei einem neuen Status eine
   * Entscheidung, statt ihn stillschweigend einzureihen.
   *
   * <p>Die internen Status stehen im false-Zweig, erreichen ihn aber nie: Interne Angebote fallen
   * vorher heraus (Kriterium 2).
   */
  private static boolean zugesagt(final Angebotsstatus status) {
    return switch (status) {
      case BESTELLT, ERLEDIGT, ABGERECHNET -> true;
      case ANGELEGT, ABGEGEBEN, LAEUFT, ABGESCHLOSSEN -> false;
    };
  }
}
