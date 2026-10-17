package org.mwolff.fbcrm.angebot.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Das Angebot laesst sich aus seinem Zustand nicht stornieren (Issue #334, Plan #332 E16, E17).
 *
 * <p>Zwei Lagen tragen denselben Satz: Das Angebot ist schon storniert — ein zweites Storno ist
 * kein Uebergang und ueberschriebe den vorhandenen Grund (E17) —, oder sein Status gehoert nicht zu
 * „angelegt", „abgegeben" und „bestellt" (#330, Kriterium 1; E3). Die Oberflaeche bietet die Taste
 * dort gar nicht an; kommt der Aufruf trotzdem, antwortet die Schnittstelle mit 409.
 *
 * <p>409 und nicht 422: Die Anfrage ist wohlgeformt, nur der Stand des Angebots laesst das Storno
 * nicht zu — dieselbe Abwaegung wie bei {@link StatusGrenzeErreicht}.
 */
@ResponseStatus(code = HttpStatus.CONFLICT, reason = StornoNichtMoeglich.MELDUNG)
public final class StornoNichtMoeglich extends RuntimeException {

  /** Die Meldung an der Schnittstelle (E16). */
  public static final String MELDUNG =
      "Aus diesem Zustand lässt sich das Angebot nicht stornieren.";
}
