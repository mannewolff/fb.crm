package org.mwolff.fbcrm.rechnung.application;

import java.time.Clock;
import org.mwolff.fbcrm.rechnung.domain.NachgetrageneRechnung;
import org.mwolff.fbcrm.rechnung.domain.NachgetrageneRechnungRepository;
import org.mwolff.fbcrm.rechnung.domain.Rechnungszustand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Der Ausgang einer nachgetragenen Rechnung: bezahlt, abgeschrieben, oder zurueck auf gestellt
 * (#254, Kriterium 8; Plan #259, E24).
 *
 * <p>Ein eigener Weg neben {@link RechnungZustandSetzenUseCase}, weil die beiden Kennungsraeume
 * getrennt sind; geteilt wird die Regel, nicht der Weg. <b>Welcher Uebergang zulaessig ist,
 * entscheidet die Domaene</b> ({@link NachgetrageneRechnung#mitZustand}, die {@link
 * Rechnungszustand#ausgangswechselErlaubt} fragt — E4).
 */
@Service
@Transactional
public class NachtragZustandSetzenUseCase {

  private final NachgetrageneRechnungRepository nachgetragene;
  private final Clock clock;

  NachtragZustandSetzenUseCase(
      final NachgetrageneRechnungRepository nachgetragene, final Clock clock) {
    this.nachgetragene = nachgetragene;
    this.clock = clock;
  }

  /**
   * Stellt die Rechnung auf den neuen Zustand um und liefert sie zurueck.
   *
   * @param id Kennung der nachgetragenen Rechnung
   * @param ziel der neue Zustand
   * @throws NachtragNichtGefunden wenn es die Rechnung nicht gibt
   * @throws RechnungszustandPasstNicht wenn der Uebergang nicht zulaessig ist
   */
  public NachgetrageneRechnung setze(final long id, final Rechnungszustand ziel) {
    final NachgetrageneRechnung rechnung =
        nachgetragene.findById(id).orElseThrow(NachtragNichtGefunden::new);
    return nachgetragene.save(rechnung.mitZustand(ziel, clock.instant()));
  }
}
