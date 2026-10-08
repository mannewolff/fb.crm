package org.mwolff.fbcrm.angebot.domain;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

/**
 * Die Art einer Anlage, soweit sie sich im Browser ansehen laesst (Issue #148, Kriterium 15; Plan
 * #150, E4).
 *
 * <p>Erkannt wird <b>am Inhalt</b>, an den ersten Bytes der Datei. Weder der Dateiname noch die vom
 * Browser gemeldete Art gehen ein: Beides ist eine Eingabe von aussen, und eine HTML-Datei, die
 * {@code bericht.pdf} heisst, ist kein PDF. Das Ergebnis wird beim Hochladen einmal festgestellt
 * und gespeichert; die Auslieferung nennt danach genau {@link #mimeTyp()} und sonst nichts.
 *
 * <p>Fuenf Signaturen rechtfertigen keine Bibliothek zur Inhaltserkennung (CLAUDE-react.md,
 * „Verbotene Muster": neue Dependencies aus Bequemlichkeit) — sie stehen hier ausgeschrieben.
 */
public enum Vorschauart {

  /** Portable Network Graphics. */
  PNG("image/png"),

  /** JPEG, in jeder Rahmung (JFIF, Exif). */
  JPEG("image/jpeg"),

  /** Graphics Interchange Format, Fassung 87a und 89a. */
  GIF("image/gif"),

  /** WebP — ein RIFF-Behaelter mit der Kennung {@code WEBP}. */
  WEBP("image/webp"),

  /** Portable Document Format. */
  PDF("application/pdf");

  private static final byte[] PNG_SIGNATUR = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

  private static final byte[] JPEG_SIGNATUR = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

  private static final byte[] GIF87_SIGNATUR = ascii("GIF87a");

  private static final byte[] GIF89_SIGNATUR = ascii("GIF89a");

  private static final byte[] RIFF_SIGNATUR = ascii("RIFF");

  private static final byte[] WEBP_KENNUNG = ascii("WEBP");

  /** Zwischen {@code RIFF} und {@code WEBP} stehen vier Byte Laenge. */
  private static final int WEBP_KENNUNG_AB = 8;

  private static final byte[] PDF_SIGNATUR = ascii("%PDF-");

  /**
   * So viele Bytes vom Anfang des Inhalts reichen fuer jede der fuenf Signaturen.
   *
   * <p>Der Anwendungsfall liest genau so viele und reicht sie an {@link #erkannt(byte[])} — die
   * laengste Pruefung ist WebP mit der Kennung ab Byte acht.
   */
  public static final int SIGNATUR_BYTES = WEBP_KENNUNG_AB + 4;

  /*
   * Das Feld heisst nicht wie sein Leser: PMD (AvoidFieldNameMatchingMethodName) beanstandet den
   * gleichen Namen, und ein Record ist hier nicht moeglich — dies ist ein Aufzaehlungstyp.
   */
  private final String mime;

  Vorschauart(final String mime) {
    this.mime = mime;
  }

  private static byte[] ascii(final String zeichen) {
    return zeichen.getBytes(StandardCharsets.US_ASCII);
  }

  /** Der MIME-Typ, unter dem eine Anlage dieser Art ausgeliefert wird. */
  public String mimeTyp() {
    return mime;
  }

  /**
   * Die Art, die zu den ersten Bytes des Inhalts passt, oder leer.
   *
   * <p>Leer heisst „keine Vorschau": Die Anlage bleibt gueltig, sie laesst sich nur nicht ansehen.
   * Ein zu kurzer Inhalt ergibt ebenso nichts — eine halbe Signatur ist keine.
   *
   * @param anfang die ersten Bytes des Inhalts; {@link #SIGNATUR_BYTES} genuegen, mehr schadet
   *     nicht
   */
  public static Optional<Vorschauart> erkannt(final byte[] anfang) {
    if (beginntMit(anfang, PNG_SIGNATUR)) {
      return Optional.of(PNG);
    }
    if (beginntMit(anfang, JPEG_SIGNATUR)) {
      return Optional.of(JPEG);
    }
    if (beginntMit(anfang, GIF87_SIGNATUR) || beginntMit(anfang, GIF89_SIGNATUR)) {
      return Optional.of(GIF);
    }
    if (beginntMit(anfang, RIFF_SIGNATUR) && stehtAb(anfang, WEBP_KENNUNG_AB, WEBP_KENNUNG)) {
      return Optional.of(WEBP);
    }
    if (beginntMit(anfang, PDF_SIGNATUR)) {
      return Optional.of(PDF);
    }
    return Optional.empty();
  }

  private static boolean beginntMit(final byte[] inhalt, final byte[] signatur) {
    return stehtAb(inhalt, 0, signatur);
  }

  private static boolean stehtAb(final byte[] inhalt, final int ab, final byte[] signatur) {
    // Die Laengenpruefung steht vor dem Vergleich und kuerzt ihn ab, weil Arrays.equals mit einem
    // Bereich ausserhalb des Feldes wirft; ein zu kurzer Inhalt ist hier aber kein Fehler, sondern
    // ein Nein.
    return inhalt.length >= ab + signatur.length
        && Arrays.equals(inhalt, ab, ab + signatur.length, signatur, 0, signatur.length);
  }
}
