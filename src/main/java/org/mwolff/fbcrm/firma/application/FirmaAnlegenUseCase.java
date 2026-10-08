package org.mwolff.fbcrm.firma.application;

import java.time.Clock;
import java.time.Instant;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Das Anlegen einer Firma (Kriterien 4, 7).
 *
 * <p>Eine neue Firma ist aktiv, und beide Zeitstempel kommen aus derselben Uhr — nicht aus zwei
 * Aufrufen von {@code Instant.now()}, die um Millisekunden auseinanderliegen koennten.
 */
@Service
@Transactional
public class FirmaAnlegenUseCase {

  private final FirmaRepository firmen;
  private final Clock clock;

  public FirmaAnlegenUseCase(final FirmaRepository firmen, final Clock clock) {
    this.firmen = firmen;
    this.clock = clock;
  }

  /**
   * Legt die Firma an und liefert sie mit der Kennung aus dem Bestand zurueck.
   *
   * @param daten die eingereichten Angaben; normalisiert werden sie hier (E9)
   * @throws IllegalArgumentException wenn vom Namen nur Leerraum uebrig bleibt
   */
  public Firma anlegen(final FirmaDaten daten) {
    final FirmaDaten sauber = daten.normalisiert();
    final Instant jetzt = clock.instant();
    return firmen.save(
        new Firma(
            null,
            sauber.name(),
            sauber.anschrift(),
            sauber.steuernummer(),
            sauber.umsatzsteuerId(),
            true,
            jetzt,
            jetzt));
  }
}
