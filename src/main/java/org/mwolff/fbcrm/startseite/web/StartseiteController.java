package org.mwolff.fbcrm.startseite.web;

import java.time.YearMonth;
import java.util.Optional;
import org.mwolff.fbcrm.startseite.application.StartseiteUseCase;
import org.mwolff.fbcrm.startseite.application.Zeitraum;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Der eine Weg der Startseite (Issue #214; Plan #208, E7).
 *
 * <p>Ein {@code GET} und nichts sonst: Die Startseite liest, sie schreibt nicht. Der Controller
 * entscheidet nichts (CLAUDE-java.md §6.3) — er gibt den gewuenschten Monat als {@link
 * Zeitraum.Monat} weiter, wie er angekommen ist. Ein Jahr nimmt dieser Weg noch nicht entgegen; der
 * Parameter {@code zeitraum} ist ein eigenes Paket (Plan #274).
 *
 * <p><b>Der Monat ist optional.</b> Fehlt er, fragt der Controller ohne Monat, und welcher der
 * laufende ist, entscheidet der Anwendungsfall an seiner Uhr in der Geschaeftszone (E8). Ein Monat,
 * der nicht zur Wahl steht, wirkt dort wie ein fehlender (E18) — ein unbekannter Monat ist kein
 * Fehler. Ein Wert, der ueberhaupt kein Monat ist, laesst sich dagegen nicht wandeln und kommt als
 * 400 zurueck ({@code GlobalExceptionHandler}, wie bei {@code ArbeitszeitController}); eine eigene
 * Pruefung dafuer waere eine zweite Abschrift derselben Regel.
 */
@RestController
@RequestMapping("/api/startseite")
public class StartseiteController {

  private final StartseiteUseCase useCase;

  public StartseiteController(final StartseiteUseCase useCase) {
    this.useCase = useCase;
  }

  /**
   * Der Stand der Startseite: die drei Kennzahlen, der geltende Monat und die waehlbaren.
   *
   * @param monat der gewuenschte Monat als {@code JJJJ-MM}, oder weggelassen fuer den laufenden
   */
  @GetMapping
  public StartseiteResponse stand(@RequestParam(required = false) final Optional<YearMonth> monat) {
    return StartseiteResponse.of(useCase.stand(monat.<Zeitraum>map(Zeitraum.Monat::new)));
  }
}
