package org.mwolff.fbcrm.startseite.application;

import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.Optional;

/**
 * Der gewaehlte Zeitraum der Startseite: ein Monat oder ein Jahr (#273, Kriterien 1 und 2; Plan
 * #274, E1).
 *
 * <p>Ein Summentyp und keine Zeichenkette mit Formatregel: Die Ansicht schaltet an der Art drei
 * Beschriftungen und den Inhalt einer Karte um, und weil der Typ versiegelt ist, verlangt der
 * Compiler bei jeder Verzweigung ueber die beiden Arten eine Entscheidung.
 *
 * <p><b>Was eine Zeichenkette zum Zeitraum macht, entscheidet der Server</b> in {@link
 * #aus(String)} (E15): {@code JJJJ-MM} mit einem Monat von 01 bis 12 ist ein Monat, {@code JJJJ}
 * ein Jahr, alles andere keiner. Ob der Zeitraum auch zur Wahl steht, sagt erst {@link
 * WaehlbareZeitraeume}.
 */
public sealed interface Zeitraum {

  /**
   * Der erste Tag des Zeitraums, etwa fuer die Frage an die Zeiterfassung.
   *
   * @return der erste Tag, eingeschlossen
   */
  LocalDate von();

  /**
   * Der letzte Tag des Zeitraums.
   *
   * @return der letzte Tag, eingeschlossen
   */
  LocalDate bis();

  /**
   * Ob ein Monat in diesem Zeitraum liegt, etwa fuer die Verdichtung der Rechnungen je Monat.
   *
   * @param monat der zu pruefende Monat
   * @return {@code true}, wenn der Monat ganz in diesem Zeitraum liegt
   */
  boolean enthaelt(YearMonth monat);

  /**
   * Der Zeitraum als Wert des Adressparameters: {@code "2026-10"} oder {@code "2026"}.
   *
   * @return die Form, die {@link #aus(String)} wieder zu diesem Zeitraum macht
   */
  String wert();

  /**
   * Der Zeitraum zu einem Adressparameter (E15).
   *
   * @param text der Wert, wie er kam
   * @return ein Monat fuer {@code JJJJ-MM} mit Monat 01 bis 12, ein Jahr fuer {@code JJJJ}, sonst
   *     leer
   */
  static Optional<Zeitraum> aus(final String text) {
    // \d ist ohne UNICODE_CHARACTER_CLASS nur [0-9]; parseInt sieht darum nie eine fremde Ziffer.
    if (text.matches("\\d{4}")) {
      return Optional.of(new Jahr(Year.of(Integer.parseInt(text))));
    }
    if (!text.matches("\\d{4}-\\d{2}")) {
      return Optional.empty();
    }
    final int nummer = Integer.parseInt(text.substring(5));
    if (nummer < 1 || nummer > 12) {
      return Optional.empty();
    }
    return Optional.of(new Monat(YearMonth.of(Integer.parseInt(text.substring(0, 4)), nummer)));
  }

  /**
   * Ein Kalendermonat.
   *
   * @param monat der Monat
   */
  record Monat(YearMonth monat) implements Zeitraum {

    @Override
    public LocalDate von() {
      return monat.atDay(1);
    }

    @Override
    public LocalDate bis() {
      return monat.atEndOfMonth();
    }

    @Override
    public boolean enthaelt(final YearMonth anderer) {
      return monat.equals(anderer);
    }

    @Override
    public String wert() {
      return monat.toString();
    }
  }

  /**
   * Ein Geschaeftsjahr, und das ist das Kalenderjahr vom 1. Januar bis zum 31. Dezember (#273,
   * Ziel).
   *
   * @param jahr das Jahr
   */
  record Jahr(Year jahr) implements Zeitraum {

    @Override
    public LocalDate von() {
      return jahr.atDay(1);
    }

    @Override
    public LocalDate bis() {
      return jahr.atMonth(12).atEndOfMonth();
    }

    @Override
    public boolean enthaelt(final YearMonth monat) {
      return jahr.getValue() == monat.getYear();
    }

    @Override
    public String wert() {
      return jahr.toString();
    }
  }
}
