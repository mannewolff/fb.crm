package org.mwolff.fbcrm.firma.application;

import java.time.Clock;
import org.mwolff.fbcrm.firma.domain.Ansprechpartner;
import org.mwolff.fbcrm.firma.domain.AnsprechpartnerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stilllegen und Wiederaktivieren eines Ansprechpartners (Kriterium 15).
 *
 * <p>Beide Richtungen in einer Klasse, weil es dieselbe Umschaltung ist — wie bei {@link
 * FirmaStilllegenUseCase}. Ein Ansprechpartner haengt an Angeboten, die bleiben; deshalb gibt es
 * kein Loeschen, sondern nur diesen Schalter (E3).
 *
 * <p>Sein Stand gehoert ihm allein: Das Stilllegen seiner Firma laesst ihn unberuehrt, und sein
 * eigenes Stilllegen sagt nichts ueber die Firma (E2, Kriterium 16). Beide Wege stehen auch bei
 * stillgelegter Firma offen (Kriterium 14) — die Firma wird dafuer gar nicht erst befragt.
 *
 * <p><b>Zuerst der Stand</b> (#411, Kriterium 4; Plan #415, E18): Wer auf einem alten Stand
 * umschaltet, bekommt {@link AnsprechpartnerGeaendert}; das handelnde Konto steht danach als
 * Urheber am Ansprechpartner.
 */
@Service
@Transactional
public class AnsprechpartnerStilllegenUseCase {

  private final AnsprechpartnerRepository ansprechpartner;
  private final Clock clock;

  public AnsprechpartnerStilllegenUseCase(
      final AnsprechpartnerRepository ansprechpartner, final Clock clock) {
    this.ansprechpartner = ansprechpartner;
    this.clock = clock;
  }

  /**
   * Legt den Ansprechpartner still.
   *
   * @param firmaId Kennung der Firma aus dem Pfad
   * @param id technische Id des Ansprechpartners
   * @param version der vorgelegte Stand aus {@code If-Match}
   * @param konto Kennung des handelnden Kontos
   * @return der Ansprechpartner mit dem neuen Stand
   * @throws AnsprechpartnerNichtGefunden wenn es ihn unter dieser Firma nicht gibt
   * @throws AnsprechpartnerGeaendert wenn er inzwischen einen anderen Stand hat als den vorgelegten
   */
  public Ansprechpartner stilllegen(
      final long firmaId, final long id, final long version, final long konto) {
    return ansprechpartner.save(
        vorhandener(firmaId, id, version, konto).stillgelegt(clock.instant()).durch(konto));
  }

  /**
   * Nimmt den Ansprechpartner wieder in Betrieb.
   *
   * @param firmaId Kennung der Firma aus dem Pfad
   * @param id technische Id des Ansprechpartners
   * @param version der vorgelegte Stand aus {@code If-Match}
   * @param konto Kennung des handelnden Kontos
   * @return der Ansprechpartner mit dem neuen Stand
   * @throws AnsprechpartnerNichtGefunden wenn es ihn unter dieser Firma nicht gibt
   * @throws AnsprechpartnerGeaendert wenn er inzwischen einen anderen Stand hat als den vorgelegten
   */
  public Ansprechpartner aktivieren(
      final long firmaId, final long id, final long version, final long konto) {
    return ansprechpartner.save(
        vorhandener(firmaId, id, version, konto).aktiviert(clock.instant()).durch(konto));
  }

  private Ansprechpartner vorhandener(
      final long firmaId, final long id, final long version, final long konto) {
    final Ansprechpartner partner =
        ansprechpartner
            .findById(id)
            .filter(einer -> einer.firmaId() == firmaId)
            .orElseThrow(AnsprechpartnerNichtGefunden::new);
    AnsprechpartnerGeaendert.pruefe(partner, version, konto);
    return partner;
  }
}
