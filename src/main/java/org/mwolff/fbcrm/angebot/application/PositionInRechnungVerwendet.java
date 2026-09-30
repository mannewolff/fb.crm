package org.mwolff.fbcrm.angebot.application;

import java.util.List;
import java.util.Map;
import org.mwolff.fbcrm.common.Feldfehler;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Eine Position, die in einer Rechnung steht, soll entfallen oder ihre Art wechseln (#160,
 * Kriterium 28).
 *
 * <p>Gebunden ist nur, was die Rechnung traegt: Die Position muss in der Liste bleiben, und ihre
 * Einheit und ihre Abrechnungsart bleiben, wie sie sind. Text, Menge, Preis und die Reihenfolge
 * bleiben frei — was die Rechnung davon braucht, hat sie beim Anlegen festgehalten (Kriterium 9).
 *
 * <p><b>Die Meldung nennt die Position beim Namen.</b> Ein Angebot kann viele Positionen haben, und
 * eine Meldung ohne Namen liesse den Anwender raten, welche er zurueckholen muss. Der Name kommt
 * aus dem <em>gespeicherten</em> Angebot und nicht aus der Einreichung: Die Position, um die es
 * geht, steht dort moeglicherweise gar nicht mehr.
 *
 * <p>422 und nicht 404 oder 409: Die Positionsliste kommt im Rumpf und nicht im Pfad — die
 * angefragte Ressource ist das Angebot, und die Eingabe zu ihm ist wohlgeformt, aber fachlich nicht
 * verarbeitbar. Ein {@link Feldfehler} und nicht nur ein Status, aus demselben Grund wie bei {@link
 * PositionenNichtWaehlbar}: Die Maske liest Feldmeldungen aus {@code fieldErrors}.
 *
 * <p>Abgewiesen wird <b>vor</b> dem Schreiben: Ein abgewiesenes Aendern hinterlaesst nichts.
 */
@ResponseStatus(code = HttpStatus.UNPROCESSABLE_ENTITY)
public final class PositionInRechnungVerwendet extends Feldfehler {

  /** Das Feld, unter dem die Maske die Positionsliste fuehrt. */
  public static final String FELD = "positionen";

  private final String bezeichnung;

  /**
   * @param bezeichnung die Bezeichnung der gebundenen Position, wie das Angebot sie gespeichert hat
   */
  PositionInRechnungVerwendet(final String bezeichnung) {
    super();
    this.bezeichnung = bezeichnung;
  }

  /*
   * Die Meldung entsteht hier und nicht als @ResponseStatus(reason = ...): Sie nennt die Position
   * beim Namen und ist darum nicht fuer alle Faelle dieselbe. Der GlobalExceptionHandler nimmt
   * getMessage() als detail der Antwort, wenn es eines gibt — so traegt dieselbe Stelle beides.
   */
  @Override
  public String getMessage() {
    return "Die Position „"
        + bezeichnung
        + "“ steht in einer Rechnung: Sie muss erhalten bleiben, und ihre Einheit und ihre"
        + " Abrechnungsart bleiben, wie sie sind.";
  }

  @Override
  public Map<String, List<String>> felder() {
    return Map.of(FELD, List.of(getMessage()));
  }
}
