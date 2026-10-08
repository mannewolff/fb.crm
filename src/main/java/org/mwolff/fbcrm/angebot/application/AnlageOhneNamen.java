package org.mwolff.fbcrm.angebot.application;

import java.util.List;
import java.util.Map;
import org.mwolff.fbcrm.angebot.domain.Dateiname;
import org.mwolff.fbcrm.common.Feldfehler;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Von dem Dateinamen bleibt nach der Saeuberung nichts uebrig (Plan #150, E8).
 *
 * <p>{@link Dateiname} schneidet Pfadangaben, Steuerzeichen und Leerraum am Rand weg und meldet ein
 * leeres Ergebnis dem Aufrufer, statt selbst zu urteilen. Was daraus wird, entscheidet diese
 * Schicht: Ohne Namen gibt es nichts, was die Liste zeigen und die Auslieferung nennen koennte —
 * also eine Feldmeldung und keine Anlage mit ersatzweise erfundenem Namen.
 *
 * <p>Ein {@link Feldfehler} am Feld {@code datei}, aus demselben Grund wie {@link AnlageZuGross}.
 */
@ResponseStatus(code = HttpStatus.BAD_REQUEST, reason = AnlageOhneNamen.MELDUNG)
public final class AnlageOhneNamen extends Feldfehler {

  /** Was der Anwender am Feld liest — derselbe Satz, den die Antwort als {@code detail} traegt. */
  public static final String MELDUNG = "Die Datei braucht einen Namen.";

  /** Das Feld, unter dem Maske und Schnittstelle die Datei fuehren. */
  public static final String FELD = AnlageZuGross.FELD;

  @Override
  public Map<String, List<String>> felder() {
    return Map.of(FELD, List.of(MELDUNG));
  }
}
