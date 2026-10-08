package org.mwolff.fbcrm.firma.application;

import java.time.Clock;
import java.time.Instant;
import org.mwolff.fbcrm.firma.domain.Ansprechpartner;
import org.mwolff.fbcrm.firma.domain.AnsprechpartnerRepository;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Das Anlegen eines Ansprechpartners unter einer Firma (Kriterium 9).
 *
 * <p>Die Firma kommt als Kennung herein und wird gegen den Bestand geprueft, bevor irgendetwas
 * entsteht: Ohne diese Probe legte der Fremdschluessel der Datenbank zwar ebenfalls ein Veto ein,
 * aber als Serverfehler statt als 404.
 *
 * <p>Ob die Firma stillgelegt ist, spielt keine Rolle — auch eine ruhende Firma bleibt pflegbar
 * (Kriterium 14).
 */
@Service
@Transactional
public class AnsprechpartnerAnlegenUseCase {

  private final FirmaRepository firmen;
  private final AnsprechpartnerRepository ansprechpartner;
  private final Clock clock;

  public AnsprechpartnerAnlegenUseCase(
      final FirmaRepository firmen,
      final AnsprechpartnerRepository ansprechpartner,
      final Clock clock) {
    this.firmen = firmen;
    this.ansprechpartner = ansprechpartner;
    this.clock = clock;
  }

  /**
   * Legt den Ansprechpartner an und liefert ihn mit der Kennung aus dem Bestand zurueck.
   *
   * @param firmaId Kennung der Firma aus dem Pfad
   * @param daten die eingereichten Angaben; normalisiert werden sie hier (E9)
   * @throws FirmaNichtGefunden wenn es die Firma nicht gibt
   * @throws IllegalArgumentException wenn vom Nachnamen nur Leerraum uebrig bleibt
   */
  public Ansprechpartner anlegen(final long firmaId, final AnsprechpartnerDaten daten) {
    final AnsprechpartnerDaten sauber = daten.normalisiert();
    if (firmen.findById(firmaId).isEmpty()) {
      throw new FirmaNichtGefunden();
    }
    final Instant jetzt = clock.instant();
    return ansprechpartner.save(
        new Ansprechpartner(
            null,
            firmaId,
            sauber.vorname(),
            sauber.nachname(),
            sauber.rolle(),
            sauber.email(),
            sauber.telefonFestnetz(),
            sauber.telefonMobil(),
            true,
            jetzt,
            jetzt));
  }
}
