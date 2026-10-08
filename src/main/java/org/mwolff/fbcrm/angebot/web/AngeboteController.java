package org.mwolff.fbcrm.angebot.web;

import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.angebot.application.AngeboteUebersichtUseCase;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Die Uebersicht aller Angebote, wahlweise nach Status gefiltert (Issue #127, Kriterium 8).
 *
 * <p>Eine eigene Klasse neben {@link AngebotController}: Dessen Pfad traegt die Kennung, dieser
 * nicht, und die beiden Wege verdecken einander nicht. Ein unbekannter Status scheitert schon an
 * der Umwandlung in {@link Angebotsstatus} und ist 400.
 */
@RestController
@RequestMapping("/api/angebote")
public class AngeboteController {

  private final AngeboteUebersichtUseCase uebersicht;

  public AngeboteController(final AngeboteUebersichtUseCase uebersicht) {
    this.uebersicht = uebersicht;
  }

  /**
   * Alle Angebote, neueste zuerst.
   *
   * @param status der gesuchte Status; fehlt er, kommen alle
   */
  @GetMapping
  public AngeboteUebersichtResponse angebote(
      @RequestParam(required = false) final @Nullable Angebotsstatus status) {
    return AngeboteUebersichtResponse.of(uebersicht.angebote(Optional.ofNullable(status)));
  }
}
