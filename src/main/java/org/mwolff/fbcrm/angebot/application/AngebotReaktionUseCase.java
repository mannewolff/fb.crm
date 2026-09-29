package org.mwolff.fbcrm.angebot.application;

import java.time.Clock;
import java.time.Instant;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotszustand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Die Reaktion des Kunden auf ein Angebot (Kriterien 17, 18).
 *
 * <p><b>Aus welchen Zustaenden reagiert werden darf, entscheidet die Domaene.</b> {@link
 * Angebot#angenommen} und {@link Angebot#abgelehnt} lassen {@code VERSENDET} und {@code ABGELOEST}
 * zu — jeweils unabhaengig vom Datum, also auch am abgelaufenen Angebot (Kriterium 18, E4) — und
 * werfen sonst {@link AngebotNichtAenderbar}. Weil der Uebergang <b>vor</b> dem Schreiben steht,
 * hinterlaesst ein abgewiesener Versuch nichts.
 *
 * <p><b>Die Reaktion betrifft genau dieses eine Angebot.</b> Eine Firma hat mehrere unabhaengige
 * Angebote nebeneinander; die Zusage zu einem beendet keines der anderen (Issue #126).
 */
@Service
@Transactional
public class AngebotReaktionUseCase {

  private final AngebotRepository angebote;
  private final Clock clock;

  public AngebotReaktionUseCase(final AngebotRepository angebote, final Clock clock) {
    this.angebote = angebote;
    this.clock = clock;
  }

  /**
   * Markiert das Angebot als angenommen.
   *
   * @param angebotId Kennung des Angebots
   * @throws AngebotNichtGefunden wenn es das Angebot nicht gibt
   * @throws AngebotNichtAenderbar wenn das Angebot nicht versendet oder abgeloest ist
   */
  public AngebotAnsicht nimmAn(final long angebotId) {
    return reagiere(angebotId, Angebotszustand.ANGENOMMEN);
  }

  /**
   * Markiert das Angebot als abgelehnt.
   *
   * @param angebotId Kennung des Angebots
   * @throws AngebotNichtGefunden wenn es das Angebot nicht gibt
   * @throws AngebotNichtAenderbar wenn das Angebot nicht versendet oder abgeloest ist
   */
  public AngebotAnsicht lehneAb(final long angebotId) {
    return reagiere(angebotId, Angebotszustand.ABGELEHNT);
  }

  /*
   * Beide Wege unterscheiden sich allein im Zielzustand. Der Zielzustand kommt als Parameter und
   * nicht als Rumpf eines dritten Wegs: Der Uebergang steht im Pfad und ist damit aus dem
   * Zugriffsprotokoll lesbar.
   */
  private AngebotAnsicht reagiere(final long angebotId, final Angebotszustand ziel) {
    final Angebot angebot = angebote.findById(angebotId).orElseThrow(AngebotNichtGefunden::new);
    final Instant jetzt = clock.instant();
    final Angebot reagiert =
        angebote.save(
            ziel == Angebotszustand.ANGENOMMEN
                ? angebot.angenommen(jetzt)
                : angebot.abgelehnt(jetzt));
    return AngebotAnsicht.of(reagiert, clock);
  }
}
