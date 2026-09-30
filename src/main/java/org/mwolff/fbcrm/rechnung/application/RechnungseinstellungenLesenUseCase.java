package org.mwolff.fbcrm.rechnung.application;

import org.mwolff.fbcrm.rechnung.domain.Rechnungseinstellungen;
import org.mwolff.fbcrm.rechnung.domain.RechnungseinstellungenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Die Rechnungseinstellungen, wie sie im Bestand stehen (fachliche Quelle #159, Kriterium 5).
 *
 * <p>Ohne Sonderzweig fuer die frische Instanz: Die Migration hat die eine Zeile mit ihren
 * Vorbelegungen angelegt, und der Port kennt kein „noch keine Einstellungen".
 */
@Service
@Transactional(readOnly = true)
public class RechnungseinstellungenLesenUseCase {

  private final RechnungseinstellungenRepository bestand;

  public RechnungseinstellungenLesenUseCase(final RechnungseinstellungenRepository bestand) {
    this.bestand = bestand;
  }

  /** Die Einstellungen; auf einer frischen Instanz die Vorbelegungen der Migration. */
  public Rechnungseinstellungen lese() {
    return bestand.lies();
  }
}
