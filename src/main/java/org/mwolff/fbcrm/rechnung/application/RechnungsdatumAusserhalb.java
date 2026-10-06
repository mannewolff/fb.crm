package org.mwolff.fbcrm.rechnung.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.mwolff.fbcrm.common.Feldfehler;
import org.mwolff.fbcrm.common.Geschaeftszone;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Das Datum einer nachgetragenen Rechnung liegt nicht zwischen dem 1. Januar des laufenden Jahres
 * und heute (#254, Kriterium 2; Plan #259, E7).
 *
 * <p>Die Grenze rechnet gegen die injizierte Uhr in der {@link Geschaeftszone} — weder gegen die
 * Systemuhr noch gegen UTC. Darum steht sie hier und nicht als CHECK mit {@code now()}, der
 * dieselbe Zeile im naechsten Jahr ungueltig machte, und nicht als {@code @PastOrPresent}.
 *
 * <p>422 wie die uebrigen Feldfehler dieses Projekts (E23).
 */
@ResponseStatus(code = HttpStatus.UNPROCESSABLE_ENTITY, reason = RechnungsdatumAusserhalb.MELDUNG)
public final class RechnungsdatumAusserhalb extends Feldfehler {

  /** Was der Anwender am Feld liest — derselbe Satz, den die Antwort als {@code detail} traegt. */
  public static final String MELDUNG =
      "Das Rechnungsdatum muss im laufenden Jahr liegen und darf nicht in der Zukunft liegen.";

  /** Das Feld, unter dem die Maske das Rechnungsdatum fuehrt. */
  public static final String FELD = "rechnungDatum";

  /**
   * Weist ein Datum ausserhalb der Grenze ab; beide Raender gehoeren dazu.
   *
   * @param datum das eingereichte Rechnungsdatum
   * @param clock die Uhr der Anwendung
   * @throws RechnungsdatumAusserhalb wenn das Datum vor dem 1. Januar des laufenden Jahres oder
   *     nach heute liegt
   */
  static void pruefe(final LocalDate datum, final Clock clock) {
    final LocalDate heute = LocalDate.now(clock.withZone(Geschaeftszone.ZONE));
    if (datum.isBefore(heute.withDayOfYear(1)) || datum.isAfter(heute)) {
      throw new RechnungsdatumAusserhalb();
    }
  }

  @Override
  public Map<String, List<String>> felder() {
    return Map.of(FELD, List.of(MELDUNG));
  }
}
