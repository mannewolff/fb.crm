package org.mwolff.fbcrm.rechnung.application;

import java.time.Clock;
import org.mwolff.fbcrm.rechnung.domain.Rechnungseinstellungen;
import org.mwolff.fbcrm.rechnung.domain.RechnungseinstellungenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Das Fortschreiben der Rechnungseinstellungen (fachliche Quelle #159, Kriterium 7).
 *
 * <p>Geschrieben wird immer der ganze Satz: Die Maske zeigt alle vier Werte auf einmal, und ein
 * Wert, der unveraendert zurueckkommt, ist nicht ausgelassen.
 *
 * <p>Der Zeitpunkt der Aenderung kommt aus der injizierten {@link Clock} und nicht aus {@code
 * Instant.now()} — sonst waere er im Test nicht festzuhalten (CLAUDE-java.md §6.2).
 */
@Service
@Transactional
public class RechnungseinstellungenPflegenUseCase {

  private final RechnungseinstellungenRepository bestand;
  private final Clock clock;

  public RechnungseinstellungenPflegenUseCase(
      final RechnungseinstellungenRepository bestand, final Clock clock) {
    this.bestand = bestand;
    this.clock = clock;
  }

  /**
   * Schreibt die neuen Einstellungen fort.
   *
   * @param einstellungen die eingereichten Einstellungen, an der Schnittstelle bereits geprueft
   */
  public void pflege(final Rechnungseinstellungen einstellungen) {
    bestand.speichere(einstellungen, clock.instant());
  }
}
