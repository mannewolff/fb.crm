package org.mwolff.fbcrm.arbeitszeit.application;

import java.util.List;
import java.util.Map;
import org.mwolff.fbcrm.common.Feldfehler;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Das eingereichte Ende liegt nicht nach dem Beginn (Plan #194, A19).
 *
 * <p>Die Gleichheit ist schon zu wenig: Ein Eintrag von 9:00 bis 9:00 dauert nichts. Die kuerzeste
 * erfassbare Zeit ist damit eine Viertelstunde, und das spaeteste Ende 23:45 — eine eigene
 * Obergrenze braucht es dafuer nicht (E6).
 *
 * <p>Die Meldung haengt am Ende und nicht am Beginn: Der Beginn ist gesetzt, und wer das Ende
 * eintraegt, soll dort lesen, dass es zu frueh liegt. Dieselben Erwaegungen zu 422 und zum {@link
 * Feldfehler} wie bei {@link UhrzeitNichtImRaster}.
 */
@ResponseStatus(code = HttpStatus.UNPROCESSABLE_ENTITY, reason = EndeVorBeginn.MELDUNG)
public final class EndeVorBeginn extends Feldfehler {

  /** Was der Anwender am Feld liest — derselbe Satz, den die Antwort als {@code detail} traegt. */
  public static final String MELDUNG = "Das Ende muss nach dem Beginn liegen.";

  /** Das Feld, unter dem die Maske das Ende fuehrt. */
  public static final String FELD = "bis";

  @Override
  public Map<String, List<String>> felder() {
    return Map.of(FELD, List.of(MELDUNG));
  }
}
