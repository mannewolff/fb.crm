package org.mwolff.fbcrm.rechnung.application;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import org.mwolff.fbcrm.common.Feldfehler;
import org.mwolff.fbcrm.rechnung.application.Randgrenzen.Rand;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Ein Rand des Briefbogens laesst dem Inhalt keinen Platz mehr (Kriterium 13; Plan #363, E9).
 *
 * <p>Die Meldung haengt am Feld des geaenderten Randes und nennt den groessten Wert, den er bei den
 * uebrigen eingestellten Raendern haben darf — gefunden von {@link Randgrenzen#groesstZulaessig}
 * mit derselben Regel, die abgewiesen hat. Passt auch 0 nicht, gibt es keinen solchen Wert, und die
 * Meldung nennt stattdessen den Grund. Weil sie einen Wert nennt, entsteht sie zur Laufzeit und
 * steht nicht in {@code reason}.
 *
 * <p>Der Wert steht in Millimetern mit einer Nachkommastelle, so wie die Maske ihn zeigt und nimmt
 * (E8).
 *
 * <p>422 wie die uebrigen Feldfehler dieses Projekts: Der Rumpf ist wohlgeformt, abgewiesen wird er
 * fachlich.
 */
@ResponseStatus(code = HttpStatus.UNPROCESSABLE_ENTITY)
public final class RandNichtMoeglich extends Feldfehler {

  /** Der Grund, wenn bei den uebrigen Raendern kein Wert dieses Randes passt. */
  public static final String MELDUNG_KEIN_WERT =
      "Bei den übrigen eingestellten Rändern passt kein Wert dieses Randes mehr.";

  /** Zehntel-Millimeter je Millimeter. */
  private static final int ZEHNTEL = 10;

  /** Das Feld des geaenderten Randes. */
  private final String feld;

  /**
   * Weist den geaenderten Rand ab.
   *
   * @param rand der geaenderte Rand; an seinem Feld haengt die Meldung
   * @param grenze der groesste zulaessige Wert in Zehntel-Millimetern, oder leer, wenn auch 0 nicht
   *     passt
   */
  RandNichtMoeglich(final Rand rand, final OptionalInt grenze) {
    super(meldung(grenze));
    this.feld = rand.feld();
  }

  private static String meldung(final OptionalInt grenze) {
    if (grenze.isEmpty()) {
      return MELDUNG_KEIN_WERT;
    }
    final int zehntelMm = grenze.getAsInt();
    return "Bei den übrigen eingestellten Rändern darf dieser Rand höchstens "
        + zehntelMm / ZEHNTEL
        + ","
        + zehntelMm % ZEHNTEL
        + " mm betragen.";
  }

  @Override
  public Map<String, List<String>> felder() {
    return Map.of(feld, List.of(getMessage()));
  }
}
