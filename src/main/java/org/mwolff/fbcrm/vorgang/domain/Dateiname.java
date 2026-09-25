package org.mwolff.fbcrm.vorgang.domain;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Saeuberung des Dateinamens, der mit einem Anhang aus der Anfrage kommt (E13).
 *
 * <p>Der Name ist eine Eingabe von aussen und geht spaeter in eine HTTP-Kopfzeile und in das
 * Dateisystem des Benutzers. Deshalb faellt alles bis zum letzten {@code /} oder {@code \} weg —
 * ein Browser schickt bei manchen Betriebssystemen den ganzen Pfad —, Steuerzeichen werden
 * entfernt, und der Rest wird auf die Spaltenbreite von 255 Zeichen begrenzt.
 *
 * <p>Bleibt nichts uebrig, meldet die Funktion das dem Aufrufer als leeres {@link Optional}. Sie
 * wirft nicht: Ob ein unbrauchbarer Name eine Feldmeldung oder etwas anderes wird, entscheidet die
 * Schicht darueber, nicht die Domaene.
 */
public final class Dateiname {

  /** Die Spaltenbreite von {@code vorgang_eintrag.datei_name}. */
  private static final int MAX_LAENGE = 255;

  private static final Pattern STEUERZEICHEN = Pattern.compile("\\p{Cc}");

  private Dateiname() {}

  /**
   * Der gesaeuberte Name, oder leer, wenn davon nichts uebrig bleibt.
   *
   * @param roh der Name, wie er mit der Anfrage kam
   */
  public static Optional<String> gesaeubert(final String roh) {
    final int letzterTrenner = Math.max(roh.lastIndexOf('/'), roh.lastIndexOf('\\'));
    final String ohnePfad = roh.substring(letzterTrenner + 1);
    final String ohneSteuerzeichen = STEUERZEICHEN.matcher(ohnePfad).replaceAll("");
    final String gekuerzt =
        ohneSteuerzeichen.length() > MAX_LAENGE
            ? ohneSteuerzeichen.substring(0, MAX_LAENGE)
            : ohneSteuerzeichen;
    final String getrimmt = gekuerzt.strip();
    return getrimmt.isEmpty() ? Optional.empty() : Optional.of(getrimmt);
  }
}
