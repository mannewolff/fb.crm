package org.mwolff.fbcrm.angebot.application;

import java.time.Clock;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Die Ansicht eines einzelnen Angebots samt seinen Positionen (Kriterien 5, 18).
 *
 * <p>Der Stand kommt nicht aus einer Spalte: Ein versendetes Angebot mit verstrichener Gueltigkeit
 * liest sich als „abgelaufen", ohne dass jemand etwas hat tun muessen (E4). Welcher Tag heute ist,
 * entscheidet die Geschaeftszone (E12) — deshalb die Uhr.
 */
@Service
@Transactional(readOnly = true)
public class AngebotLesenUseCase {

  private final AngebotRepository angebote;
  private final Clock clock;

  public AngebotLesenUseCase(final AngebotRepository angebote, final Clock clock) {
    this.angebote = angebote;
    this.clock = clock;
  }

  /**
   * Das Angebot zu einer Kennung.
   *
   * @param angebotId Kennung des Angebots
   * @throws AngebotNichtGefunden wenn es das Angebot nicht gibt
   */
  public AngebotAnsicht lese(final long angebotId) {
    return AngebotAnsicht.of(
        angebote.findById(angebotId).orElseThrow(AngebotNichtGefunden::new), clock);
  }
}
