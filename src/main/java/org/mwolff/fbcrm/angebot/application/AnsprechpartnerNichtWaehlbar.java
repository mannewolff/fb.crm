package org.mwolff.fbcrm.angebot.application;

import java.util.List;
import java.util.Map;
import org.mwolff.fbcrm.common.Feldfehler;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Der genannte Ansprechpartner steht fuer dieses Angebot nicht zur Wahl (Issue #126).
 *
 * <p>Das gilt fuer eine unbekannte Kennung ebenso wie fuer einen Ansprechpartner einer anderen
 * Firma oder einen stillgelegten: Der Anwender soll alle drei Faelle gleich erfahren, und keiner
 * davon verraet etwas ueber fremde Firmen.
 *
 * <p>422 und nicht 404: Die Kennung kommt im Rumpf und nicht im Pfad — die angefragte Ressource ist
 * die Angebotsliste der Firma, und die Eingabe zu ihr ist wohlgeformt, aber fachlich nicht
 * verarbeitbar.
 *
 * <p><b>Ein {@link Feldfehler} und nicht nur ein Status</b> (Issue #138): Die Lage betrifft genau
 * ein Feld der Maske, und die Maske liest Feldmeldungen bereits aus {@code fieldErrors}. Ohne die
 * Feldliste fand sie zu diesem 422 keine Meldung und zeigte statt der Aussage zum Ansprechpartner
 * nur den allgemeinen Satz, das Angebot sei nicht gespeichert worden. Statuscode und Text kommen
 * unveraendert aus {@code @ResponseStatus}; {@code common.web.GlobalExceptionHandler} setzt die
 * Feldliste dazu.
 */
@ResponseStatus(
    code = HttpStatus.UNPROCESSABLE_ENTITY,
    reason = AnsprechpartnerNichtWaehlbar.MELDUNG)
public final class AnsprechpartnerNichtWaehlbar extends Feldfehler {

  /** Was der Anwender am Feld liest — derselbe Satz, den die Antwort als {@code detail} traegt. */
  public static final String MELDUNG =
      "Dieser Ansprechpartner steht für das Angebot nicht zur Wahl.";

  /** Das Feld, unter dem die Maske die Wahl des Ansprechpartners fuehrt. */
  public static final String FELD = "ansprechpartnerId";

  @Override
  public Map<String, List<String>> felder() {
    return Map.of(FELD, List.of(MELDUNG));
  }
}
