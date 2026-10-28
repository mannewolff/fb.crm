package org.mwolff.fbcrm.common;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Eine gueltige IBAN (Plan #461, E15; fachliche Quelle #460, Kriterium 7).
 *
 * <p>Gueltig heisst: zwei Buchstaben, zwei Ziffern und eins bis dreissig Zeichen aus Buchstaben und
 * Ziffern, und die Pruefung nach Modulo 97 ergibt 1. Ob es die Bank oder das Konto gibt, prueft
 * fb.crm nicht.
 *
 * <p>Gespeichert und in die E-Rechnung geschrieben wird die elektronische Form ohne Leerzeichen,
 * gedruckt wird in Vierergruppen. Die Zerlegung des bisherigen Freitexts ({@link
 * Bankverbindungszerlegung}) prueft mit derselben Regel ({@link #gueltig(String)}).
 *
 * @param elektronisch die IBAN ohne Leerzeichen, in Grossbuchstaben
 */
public record Iban(String elektronisch) {

  private static final Pattern FORMAT = Pattern.compile("[A-Z]{2}[0-9]{2}[A-Z0-9]{1,30}");

  private static final Pattern LEERRAUM = Pattern.compile("\\s+");

  /** Die Laenge einer Gruppe in der gedruckten Form. */
  private static final int GRUPPE = 4;

  private static final int MODUL = 97;

  /** Die Basis, mit der ein Buchstabe als Zahl zaehlt: A ist 10, Z ist 35. */
  private static final int BUCHSTABENBASIS = 36;

  /**
   * Prueft die elektronische Form.
   *
   * @throws IllegalArgumentException wenn sie keine gueltige IBAN ist
   */
  public Iban {
    if (!gueltig(elektronisch)) {
      throw new IllegalArgumentException("Keine gueltige IBAN.");
    }
  }

  /**
   * Die IBAN zu einer Eingabe.
   *
   * @param text die IBAN, wie sie eingegeben wurde: mit oder ohne Leerzeichen, in beliebiger Gross-
   *     und Kleinschreibung
   * @return die IBAN
   * @throws IllegalArgumentException wenn der Text ohne Leerzeichen keine gueltige IBAN ist
   */
  public static Iban aus(final String text) {
    return new Iban(LEERRAUM.matcher(text).replaceAll("").toUpperCase(Locale.ROOT));
  }

  /**
   * Ob eine Zeichenfolge eine gueltige IBAN in elektronischer Form ist.
   *
   * @param zeichen die Zeichen ohne Leerzeichen, in Grossbuchstaben
   * @return {@code true}, wenn Format und Pruefziffer stimmen
   */
  public static boolean gueltig(final String zeichen) {
    return FORMAT.matcher(zeichen).matches() && rest(zeichen) == 1;
  }

  /*
   * Die ersten vier Zeichen wandern ans Ende, jeder Buchstabe zaehlt als zweistellige Zahl, und der
   * Rest nach Modulo 97 wird Ziffer fuer Ziffer gerechnet — die Zahl selbst passt in keinen long.
   */
  private static int rest(final String zeichen) {
    final String umgestellt = zeichen.substring(GRUPPE) + zeichen.substring(0, GRUPPE);
    int rest = 0;
    for (int i = 0; i < umgestellt.length(); i++) {
      final int wert = Character.digit(umgestellt.charAt(i), BUCHSTABENBASIS);
      rest = (wert < 10 ? rest * 10 : rest * 100) + wert;
      rest %= MODUL;
    }
    return rest;
  }

  /**
   * Die IBAN zum Drucken.
   *
   * @return die IBAN in Vierergruppen, getrennt durch je ein Leerzeichen; die letzte Gruppe darf
   *     kuerzer sein
   */
  public String gedruckt() {
    final StringBuilder gedruckt = new StringBuilder();
    for (int i = 0; i < elektronisch.length(); i += GRUPPE) {
      if (i > 0) {
        gedruckt.append(' ');
      }
      gedruckt.append(elektronisch, i, Math.min(i + GRUPPE, elektronisch.length()));
    }
    return gedruckt.toString();
  }
}
