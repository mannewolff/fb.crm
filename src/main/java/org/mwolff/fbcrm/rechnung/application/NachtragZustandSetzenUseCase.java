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
 *
 * <p><b>Zuerst der Stand</b> (#411, Kriterium 4; Plan #415, E1, E14): Wer auf einem alten Stand
 * umstellt, bekommt {@link NachtragGeaendert} — vor der Kantenregel der Domaene, denn der andere
 * kann den Zustand eben erst gewechselt haben. Das handelnde Konto steht danach als Urheber an der
 * Rechnung.
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
   * @param version der vorgelegte Stand aus {@code If-Match}
   * @param konto Kennung des handelnden Kontos
   * @throws NachtragNichtGefunden wenn es die Rechnung nicht gibt
   * @throws NachtragGeloescht wenn die Rechnung inzwischen geloescht ist
   * @throws NachtragGeaendert wenn die Rechnung inzwischen einen anderen Stand hat als den
   *     vorgelegten
   * @throws RechnungszustandPasstNicht wenn der Uebergang nicht zulaessig ist
   */
  public NachgetrageneRechnung setze(
      final long id, final Rechnungszustand ziel, final long version, final long konto) {
    final NachgetrageneRechnung rechnung =
        nachgetragene.findById(id).orElseThrow(NachtragNichtGefunden::new);
    Nachtragsvergleich.pruefe(rechnung, version, konto);
    return nachgetragene.save(rechnung.mitZustand(ziel, clock.instant()).durch(konto));
  }
}
