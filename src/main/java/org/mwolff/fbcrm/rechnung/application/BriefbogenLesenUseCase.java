package org.mwolff.fbcrm.rechnung.application;

import org.mwolff.fbcrm.rechnung.domain.BriefbogenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Der Stand des Briefbogens: Briefpapier, Schalter und Raender (fachliche Quelle #361, Kriterien 1
 * und 15; Plan #363, E1).
 *
 * <p>Ohne Sonderzweig fuer die frische Instanz: Die Migration hat die eine Zeile mit den Vorgaben
 * aus Kriterium 15 und beiden Schaltern an angelegt (E15).
 */
@Service
@Transactional(readOnly = true)
public class BriefbogenLesenUseCase {

  private final BriefbogenRepository bestand;

  public BriefbogenLesenUseCase(final BriefbogenRepository bestand) {
    this.bestand = bestand;
  }

  /** Der Stand, ohne den Schluessel des Briefpapiers im Objektspeicher. */
  public Briefbogenstand lese() {
    return Briefbogenstand.von(bestand.lies());
  }
}
