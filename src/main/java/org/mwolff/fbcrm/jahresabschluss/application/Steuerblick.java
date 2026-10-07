package org.mwolff.fbcrm.jahresabschluss.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Geldrechnung;
import org.mwolff.fbcrm.rechnung.application.GestellteRechnung;

/**
 * Wie der Jahresabschluss die Rechnungen eines Jahres nach Steuersatz aufteilt (#287, Kriterium 6;
 * Plan #288, E13, E14).
 *
 * <p><b>Schluessel ist der Satz auf zwei Nachkommastellen gebracht</b>: Die Spalte ist {@code
 * numeric(5,2)}, 19 und 19,00 sind derselbe Satz und fallen in eine Zeile.
 *
 * <p><b>Die nachgetragenen Rechnungen stehen in einer eigenen Zeile ohne Satz</b>, und zwar
 * zuletzt: Ihr Satz ist nicht erfasst, und aus Netto und Brutto abgeleitet waere er eine Zahl, die
 * niemand angegeben hat. In einer Ordnung nach Saetzen hat eine Zeile ohne Satz keinen Platz.
 *
 * <p><b>Netto und Umsatzsteuer einer Zeile sind Summen der je Rechnung gerundeten Betraege</b>
 * (E5): Die Umsatzsteuer ist Summe brutto minus Summe netto und nicht die Jahressumme mal Satz.
 * Darum ergeben alle Zeilen zusammen genau die {@link Einnahmen}.
 *
 * <p><b>Ergaebe die Aufteilung nur eine Zeile, entfaellt sie</b> (E14): Die Regel steht hier und
 * nicht in der Ansicht, damit sie an einer Stelle steht und pruefbar ist.
 *
 * <p>Paket-privat und ohne eigene Testklasse — geprueft wird sie ueber {@code
 * JahresabschlussUseCaseTest}.
 */
final class Steuerblick {

  /** Die Zahl der Nachkommastellen eines Steuersatzes, wie in der Spalte {@code numeric(5,2)}. */
  private static final int SATZSTELLEN = 2;

  private Steuerblick() {}

  /**
   * Die Steuerzeilen der gegebenen Rechnungen.
   *
   * @param rechnungen die gestellten Rechnungen eines Jahres
   * @return je Satz eine Zeile, aufsteigend, und die nachgetragenen zuletzt; leer, wenn es weniger
   *     als zwei Zeilen waeren
   */
  static List<Steuerzeile> zeilen(final List<GestellteRechnung> rechnungen) {
    final SortedMap<BigDecimal, List<GestellteRechnung>> jeSatz = new TreeMap<>();
    final List<GestellteRechnung> ohneSatz = new ArrayList<>();
    for (final GestellteRechnung rechnung : rechnungen) {
      final BigDecimal satz = rechnung.steuersatz();
      if (satz == null) {
        ohneSatz.add(rechnung);
      } else {
        jeSatz
            .computeIfAbsent(
                satz.setScale(SATZSTELLEN, RoundingMode.HALF_UP), schluessel -> new ArrayList<>())
            .add(rechnung);
      }
    }
    final List<Steuerzeile> zeilen = new ArrayList<>();
    jeSatz.forEach((satz, gruppe) -> zeilen.add(zeile(satz, gruppe)));
    if (!ohneSatz.isEmpty()) {
      zeilen.add(zeile(null, ohneSatz));
    }
    return zeilen.size() > 1 ? List.copyOf(zeilen) : List.of();
  }

  /* Eine Zeile: Netto und Brutto Cent fuer Cent summiert, die Steuer als ihr Abstand. */
  private static Steuerzeile zeile(
      final @Nullable BigDecimal satz, final List<GestellteRechnung> gruppe) {
    final BigDecimal netto = Geldrechnung.summe(gruppe.stream().map(GestellteRechnung::netto));
    final BigDecimal brutto = Geldrechnung.summe(gruppe.stream().map(GestellteRechnung::brutto));
    return new Steuerzeile(satz, netto, brutto.subtract(netto));
  }
}
