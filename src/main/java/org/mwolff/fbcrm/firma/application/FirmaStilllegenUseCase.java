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
 * Angeboten und Rechnungen, die bleiben — deshalb gibt es kein Loeschen, sondern nur diesen
 * Schalter (E3).
 *
 * <p>Die Ansprechpartner der Firma bleiben unberuehrt: Ihr Stilllegungsstand gehoert ihnen allein
 * (E2, Kriterium 16).
 *
 * <p><b>Zuerst der Stand</b> (#411, Kriterium 4; Plan #415, E18): Stilllegen und Wiederaktivieren
 * sind Zustandswechsel und werden wie jede Aenderung geprueft. Wer auf einem alten Stand
 * umschaltet, bekommt {@link FirmaGeaendert}; das handelnde Konto steht danach als Urheber an der
 * Firma.
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
   * @param version der vorgelegte Stand aus {@code If-Match}
   * @param konto Kennung des handelnden Kontos
   * @throws FirmaNichtGefunden wenn es die Firma nicht gibt
   * @throws FirmaGeaendert wenn die Firma inzwischen einen anderen Stand hat als den vorgelegten
   */
  public void stilllegen(final long id, final long version, final long konto) {
    firmen.save(vorhandene(id, version, konto).stillgelegt(clock.instant()).durch(konto));
  }

  /**
   * Nimmt die Firma wieder in Betrieb.
   *
   * @param id technische Id der Firma
   * @param version der vorgelegte Stand aus {@code If-Match}
   * @param konto Kennung des handelnden Kontos
   * @throws FirmaNichtGefunden wenn es die Firma nicht gibt
   * @throws FirmaGeaendert wenn die Firma inzwischen einen anderen Stand hat als den vorgelegten
   */
  public void aktivieren(final long id, final long version, final long konto) {
    firmen.save(vorhandene(id, version, konto).aktiviert(clock.instant()).durch(konto));
  }

  private Firma vorhandene(final long id, final long version, final long konto) {
    final Firma firma = firmen.findById(id).orElseThrow(FirmaNichtGefunden::new);
    FirmaGeaendert.pruefe(firma, version, konto);
    return firma;
  }
}
