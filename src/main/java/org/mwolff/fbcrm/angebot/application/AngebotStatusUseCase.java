package org.mwolff.fbcrm.angebot.application;

import java.time.Clock;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Das Weiter- und Zurueckschalten des Status (Issue #127, Kriterium 4).
 *
 * <p>Beide Richtungen gehen eine Stufe und ohne weitere Pruefung (Kriterium 9). Ob es in die
 * verlangte Richtung eine Stufe gibt, entscheidet die Domaene ({@link Angebot#statusWeiter}, {@link
 * Angebot#statusZurueck}); die Ausnahme fliegt, bevor gespeichert wird.
 */
@Service
@Transactional
public class AngebotStatusUseCase {

  private final AngebotRepository angebote;
  private final Clock clock;

  AngebotStatusUseCase(final AngebotRepository angebote, final Clock clock) {
    this.angebote = angebote;
    this.clock = clock;
  }

  /**
   * Schaltet das Angebot eine Stufe weiter.
   *
   * @param angebotId Kennung des Angebots
   * @throws AngebotNichtGefunden wenn es das Angebot nicht gibt
   * @throws StatusGrenzeErreicht wenn das Angebot schon abgerechnet ist
   */
  public Angebot weiter(final long angebotId) {
    return angebote.save(lies(angebotId).statusWeiter(clock.instant()));
  }

  /**
   * Schaltet das Angebot eine Stufe zurueck.
   *
   * @param angebotId Kennung des Angebots
   * @throws AngebotNichtGefunden wenn es das Angebot nicht gibt
   * @throws StatusGrenzeErreicht wenn das Angebot erst angelegt ist
   */
  public Angebot zurueck(final long angebotId) {
    return angebote.save(lies(angebotId).statusZurueck(clock.instant()));
  }

  private Angebot lies(final long angebotId) {
    return angebote.findById(angebotId).orElseThrow(AngebotNichtGefunden::new);
  }
}
