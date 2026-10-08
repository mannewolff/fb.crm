package org.mwolff.fbcrm.rechnung.application;

import org.mwolff.fbcrm.firma.application.FirmaNichtGefunden;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
import org.mwolff.fbcrm.rechnung.domain.NachgetrageneRechnung;
import org.mwolff.fbcrm.rechnung.domain.NachgetrageneRechnungRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Die einzelne nachgetragene Rechnung samt dem heutigen Namen ihrer Firma (#254, Kriterium 11).
 *
 * <p>Anders als eine gestellte Rechnung traegt sie keine Kopie des Empfaengers: fb.crm hat sie nie
 * geschrieben, es gibt keinen Beleg, dessen Wortlaut festzuhalten waere. Sie verweist auf die
 * Firma, und eine spaetere Umbenennung zeigt sich auch bei ihr.
 */
@Service
@Transactional(readOnly = true)
public class NachtragLesenUseCase {

  private final NachgetrageneRechnungRepository nachgetragene;
  private final FirmaRepository firmen;

  NachtragLesenUseCase(
      final NachgetrageneRechnungRepository nachgetragene, final FirmaRepository firmen) {
    this.nachgetragene = nachgetragene;
    this.firmen = firmen;
  }

  /**
   * Die nachgetragene Rechnung zu einer Kennung.
   *
   * @param id Kennung der nachgetragenen Rechnung
   * @throws NachtragNichtGefunden wenn es die Rechnung nicht gibt
   * @throws FirmaNichtGefunden wenn es ihre Firma nicht gibt — Firmen werden nie geloescht, das
   *     waere ein Widerspruch im Bestand
   */
  public NachgetrageneRechnungMitFirma lese(final long id) {
    final NachgetrageneRechnung rechnung =
        nachgetragene.findById(id).orElseThrow(NachtragNichtGefunden::new);
    final Firma firma = firmen.findById(rechnung.firmaId()).orElseThrow(FirmaNichtGefunden::new);
    return new NachgetrageneRechnungMitFirma(rechnung, rechnung.firmaId(), firma.name());
  }
}
