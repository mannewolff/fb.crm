package org.mwolff.fbcrm.angebot.web;

import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.angebot.application.AngeboteUebersichtUseCase;
import org.mwolff.fbcrm.angebot.application.Angebotsauswahl;
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
 *
 * <p><b>Zwei freiwillige Parameter</b> (Issue #335, Plan #332 E6): Ein gesetzter {@code status}
 * heisst {@link Angebotsauswahl#imStatus} — die nicht stornierten Angebote darin, ein mitgegebenes
 * {@code storniert} aendert daran nichts (E7). Ohne Status heisst {@code storniert=true} {@link
 * Angebotsauswahl#stornierte()}, alles andere {@link Angebotsauswahl#alle()}.
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
   * @param storniert {@code true} ohne Status fuer die stornierten Angebote
   */
  @GetMapping
  public AngeboteUebersichtResponse angebote(
      @RequestParam(required = false) final @Nullable Angebotsstatus status,
      @RequestParam(required = false) final @Nullable Boolean storniert) {
    return AngeboteUebersichtResponse.of(uebersicht.angebote(auswahl(status, storniert)));
  }

  private static Angebotsauswahl auswahl(
      final @Nullable Angebotsstatus status, final @Nullable Boolean storniert) {
    if (status != null) {
      return Angebotsauswahl.imStatus(status);
    }
    if (Boolean.TRUE.equals(storniert)) {
      return Angebotsauswahl.stornierte();
    }
    return Angebotsauswahl.alle();
  }
}
