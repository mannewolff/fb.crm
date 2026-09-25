package org.mwolff.fbcrm.firma.application;

import java.time.Clock;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stilllegen und Wiederaktivieren einer Firma (Kriterium 13).
 *
 * <p>Beide Richtungen in einer Klasse, weil es dieselbe Umschaltung ist. Eine Firma haengt an
 * Vorgaengen, Angeboten und Rechnungen, die bleiben — deshalb gibt es kein Loeschen, sondern nur
 * diesen Schalter (E3).
 *
 * <p>Die Ansprechpartner der Firma bleiben unberuehrt: Ihr Stilllegungsstand gehoert ihnen allein
 * (E2, Kriterium 16).
 */
@Service
@Transactional
public class FirmaStilllegenUseCase {

  private final FirmaRepository firmen;
  private final Clock clock;

  public FirmaStilllegenUseCase(final FirmaRepository firmen, final Clock clock) {
    this.firmen = firmen;
    this.clock = clock;
  }

  /**
   * Legt die Firma still.
   *
   * @param id technische Id der Firma
   * @throws FirmaNichtGefunden wenn es die Firma nicht gibt
   */
  public void stilllegen(final long id) {
    firmen.save(vorhandene(id).stillgelegt(clock.instant()));
  }

  /**
   * Nimmt die Firma wieder in Betrieb.
   *
   * @param id technische Id der Firma
   * @throws FirmaNichtGefunden wenn es die Firma nicht gibt
   */
  public void aktivieren(final long id) {
    firmen.save(vorhandene(id).aktiviert(clock.instant()));
  }

  private Firma vorhandene(final long id) {
    return firmen.findById(id).orElseThrow(FirmaNichtGefunden::new);
  }
}
