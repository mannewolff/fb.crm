package org.mwolff.fbcrm.arbeitszeit.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Ein Tag der Monatsliste mit seinen Buchungen und seiner Summe (Issue #193, Kriterium 5).
 *
 * <p>Die Summe steht hier und wird nicht von der Ansicht gerechnet: Sie entsteht aus den Minuten
 * der Zeilen und erst danach aus der Umrechnung in Stunden ({@link
 * org.mwolff.fbcrm.arbeitszeit.domain.Zeiteintrag#stundenAus(long)}) — die Summe der gerundeten
 * Einzelwerte koennte um einen Rundungsrest daneben liegen.
 *
 * @param tag der Tag, an dem gearbeitet wurde
 * @param buchungen die Eintraege dieses Tages, nach Beginn aufsteigend
 * @param stunden die Summe des Tages in Stunden mit zwei Nachkommastellen
 */
public record Arbeitstag(LocalDate tag, List<Zeitbuchung> buchungen, BigDecimal stunden) {

  /** Nimmt die Buchungen als Kopie: Der Aufrufer darf seine Liste danach weiterverwenden. */
  public Arbeitstag {
    buchungen = List.copyOf(buchungen);
  }
}
