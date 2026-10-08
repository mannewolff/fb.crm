package org.mwolff.fbcrm.firma.application;

import org.mwolff.fbcrm.firma.domain.AnsprechpartnerRepository;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Die Detailansicht einer Firma samt ihrer Ansprechpartner (Kriterien 5, 10). */
@Service
@Transactional(readOnly = true)
public class FirmaLesenUseCase {

  private final FirmaRepository firmen;
  private final AnsprechpartnerRepository ansprechpartner;

  public FirmaLesenUseCase(
      final FirmaRepository firmen, final AnsprechpartnerRepository ansprechpartner) {
    this.firmen = firmen;
    this.ansprechpartner = ansprechpartner;
  }

  /**
   * Die Firma zu einer Kennung mit allen ihren Ansprechpartnern.
   *
   * @param id technische Id der Firma
   * @throws FirmaNichtGefunden wenn es die Firma nicht gibt
   */
  public FirmaMitAnsprechpartnern lese(final long id) {
    final Firma firma = firmen.findById(id).orElseThrow(FirmaNichtGefunden::new);
    return new FirmaMitAnsprechpartnern(firma, ansprechpartner.findByFirma(id));
  }
}
