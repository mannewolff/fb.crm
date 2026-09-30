package org.mwolff.fbcrm.angebot.application;

import java.util.List;
import java.util.Map;
import org.mwolff.fbcrm.common.Feldfehler;
import org.mwolff.fbcrm.common.Uploadgrenze;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Die hochgeladene Datei ueberschreitet die Grenze aus {@link Uploadgrenze} (Issue #148, Kriterium
 * 6; Plan #150, E8).
 *
 * <p>Der dritte der drei Riegel und der letzte vor dem Objektspeicher: Die Maske prueft vor dem
 * Absenden, der Container weist einen zu grossen Rumpf ab, und diese Ausnahme haelt den
 * Anwendungsfall auch dann an, wenn er an beiden vorbei aufgerufen wird. Sie wirft, <b>bevor</b>
 * abgelegt wird — eine abgewiesene Datei soll keine Waise im Speicher hinterlassen.
 *
 * <p>Ein {@link Feldfehler} am Feld {@code datei} und nicht nur ein Statuscode: Die Maske liest
 * Feldmeldungen aus {@code fieldErrors} und setzt sie an das Dateifeld, das die Lage betrifft
 * (Muster {@link AnsprechpartnerNichtWaehlbar}).
 */
@ResponseStatus(code = HttpStatus.BAD_REQUEST, reason = Uploadgrenze.MELDUNG)
public final class AnlageZuGross extends Feldfehler {

  /** Das Feld, unter dem Maske und Schnittstelle die Datei fuehren. */
  public static final String FELD = "datei";

  @Override
  public Map<String, List<String>> felder() {
    return Map.of(FELD, List.of(Uploadgrenze.MELDUNG));
  }
}
