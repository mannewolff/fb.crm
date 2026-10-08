package org.mwolff.fbcrm.angebot.domain;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Saeuberung des Dateinamens, der mit einer Anlage aus der Anfrage kommt (Plan #150, E8).
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

  /** Die Spaltenbreite von {@code angebot_anlage.datei_name}. */
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
    // Ohne Verzweigung, und das ist Absicht: Als Bedingung war die Laengengrenze nicht pruefbar —
    // bei genau 255 Zeichen liefern "kuerzen" und "nicht kuerzen" dieselbe Zeichenkette, und ein
    // Mutationstest, der die Grenze um eins verschiebt, blieb darum unentdeckt (PIT, Issue #64).
    final String gekuerzt =
        ohneSteuerzeichen.substring(0, Math.min(ohneSteuerzeichen.length(), MAX_LAENGE));
    final String getrimmt = gekuerzt.strip();
    return getrimmt.isEmpty() ? Optional.empty() : Optional.of(getrimmt);
  }
}
