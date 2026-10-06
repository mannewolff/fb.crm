package org.mwolff.fbcrm.startseite.web;

import java.util.Optional;
import org.mwolff.fbcrm.startseite.application.StartseiteUseCase;
import org.mwolff.fbcrm.startseite.application.Zeitraum;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Der eine Weg der Startseite (Issue #214; Plan #208, E7; Plan #274, E1 bis E3).
 *
 * <p>Ein {@code GET} und nichts sonst: Die Startseite liest, sie schreibt nicht. Der Controller
 * entscheidet nichts (CLAUDE-java.md §6.3) — er gibt den gewuenschten {@link Zeitraum} weiter, wie
 * er angekommen ist.
 *
 * <p><b>Ein Parameter {@code zeitraum} fuer Monat und Jahr</b> (E1): {@code JJJJ-MM} oder {@code
 * JJJJ}. Den frueheren Parameter {@code monat} liest der Weg nicht mehr, auch nicht als Zweitnamen
 * (E2) — eine alte Adresse zeigt den laufenden Monat.
 *
 * <p><b>Der Zeitraum ist optional.</b> Fehlt er, fragt der Controller ohne Zeitraum, und welcher
 * Monat der laufende ist, entscheidet der Anwendungsfall an seiner Uhr in der Geschaeftszone (E8).
 * Ein Zeitraum, der nicht zur Wahl steht, wirkt dort wie ein fehlender (E18) — ein unbekannter
 * Zeitraum ist kein Fehler. Ein Wert, der ueberhaupt kein Zeitraum ist, laesst sich dagegen nicht
 * wandeln: {@link ZeitraumConverter} wirft, und die Wandlung kommt als 400 zurueck ({@code
 * GlobalExceptionHandler}, E3); eine eigene Pruefung hier waere eine zweite Abschrift derselben
 * Regel.
 */
@RestController
@RequestMapping("/api/startseite")
public class StartseiteController {

  private final StartseiteUseCase useCase;

  public StartseiteController(final StartseiteUseCase useCase) {
    this.useCase = useCase;
  }

  /**
   * Der Stand der Startseite: die drei Kennzahlen, der geltende Zeitraum und die waehlbaren.
   *
   * @param zeitraum der gewuenschte Zeitraum als {@code JJJJ-MM} oder {@code JJJJ}, oder
   *     weggelassen fuer den laufenden Monat
   */
  @GetMapping
  public StartseiteResponse stand(
      @RequestParam(required = false) final Optional<Zeitraum> zeitraum) {
    return StartseiteResponse.of(useCase.stand(zeitraum));
  }
}
