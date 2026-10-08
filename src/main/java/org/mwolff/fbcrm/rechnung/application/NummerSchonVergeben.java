package org.mwolff.fbcrm.rechnung.application;

import java.util.List;
import java.util.Map;
import org.mwolff.fbcrm.common.Feldfehler;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Die Nummer einer nachgetragenen Rechnung traegt schon eine andere Rechnung, geschrieben oder
 * nachgetragen (#254, Kriterium 4; Plan #259, E16).
 *
 * <p>Nicht zu verwechseln mit {@link RechnungsnummerSchonVergeben}: Dort hat der Nummernkreis die
 * Nummer gezogen, und das Stellen hat kein Feld dafuer. Hier hat der Anwender sie eingegeben, und
 * die Maske des Nachtrags zeigt die Meldung an genau diesem Feld.
 *
 * <p>422 wie die uebrigen Feldfehler dieses Projekts (E23): Der Rumpf ist wohlgeformt, sein Wert
 * aber fachlich nicht verarbeitbar.
 */
@ResponseStatus(code = HttpStatus.UNPROCESSABLE_ENTITY, reason = NummerSchonVergeben.MELDUNG)
public final class NummerSchonVergeben extends Feldfehler {

  /** Was der Anwender am Feld liest — derselbe Satz, den die Antwort als {@code detail} traegt. */
  public static final String MELDUNG = "Diese Rechnungsnummer trägt schon eine andere Rechnung.";

  /** Das Feld, unter dem die Maske die Rechnungsnummer fuehrt. */
  public static final String FELD = "nummer";

  @Override
  public Map<String, List<String>> felder() {
    return Map.of(FELD, List.of(MELDUNG));
  }
}
