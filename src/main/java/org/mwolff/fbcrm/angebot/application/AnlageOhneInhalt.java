package org.mwolff.fbcrm.angebot.application;

import java.util.List;
import java.util.Map;
import org.mwolff.fbcrm.common.Feldfehler;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Die hochgeladene Datei hat keinen Inhalt (Issue #148, Kriterium 5; Plan #150, E8).
 *
 * <p>Eine Datei mit null Byte ist keine Anlage: Es gaebe nichts herunterzuladen und nichts
 * anzusehen, und der CHECK der Tabelle liesse die Zeile ohnehin nicht zu. Die Maske prueft es vor
 * dem Senden; diese Ausnahme haelt den Aufruf an, der an ihr vorbeikommt — und sie wirft, bevor ein
 * leeres Objekt im Speicher liegt.
 *
 * <p>Ein {@link Feldfehler} am Feld {@code datei}, aus demselben Grund wie {@link AnlageZuGross}.
 */
@ResponseStatus(code = HttpStatus.BAD_REQUEST, reason = AnlageOhneInhalt.MELDUNG)
public final class AnlageOhneInhalt extends Feldfehler {

  /** Was der Anwender am Feld liest — derselbe Satz, den die Antwort als {@code detail} traegt. */
  public static final String MELDUNG = "Die Datei ist leer.";

  /** Das Feld, unter dem Maske und Schnittstelle die Datei fuehren. */
  public static final String FELD = AnlageZuGross.FELD;

  @Override
  public Map<String, List<String>> felder() {
    return Map.of(FELD, List.of(MELDUNG));
  }
}
