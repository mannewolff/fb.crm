package org.mwolff.fbcrm.angebot.application;

import java.util.List;
import java.util.Map;
import org.mwolff.fbcrm.common.Feldfehler;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Die eingereichte Positionsliste beruft sich auf eine Kennung, die dieses Angebot nicht hat (Plan
 * #169, E2).
 *
 * <p>Eine Position mit Kennung sagt „dieselbe Position wie vorher". Gueltig sind darum nur die
 * Kennungen der Positionen des Angebots, das geaendert wird — und jede hoechstens einmal. Beide
 * Verletzungen erfaehrt der Anwender gleich: Eine Kennung aus einem fremden Angebot verriet sonst,
 * dass es sie gibt, und wer zweimal dieselbe Kennung einreicht, hat ebenso eine Liste geschickt,
 * die nicht zu diesem Angebot passt.
 *
 * <p>422 und nicht 404: Die Kennungen kommen im Rumpf und nicht im Pfad — die angefragte Ressource
 * ist das Angebot, und die Eingabe zu ihm ist wohlgeformt, aber fachlich nicht verarbeitbar. Ein
 * {@link Feldfehler} und nicht nur ein Status, aus demselben Grund wie bei {@link
 * AnsprechpartnerNichtWaehlbar} (Issue #138): Die Maske liest Feldmeldungen aus {@code fieldErrors}
 * und fand ohne die Feldliste zu diesem 422 keine Meldung.
 *
 * <p>Abgewiesen wird <b>vor</b> dem Schreiben: Ein abgewiesenes Aendern hinterlaesst nichts. Der
 * Bestand verlaesst sich darauf — er wirft bei einer fremden Kennung eine {@code
 * IllegalArgumentException}, weil sie dort nur noch ein Programmierfehler sein kann.
 */
@ResponseStatus(code = HttpStatus.UNPROCESSABLE_ENTITY, reason = PositionenNichtWaehlbar.MELDUNG)
public final class PositionenNichtWaehlbar extends Feldfehler {

  /** Was der Anwender am Feld liest — derselbe Satz, den die Antwort als {@code detail} traegt. */
  public static final String MELDUNG =
      "Die eingereichten Positionen passen nicht zu diesem Angebot.";

  /** Das Feld, unter dem die Maske die Positionsliste fuehrt. */
  public static final String FELD = "positionen";

  @Override
  public Map<String, List<String>> felder() {
    return Map.of(FELD, List.of(MELDUNG));
  }
}
