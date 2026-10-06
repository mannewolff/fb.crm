package org.mwolff.fbcrm.startseite.application;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.mwolff.fbcrm.rechnung.application.Monatsabrechnung;

/**
 * Die Verdichtung der gestellten Rechnungen je Monat zur Kennzahl „Abgerechnet" eines Zeitraums
 * (Plan #274, E4, E5, E11, E16).
 *
 * <p>Sie steht neben dem Anwendungsfall und nicht darin (E16): Der Zuschnitt soll nicht an einem
 * Grenzwert von PMD haengen. Paket-privat und ohne eigene Testklasse — die Zerlegung ist keine
 * eigene Zusage, geprueft wird sie ueber {@code StartseiteUseCaseTest}.
 *
 * <p><b>Die Summe ist die Summe der Monatssummen</b> (E5): Jeder Betrag ist schon je Rechnung auf
 * den Cent gerundet, und die Addition gerundeter Werte ist in jeder Gruppierung dieselbe Zahl. Ohne
 * eine Rechnung im Zeitraum ist es die Summe ueber keine — 0,00 und Anzahl 0.
 */
final class Abrechnungsblick {

  private Abrechnungsblick() {}

  /**
   * „Abgerechnet" fuer einen Zeitraum.
   *
   * @param jeMonat je Monat mit mindestens einer gestellten Rechnung deren Abrechnung, ueber alle
   *     Monate
   * @param zeitraum der geltende Zeitraum
   * @return die Summe der Monate, die der Zeitraum enthaelt, und bei einem Jahr ihre Zeilen
   *     aufsteigend, bei einem Monat keine
   */
  static Abgerechnet fuer(final Map<YearMonth, Monatsabrechnung> jeMonat, final Zeitraum zeitraum) {
    final List<Abrechnungsmonat> imZeitraum =
        jeMonat.entrySet().stream()
            .filter(eintrag -> zeitraum.enthaelt(eintrag.getKey()))
            .sorted(Map.Entry.comparingByKey())
            .map(eintrag -> new Abrechnungsmonat(eintrag.getKey(), eintrag.getValue()))
            .toList();
    return new Abgerechnet(
        Monatsabrechnung.summe(imZeitraum.stream().map(Abrechnungsmonat::abrechnung)),
        switch (zeitraum) {
          case Zeitraum.Monat _ -> List.of();
          case Zeitraum.Jahr _ -> imZeitraum;
        });
  }
}
