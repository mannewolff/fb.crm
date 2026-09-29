package org.mwolff.fbcrm.angebot.application;

import java.time.Clock;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Das Aendern eines Angebots — in jedem Status (Issue #127, Kriterium 5).
 *
 * <p>Geschrieben wird das Angebot als Ganzes: Datum, Ansprechpartner, Beschreibung und die
 * vollstaendige Positionsliste in der gewuenschten Reihenfolge (E8). Die Plaetze der Positionen
 * vergibt der Bestand daraus lueckenlos neu (E24). Die Firma und der Status bleiben, wie sie sind.
 *
 * <p>Der Ansprechpartner geht durch {@link Ansprechpartnerwahl}: Ein neu gewaehlter muss zur Firma
 * gehoeren und aktiv sein, der bisherige bleibt wählbar.
 */
@Service
@Transactional
public class AngebotAendernUseCase {

  private final AngebotRepository angebote;
  private final Ansprechpartnerwahl wahl;
  private final Clock clock;

  AngebotAendernUseCase(
      final AngebotRepository angebote, final Ansprechpartnerwahl wahl, final Clock clock) {
    this.angebote = angebote;
    this.wahl = wahl;
    this.clock = clock;
  }

  /**
   * Aendert das Angebot und liefert es in seinem neuen Stand.
   *
   * @param angebotId Kennung des Angebots
   * @param daten die eingereichten Angaben samt vollstaendiger Positionsliste
   * @throws AngebotNichtGefunden wenn es das Angebot nicht gibt
   * @throws AnsprechpartnerNichtWaehlbar wenn der gewaehlte Ansprechpartner nicht zur Wahl steht
   */
  public Angebot aendere(final long angebotId, final AngebotDaten daten) {
    final Angebot angebot = angebote.findById(angebotId).orElseThrow(AngebotNichtGefunden::new);
    wahl.pruefe(angebot.firmaId(), daten.ansprechpartnerId(), angebot.ansprechpartnerId());
    return angebote.save(
        angebot.geaendert(
            daten.angebotDatum(),
            daten.ansprechpartnerId(),
            daten.beschreibung(),
            daten.positionen(),
            clock.instant()));
  }
}
