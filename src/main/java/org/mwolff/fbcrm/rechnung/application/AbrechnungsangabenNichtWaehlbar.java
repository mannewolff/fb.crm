package org.mwolff.fbcrm.rechnung.application;

import java.util.List;
import java.util.Map;
import org.mwolff.fbcrm.common.Feldfehler;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Die eingereichten Angaben passen nicht zu diesem Entwurf (#160, Kriterien 4 und 9).
 *
 * <p>Zwei Verletzungen, eine Meldung: eine Angabe zu einer Position, die nicht zum Angebot dieser
 * Rechnung gehoert, und eine negative Menge. Beide sagen dasselbe — die eingereichte Positionsliste
 * ist keine Liste zu diesem Entwurf —, und eine fremde Kennung getrennt zu melden verriete, dass es
 * sie gibt (dieselbe Ueberlegung wie in {@code angebot.application.PositionenNichtWaehlbar}).
 *
 * <p>422 und nicht 404: Die Angaben kommen im Rumpf und nicht im Pfad; die angefragte Ressource ist
 * die Rechnung, und die Eingabe zu ihr ist wohlgeformt, aber fachlich nicht verarbeitbar. Ein
 * {@link Feldfehler} und nicht nur ein Status, damit die Maske die Meldung an der Positionsliste
 * zeigen kann.
 *
 * <p>Abgewiesen wird <b>vor</b> dem Schreiben: Eine abgewiesene Aenderung hinterlaesst nichts.
 */
@ResponseStatus(
    code = HttpStatus.UNPROCESSABLE_ENTITY,
    reason = AbrechnungsangabenNichtWaehlbar.MELDUNG)
public final class AbrechnungsangabenNichtWaehlbar extends Feldfehler {

  /** Was der Anwender am Feld liest — derselbe Satz, den die Antwort als {@code detail} traegt. */
  public static final String MELDUNG =
      "Die eingereichten Positionen passen nicht zu diesem Entwurf.";

  /** Das Feld, unter dem die Maske die Positionsliste fuehrt. */
  public static final String FELD = "positionen";

  @Override
  public Map<String, List<String>> felder() {
    return Map.of(FELD, List.of(MELDUNG));
  }
}
