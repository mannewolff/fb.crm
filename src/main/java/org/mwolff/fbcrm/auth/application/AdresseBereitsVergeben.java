package org.mwolff.fbcrm.auth.application;

import java.util.List;
import java.util.Map;
import org.mwolff.fbcrm.common.Feldfehler;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Die Adresse traegt schon ein anderes Konto — gleich in welcher Schreibweise, auch ein
 * stillgelegtes (Plan #414, E6; fachliche Quelle #409, Kriterium 4).
 *
 * <p>Ein {@link Feldfehler}: Die Maske zeigt den Satz am Feld {@code email}, an derselben Stelle
 * wie eine Verletzung der Bean Validation. 409, weil die Eingabe der Form nach stimmt und nur dem
 * Bestand widerspricht.
 *
 * <p>Die Ursache bleibt <b>im Haus</b>, aus demselben Grund wie bei {@link SetupRefused}: Die
 * Meldung bleibt leer, denn {@code GlobalExceptionHandler} gibt {@code getMessage()} nach aussen,
 * und der Text der Datenbank nennte Tabelle, Index und die Adresse.
 */
@ResponseStatus(code = HttpStatus.CONFLICT, reason = AdresseBereitsVergeben.MELDUNG)
public final class AdresseBereitsVergeben extends Feldfehler {

  /** Der Satz an der Adresse. */
  public static final String MELDUNG = "Diese Adresse traegt bereits ein anderes Konto.";

  /** Abgewiesen, weil die Pruefung das andere Konto gefunden hat. */
  public AdresseBereitsVergeben() {
    super();
  }

  /**
   * Abgewiesen, weil erst der Index {@code account_email_key} das zweite Konto verhindert hat —
   * etwa bei zwei gleichzeitigen Aufrufen mit derselben Adresse.
   *
   * @param cause die Verletzung des Index
   */
  public AdresseBereitsVergeben(final Throwable cause) {
    super();
    initCause(cause);
  }

  @Override
  public Map<String, List<String>> felder() {
    return Map.of("email", List.of(MELDUNG));
  }
}
