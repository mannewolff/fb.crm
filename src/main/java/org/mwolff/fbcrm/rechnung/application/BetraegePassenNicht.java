package org.mwolff.fbcrm.rechnung.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.mwolff.fbcrm.common.Feldfehler;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Der Bruttobetrag einer nachgetragenen Rechnung liegt unter ihrem Nettobetrag (#254, Kriterium 3;
 * Plan #259, E9).
 *
 * <p>Die Meldung steht am Bruttobetrag, wie Kriterium 3 es verlangt. Das Aggregat haelt dieselbe
 * Regel als Invariante und wirft {@link IllegalArgumentException}; der Anwendungsfall prueft
 * vorher, damit der Anwender eine Meldung am Feld bekommt und keinen Programmierfehler.
 *
 * <p>422 wie die uebrigen Feldfehler dieses Projekts (E23).
 */
@ResponseStatus(code = HttpStatus.UNPROCESSABLE_ENTITY, reason = BetraegePassenNicht.MELDUNG)
public final class BetraegePassenNicht extends Feldfehler {

  /** Was der Anwender am Feld liest — derselbe Satz, den die Antwort als {@code detail} traegt. */
  public static final String MELDUNG =
      "Der Bruttobetrag darf nicht kleiner als der Nettobetrag sein.";

  /** Das Feld, unter dem die Maske den Bruttobetrag fuehrt. */
  public static final String FELD = "brutto";

  /**
   * Weist Brutto unter Netto ab; gleiche Betraege sind zulaessig.
   *
   * @param netto der eingereichte Nettobetrag
   * @param brutto der eingereichte Bruttobetrag
   * @throws BetraegePassenNicht wenn {@code brutto} kleiner als {@code netto} ist
   */
  static void pruefe(final BigDecimal netto, final BigDecimal brutto) {
    if (brutto.compareTo(netto) < 0) {
      throw new BetraegePassenNicht();
    }
  }

  @Override
  public Map<String, List<String>> felder() {
    return Map.of(FELD, List.of(MELDUNG));
  }
}
