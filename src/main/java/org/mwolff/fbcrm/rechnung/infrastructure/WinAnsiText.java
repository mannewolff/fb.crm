package org.mwolff.fbcrm.rechnung.infrastructure;

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;

/**
 * Bringt einen Text in die Zeichen, die die Standardschrift des Belegs schreiben kann.
 *
 * <p>Geschrieben wird mit Helvetica aus den 14 Standardschriften; ihre WinAnsi-Kodierung deckt die
 * Umlaute und das Eurozeichen des deutschen Belegs ab, ein Emoji oder ein chinesisches Zeichen aber
 * nicht. Ohne Ersatz liesse ein solches Zeichen in einem Positionstext das Schreiben scheitern —
 * und ein Beleg, der wegen eines Zeichens gar nicht entsteht, waere schlechter als einer mit einem
 * Fragezeichen darin. Steuerzeichen haben auf dem Blatt ebenso keine Gestalt.
 */
final class WinAnsiText {

  /** Die Kodierung, mit der Helvetica aus den Standardschriften geschrieben wird. */
  private static final Charset WIN_ANSI = Charset.forName("windows-1252");

  /** Steht fuer jedes Zeichen, das die Kodierung nicht darstellen kann. */
  private static final String ERSATZZEICHEN = "?";

  private WinAnsiText() {}

  /**
   * Ersetzt jedes Zeichen, das die Schrift nicht schreiben kann, durch ein Fragezeichen.
   *
   * @param text der gesetzte Text
   * @return derselbe Text, in darstellbaren Zeichen
   */
  static String darstellbar(final String text) {
    final CharsetEncoder kodierer = WIN_ANSI.newEncoder();
    final StringBuilder gesaeubert = new StringBuilder(text.length());
    text.codePoints()
        .forEach(
            zeichen ->
                gesaeubert.append(
                    darstellbar(kodierer, zeichen) ? Character.toString(zeichen) : ERSATZZEICHEN));
    return gesaeubert.toString();
  }

  private static boolean darstellbar(final CharsetEncoder kodierer, final int zeichen) {
    return !Character.isISOControl(zeichen) && kodierer.canEncode(Character.toString(zeichen));
  }
}
