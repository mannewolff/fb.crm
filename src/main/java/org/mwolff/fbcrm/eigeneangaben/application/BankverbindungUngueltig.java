package org.mwolff.fbcrm.eigeneangaben.application;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.mwolff.fbcrm.common.Feldfehler;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * IBAN oder BIC der eigenen Angaben sind ungueltig, und nichts wird gespeichert (Plan #461, E15;
 * fachliche Quelle #460, Kriterium 7).
 *
 * <p>Beide Felder zusammen, nicht das erste: Wer beides falsch eingibt, soll beides auf einmal
 * sehen. Die Meldungen gehen als {@link Feldfehler} hinaus, damit die Maske sie am Feld zeigt.
 *
 * <p>400 wie die Laengengrenzen der Bean Validation: Die Eingabe hat nicht die Form, die das Feld
 * verlangt.
 */
@ResponseStatus(code = HttpStatus.BAD_REQUEST, reason = BankverbindungUngueltig.MELDUNG)
public final class BankverbindungUngueltig extends Feldfehler {

  /** Was die Antwort als {@code detail} traegt. */
  public static final String MELDUNG = "Die Bankverbindung ist ungültig.";

  /** Die Meldung am Feld {@code iban}. */
  public static final String IBAN_UNGUELTIG = "Diese IBAN ist ungültig.";

  /** Die Meldung am Feld {@code bic}. */
  public static final String BIC_UNGUELTIG = "Diese BIC ist ungültig.";

  private final Map<String, List<String>> ungueltige;

  /**
   * Die Ausnahme zu den ungueltigen Feldern.
   *
   * @param felder je ungueltigem Feld seine Meldung, in der Reihenfolge der Maske
   */
  BankverbindungUngueltig(final Map<String, List<String>> felder) {
    super();
    this.ungueltige = Collections.unmodifiableMap(new LinkedHashMap<>(felder));
  }

  @Override
  public Map<String, List<String>> felder() {
    return ungueltige;
  }
}
