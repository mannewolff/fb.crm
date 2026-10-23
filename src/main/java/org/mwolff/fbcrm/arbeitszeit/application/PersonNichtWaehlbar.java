package org.mwolff.fbcrm.arbeitszeit.application;

import java.util.List;
import java.util.Map;
import org.mwolff.fbcrm.common.Feldfehler;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Fuer die genannte Person darf das anfragende Konto keine Zeit schreiben (Plan #426, E12, E19;
 * fachliche Quelle #410, Kriterien 2 bis 4 und 9).
 *
 * <p><b>Eine Lage fuer „nicht erlaubt", „stillgelegt" und „unbekannt"</b> (E12): Ein Employee nennt
 * ein anderes Konto als sein eigenes, ein C-Level nennt als neue Person ein stillgelegtes oder
 * eines, das es nicht gibt, oder ein ReadOnly-Konto will ueberhaupt erfassen. Der Anwender soll
 * alle Faelle gleich erfahren, und keiner verraet einem Employee, welche Kennungen es gibt —
 * dieselbe Begruendung wie bei {@code angebot.application.AnsprechpartnerNichtWaehlbar}.
 *
 * <p>422 und nicht 404: Die Kennung kommt im Rumpf und nicht im Pfad. Ein {@link Feldfehler}, weil
 * die Meldung an das Feld gehoert, an dem ein C-Level die Person waehlt (Plan #194, A19).
 *
 * <p>Nicht zu verwechseln mit {@link PersonenauswahlUngueltig}: Die gilt dem Parameter {@code
 * person} der Monatsliste, also dem Lesen.
 */
@ResponseStatus(code = HttpStatus.UNPROCESSABLE_ENTITY, reason = PersonNichtWaehlbar.MELDUNG)
public final class PersonNichtWaehlbar extends Feldfehler {

  /** Was der Anwender am Feld liest — derselbe Satz, den die Antwort als {@code detail} traegt. */
  public static final String MELDUNG = "Für diese Person kann hier keine Zeit erfasst werden.";

  /** Das Feld, unter dem die Maske die Wahl der Person fuehrt. */
  public static final String FELD = "accountId";

  @Override
  public Map<String, List<String>> felder() {
    return Map.of(FELD, List.of(MELDUNG));
  }
}
