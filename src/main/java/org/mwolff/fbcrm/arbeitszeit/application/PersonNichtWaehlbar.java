package org.mwolff.fbcrm.arbeitszeit.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Der Parameter {@code person} nennt nichts, was das anfragende Konto waehlen kann: weder das Wort
 * {@code alle} noch die Kennung eines Kontos, das es gibt (Plan #426, E10).
 *
 * <p>400 und nicht 404, weil der Wert ein Anfrageparameter ist und kein Pfad — dieselbe Lage wie
 * ein Monat, den es nicht gibt ({@code GlobalExceptionHandler#handleTypeMismatch}). Ein Employee
 * bekommt diesen Fehler nie: Fuer ihn wertet der Anwendungsfall den Parameter gar nicht aus.
 */
@ResponseStatus(code = HttpStatus.BAD_REQUEST, reason = "Diese Person steht nicht zur Wahl.")
public final class PersonNichtWaehlbar extends RuntimeException {

  /** Ohne Ursache: Der Wert war eine Kennung, aber zu keinem Konto. */
  public PersonNichtWaehlbar() {
    super();
  }

  /**
   * Mit Ursache: Der Wert liess sich nicht als Kennung lesen.
   *
   * @param ursache der Fehler beim Lesen der Kennung
   */
  public PersonNichtWaehlbar(final Throwable ursache) {
    super(ursache);
  }
}
