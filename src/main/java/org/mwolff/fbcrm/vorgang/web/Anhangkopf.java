package org.mwolff.fbcrm.vorgang.web;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Die Kopfzeilen, mit denen ein Anhang hinausgeht (E11).
 *
 * <p>Der Dateiname kommt von aussen (E13) und geht hier in eine HTTP-Kopfzeile. Ein Umlaut oder ein
 * Anfuehrungszeichen darin darf die Zeile weder zerreissen noch beim Empfaenger als etwas anderes
 * ankommen, als hochgeladen wurde. Deshalb zwei Formen nebeneinander, wie RFC 6266 es vorsieht:
 *
 * <ul>
 *   <li>{@code filename="…"} als Rueckfall fuer alte Empfaenger — reines druckbares ASCII, ohne
 *       Anfuehrungszeichen und ohne Rueckstrich, die beide die Zeile beenden koennten. Was nicht
 *       hineinpasst, wird zu {@code _}.
 *   <li>{@code filename*=UTF-8''…} mit der Prozentkodierung nach RFC 5987 — dort steht der Name
 *       vollstaendig, Umlaut und Anfuehrungszeichen eingeschlossen.
 * </ul>
 *
 * <p>Ein Empfaenger, der beide Formen kennt, nimmt die zweite; wer nur die erste kennt, bekommt
 * einen brauchbaren Namen statt einer kaputten Zeile.
 *
 * <p>Warum nicht {@code ContentDisposition} aus Spring: Dessen Rueckfall traegt den Namen
 * unveraendert im Anfuehrungszeichen-Paar, mit maskiertem Anfuehrungszeichen und rohen
 * UTF-8-Zeichen. Das ist kein ASCII-Rueckfall, sondern dieselbe Zeichenkette zweimal.
 */
final class Anhangkopf {

  /** Der Name der Kopfzeile, die die Sandbox-Regel traegt. */
  static final String INHALTSREGEL = "Content-Security-Policy";

  /**
   * Die Regel selbst: eigene Herkunft, kein Skript, keine Formulare, keine Navigation.
   *
   * <p>Die letzte Schranke, falls ein Empfaenger den Inhalt entgegen {@code Content-Disposition}
   * und {@code Content-Type} doch rendert.
   */
  static final String SANDKASTEN = "sandbox";

  /**
   * Alles, was druckbares ASCII ist, ausser {@code "} (0x22) und {@code \} (0x5C) — beide wuerden
   * das Anfuehrungszeichen-Paar des Rueckfalls von innen aufbrechen.
   */
  private static final Pattern NICHT_RUECKFALLFAEHIG =
      Pattern.compile("[^\\x20-\\x21\\x23-\\x5B\\x5D-\\x7E]");

  /** Die {@code attr-char} aus RFC 5987 — alles andere wird prozentkodiert. */
  private static final String UNVERAENDERT =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!#$&+-.^_`|~";

  private Anhangkopf() {}

  /**
   * Der Wert der Kopfzeile {@code Content-Disposition} fuer einen Anhang.
   *
   * @param dateiName der Name, unter dem der Empfaenger die Datei speichern soll
   */
  static String contentDisposition(final String dateiName) {
    return "attachment; filename=\""
        + NICHT_RUECKFALLFAEHIG.matcher(dateiName).replaceAll("_")
        + "\"; filename*=UTF-8''"
        + prozentkodiert(dateiName);
  }

  private static String prozentkodiert(final String dateiName) {
    final StringBuilder kodiert = new StringBuilder(dateiName.length());
    for (final byte roh : dateiName.getBytes(StandardCharsets.UTF_8)) {
      kodiert.append(zeichen(roh));
    }
    return kodiert.toString();
  }

  private static String zeichen(final byte roh) {
    final char wert = (char) (roh & 0xFF);
    return UNVERAENDERT.indexOf(wert) < 0
        ? String.format(Locale.ROOT, "%%%02X", Integer.valueOf(wert))
        : String.valueOf(wert);
  }
}
