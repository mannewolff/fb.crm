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
 * FirmaStilllegenUseCase}. Ein Ansprechpartner haengt an Vorgaengen, die bleiben; deshalb gibt es
 * kein Loeschen, sondern nur diesen Schalter (E3).
 *
 * <p>Sein Stand gehoert ihm allein: Das Stilllegen seiner Firma laesst ihn unberuehrt, und sein
 * eigenes Stilllegen sagt nichts ueber die Firma (E2, Kriterium 16). Beide Wege stehen auch bei
 * stillgelegter Firma offen (Kriterium 14) — die Firma wird dafuer gar nicht erst befragt.
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
   * @throws AnsprechpartnerNichtGefunden wenn es ihn unter dieser Firma nicht gibt
   */
  public void stilllegen(final long firmaId, final long id) {
    ansprechpartner.save(vorhandener(firmaId, id).stillgelegt(clock.instant()));
  }

  /**
   * Nimmt den Ansprechpartner wieder in Betrieb.
   *
   * @param firmaId Kennung der Firma aus dem Pfad
   * @param id technische Id des Ansprechpartners
   * @throws AnsprechpartnerNichtGefunden wenn es ihn unter dieser Firma nicht gibt
   */
  public void aktivieren(final long firmaId, final long id) {
    ansprechpartner.save(vorhandener(firmaId, id).aktiviert(clock.instant()));
  }

  private Ansprechpartner vorhandener(final long firmaId, final long id) {
    return ansprechpartner
        .findById(id)
        .filter(partner -> partner.firmaId() == firmaId)
        .orElseThrow(AnsprechpartnerNichtGefunden::new);
  }
}
