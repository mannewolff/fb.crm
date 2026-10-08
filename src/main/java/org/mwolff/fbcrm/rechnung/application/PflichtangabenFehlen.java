package org.mwolff.fbcrm.rechnung.application;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.mwolff.fbcrm.common.Feldfehler;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Fuer das Stellen fehlen Pflichtangaben — alle fehlenden auf einmal (#160, Kriterium 13).
 *
 * <p>Eine Rechnung ohne eigene Anschrift, ohne Steuernummer oder ohne Bankverbindung ist kein
 * gueltiger Beleg, und eine ohne Anschrift des Empfaengers geht an niemanden. Geprueft wird das
 * beim Stellen und nicht beim Pflegen der Angaben: Wer seine Angaben nach und nach
 * vervollstaendigt, soll dabei nicht gestoert werden (siehe {@code EigeneAngaben}).
 *
 * <p><b>Alle fehlenden Felder zusammen</b>, nicht das erste: Wer fuenf Angaben nachtragen muss,
 * soll es in einem Gang tun koennen und nicht fuenfmal auf dieselbe Abweisung laufen. Die Felder
 * gehen als {@link Feldfehler} hinaus, damit die Maske jede Meldung an ihrem Feld zeigen kann —
 * dieselbe Form wie bei der Bean Validation ({@code GlobalExceptionHandler}).
 *
 * <p>422 und nicht 409: Die Anfrage ist wohlgeformt, aber fachlich nicht verarbeitbar — es fehlt
 * etwas, das der Anwender beisteuern muss.
 */
@ResponseStatus(code = HttpStatus.UNPROCESSABLE_ENTITY, reason = PflichtangabenFehlen.MELDUNG)
public final class PflichtangabenFehlen extends Feldfehler {

  /** Was der Anwender liest — derselbe Satz, den die Antwort als {@code detail} traegt. */
  public static final String MELDUNG = "Fuer das Stellen fehlen Pflichtangaben.";

  private final Map<String, List<String>> fehlende;

  /**
   * Die Ausnahme zu den fehlenden Feldern.
   *
   * <p>Als Kopie in der uebergebenen Reihenfolge: Die Meldungen sollen in der Ordnung gelesen
   * werden, in der die Pruefung sie gesammelt hat, und die Karte des Aufrufers darf sich danach
   * nicht mehr auf die Antwort auswirken.
   *
   * @param felder je fehlendem Feld seine Meldung, in der Reihenfolge der Pruefung
   */
  PflichtangabenFehlen(final Map<String, List<String>> felder) {
    super();
    this.fehlende = Collections.unmodifiableMap(new LinkedHashMap<>(felder));
  }

  @Override
  public Map<String, List<String>> felder() {
    return fehlende;
  }
}
