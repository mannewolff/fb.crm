package org.mwolff.fbcrm.jahresabschluss.application;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.mwolff.fbcrm.common.Geldrechnung;
import org.mwolff.fbcrm.rechnung.application.GestellteRechnung;

/**
 * Wie der Jahresabschluss die Rechnungen eines Jahres nach Kunden aufteilt (#287, Kriterium 9; Plan
 * #288, E10, E21, E22).
 *
 * <p><b>Gruppiert wird nach Firmenkennung, gezeigt der heutige Name</b> (E22): Der Name kann sich
 * aendern, die Kennung nicht. Zwei Rechnungen derselben Firma fallen so auch nach einer Umbenennung
 * in eine Zeile, und zwei Firmen mit gleichem Namen bleiben zwei. Den heutigen Namen nennt jede
 * {@link GestellteRechnung} selbst.
 *
 * <p><b>Absteigend nach Netto, bei gleichem Betrag nach Namen</b> (E21): Eine unbestimmte
 * Reihenfolge spraenge zwischen zwei Aufrufen.
 *
 * <p><b>Der Anteil entsteht ueber {@link Quote}</b> und summiert sich darum nicht zwingend auf
 * genau 100,0 % (E10): Ein Ausgleich auf die letzte Zeile erfaende einen Anteil, den die Rechnung
 * nicht hergibt.
 *
 * <p>Paket-privat und ohne eigene Testklasse — geprueft wird sie ueber {@code
 * JahresabschlussUseCaseTest}.
 */
final class Kundenblick {

  private Kundenblick() {}

  /**
   * Die Kundenzeilen der gegebenen Rechnungen.
   *
   * @param rechnungen die gestellten Rechnungen eines Jahres
   * @param einnahmenNetto ihre Summe netto, der Nenner der Anteile
   * @return je Firma eine Zeile, absteigend nach Netto und bei gleichem Betrag nach Namen; leer
   *     ohne Rechnung
   */
  static List<Kundenzeile> zeilen(
      final List<GestellteRechnung> rechnungen, final BigDecimal einnahmenNetto) {
    final Map<Long, List<GestellteRechnung>> jeFirma =
        rechnungen.stream().collect(Collectors.groupingBy(GestellteRechnung::firmaId));
    return jeFirma.values().stream()
        .map(gruppe -> zeile(gruppe, einnahmenNetto))
        .sorted(
            Comparator.comparing(Kundenzeile::netto)
                .reversed()
                .thenComparing(Kundenzeile::firmaName))
        .toList();
  }

  /* Eine Zeile: Netto Cent fuer Cent summiert, der Name von der ersten Rechnung der Firma. */
  private static Kundenzeile zeile(
      final List<GestellteRechnung> gruppe, final BigDecimal einnahmenNetto) {
    final BigDecimal netto = Geldrechnung.summe(gruppe.stream().map(GestellteRechnung::netto));
    return new Kundenzeile(
        gruppe.getFirst().firmaName(), netto, Quote.prozent(netto, einnahmenNetto));
  }
}
