package org.mwolff.fbcrm.eigeneangaben.application;

import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngaben;
import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngabenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Die eigenen Angaben, wie sie im Bestand stehen (Kriterium 1).
 *
 * <p>Ohne Sonderzweig fuer die frische Instanz: Die Migration hat die eine Zeile angelegt, und auf
 * ihr sind schlicht alle Felder leer.
 */
@Service
@Transactional(readOnly = true)
public class EigeneAngabenLesenUseCase {

  private final EigeneAngabenRepository bestand;

  public EigeneAngabenLesenUseCase(final EigeneAngabenRepository bestand) {
    this.bestand = bestand;
  }

  /** Die eigenen Angaben. */
  public EigeneAngaben lese() {
    return bestand.lies();
  }
}
