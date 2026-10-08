package org.mwolff.fbcrm.angebot.application;

import java.util.List;
import java.util.Map;
import org.mwolff.fbcrm.common.Feldfehler;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Die Art des Angebots soll wechseln, obwohl daraus schon eine Rechnung entstanden ist (Issue #227,
 * Kriterium 8 von #207).
 *
 * <p>Setzen und Entfernen des Kennzeichens sind frei, solange noch keine Rechnung besteht. Danach
 * nicht mehr: Die interne Arbeit wird nie abgerechnet, und ein internes Angebot mit einer Rechnung
 * darauf waere ein widerspruechlicher Satz. Der Entwurf zaehlt dabei wie die gestellte Rechnung —
 * ein Entwurf auf ein Angebot ohne Preise waere derselbe Widerspruch.
 *
 * <p>422 und nicht 409 (E20): Der Wert kommt aus einem Feld der Maske, und die Meldung gehoert an
 * dieses Feld — die Angebotsansicht zeigt ihre Rechnungen auf derselben Seite, ein Wettlauf bleibt
 * damit sichtbar. Ein {@link Feldfehler} und nicht nur ein Status, aus demselben Grund wie bei
 * {@link AnsprechpartnerNichtWaehlbar} (Issue #138): Die Maske liest Feldmeldungen aus {@code
 * fieldErrors}.
 *
 * <p>Abgewiesen wird <b>vor</b> dem Schreiben: Ein abgewiesenes Aendern hinterlaesst nichts.
 */
@ResponseStatus(code = HttpStatus.UNPROCESSABLE_ENTITY, reason = KennzeichenNichtAenderbar.MELDUNG)
public final class KennzeichenNichtAenderbar extends Feldfehler {

  /** Was der Anwender am Feld liest — derselbe Satz, den die Antwort als {@code detail} traegt. */
  public static final String MELDUNG = "Aus dem Angebot ist schon eine Rechnung entstanden.";

  /** Das Feld, unter dem die Maske die Art des Angebots fuehrt. */
  public static final String FELD = "intern";

  @Override
  public Map<String, List<String>> felder() {
    return Map.of(FELD, List.of(MELDUNG));
  }
}
