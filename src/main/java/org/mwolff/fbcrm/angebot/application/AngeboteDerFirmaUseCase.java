package org.mwolff.fbcrm.angebot.application;

import java.util.List;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Die Angebotsliste einer Firma, neueste zuerst (Issue #127, Kriterium 7).
 *
 * <p>Die Reihenfolge steht in {@link Angebotsreihenfolge}. Eine unbekannte Firma ist hier kein
 * Fehler, sondern eine Firma ohne Angebot: Ob es sie gibt, beantwortet ihre eigene Detailansicht.
 */
@Service
@Transactional(readOnly = true)
public class AngeboteDerFirmaUseCase {

  private final AngebotRepository bestand;

  public AngeboteDerFirmaUseCase(final AngebotRepository bestand) {
    this.bestand = bestand;
  }

  /**
   * Die Angebote einer Firma, neueste zuerst.
   *
   * @param firmaId Kennung der Firma
   */
  public List<Angebot> angebote(final long firmaId) {
    return bestand.findByFirma(firmaId).stream()
        .sorted(Angebotsreihenfolge.NEUESTE_ZUERST)
        .toList();
  }
}
