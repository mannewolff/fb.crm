package org.mwolff.fbcrm.arbeitszeit.application;

import java.util.List;
import java.util.Map;
import org.mwolff.fbcrm.common.Feldfehler;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Eine eingereichte Uhrzeit liegt nicht auf einer Viertelstunde (Issue #193, Antwort 6; Plan #194,
 * A19).
 *
 * <p><b>Warum der Anwendungsfall das prueft und nicht nur die Datenbank.</b> Das Zeitfeld des
 * Browsers mit Schritt 15 Minuten verhindert das Eintippen von 9:10 nicht, und Kriterium 3 verlangt
 * die Meldung am Feld. Ohne diese Pruefung kaeme der Verstoss als Serverfehler oder als
 * Datenbankfehler an. Der CHECK der Tabelle und die Invariante von {@code Zeiteintrag} bleiben die
 * letzte Sicherung fuer jeden Weg, der nicht durch den Anwendungsfall fuehrt.
 *
 * <p>422 und nicht 400: Die Form der Anfrage ist in Ordnung — „09:10" ist eine gueltige Uhrzeit —,
 * sie ist nur fachlich nicht verarbeitbar. Ein {@link Feldfehler} und nicht nur ein Status, weil
 * die Maske ihre Meldungen aus {@code fieldErrors} liest.
 *
 * <p><b>Das Feld steht nicht fest:</b> Verletzt der Beginn das Raster, haengt die Meldung an {@code
 * von}, verletzt es das Ende, an {@code bis}. Darum die beiden Erzeuger — eine freie Zeichenkette
 * als Feldname liesse jeden anderen Namen auch zu.
 */
@ResponseStatus(code = HttpStatus.UNPROCESSABLE_ENTITY, reason = UhrzeitNichtImRaster.MELDUNG)
public final class UhrzeitNichtImRaster extends Feldfehler {

  /** Was der Anwender am Feld liest — derselbe Satz, den die Antwort als {@code detail} traegt. */
  public static final String MELDUNG = "Arbeitszeit wird in Schritten einer Viertelstunde erfasst.";

  /** Das Feld, unter dem die Maske den Beginn fuehrt. */
  public static final String FELD_VON = "von";

  /** Das Feld, unter dem die Maske das Ende fuehrt. */
  public static final String FELD_BIS = "bis";

  private final String feld;

  private UhrzeitNichtImRaster(final String feld) {
    super();
    this.feld = feld;
  }

  /** Der Beginn liegt nicht im Raster. */
  public static UhrzeitNichtImRaster anVon() {
    return new UhrzeitNichtImRaster(FELD_VON);
  }

  /** Das Ende liegt nicht im Raster. */
  public static UhrzeitNichtImRaster anBis() {
    return new UhrzeitNichtImRaster(FELD_BIS);
  }

  @Override
  public Map<String, List<String>> felder() {
    return Map.of(feld, List.of(MELDUNG));
  }
}
