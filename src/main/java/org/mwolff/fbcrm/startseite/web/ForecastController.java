package org.mwolff.fbcrm.startseite.web;

import org.mwolff.fbcrm.startseite.application.ForecastUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Der Weg des Forecasts der Startseite (Issue #308; Plan #303, E1).
 *
 * <p><b>Ein eigener Weg neben {@link StartseiteController}</b> und kein weiteres Feld in dessen
 * Antwort: Der Forecast haengt nicht an der Zeitraumwahl (#302, Kriterium 7) und nimmt darum keinen
 * Parameter. In derselben Antwort holte ihn jeder Wechsel des Zeitraums neu.
 *
 * <p>Ein {@code GET} und nichts sonst; der Controller entscheidet nichts (CLAUDE-java.md §6.3).
 */
@RestController
@RequestMapping("/api/startseite/forecast")
public class ForecastController {

  private final ForecastUseCase useCase;

  public ForecastController(final ForecastUseCase useCase) {
    this.useCase = useCase;
  }

  /** Der Forecast, Stand von heute ueber alle Jahre mit offenen Positionen. */
  @GetMapping
  public ForecastResponse forecast() {
    return ForecastResponse.of(useCase.forecast());
  }
}
