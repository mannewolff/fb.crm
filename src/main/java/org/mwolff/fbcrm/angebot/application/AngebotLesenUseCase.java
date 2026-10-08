package org.mwolff.fbcrm.angebot.application;

import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Das einzelne Angebot samt seinen Positionen (Issue #127). */
@Service
@Transactional(readOnly = true)
public class AngebotLesenUseCase {

  private final AngebotRepository angebote;

  public AngebotLesenUseCase(final AngebotRepository angebote) {
    this.angebote = angebote;
  }

  /**
   * Das Angebot zu einer Kennung.
   *
   * @param angebotId Kennung des Angebots
   * @throws AngebotNichtGefunden wenn es das Angebot nicht gibt
   */
  public Angebot lese(final long angebotId) {
    return angebote.findById(angebotId).orElseThrow(AngebotNichtGefunden::new);
  }
}
