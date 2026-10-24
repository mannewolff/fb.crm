package org.mwolff.fbcrm.rechnung.application;

import java.time.Clock;
import org.mwolff.fbcrm.rechnung.domain.Rechnung;
import org.mwolff.fbcrm.rechnung.domain.RechnungRepository;
import org.mwolff.fbcrm.rechnung.domain.Rechnungszustand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Das Loeschen eines Rechnungsentwurfs (#160, Kriterien 12 und 14).
 *
 * <p>Ein Entwurf, aus dem nichts wird, verschwindet — er hat nie eine Nummer getragen und reisst
 * keine Luecke im Nummernkreis. Eine gestellte Rechnung bleibt: Sie zu loeschen risse genau dieses
 * Loch, und ihr Kunde hat sie bereits gelesen.
 *
 * <p>Die Grenze steht hier und nicht im Bestand: Der Bestand fuehrt aus, der Anwendungsfall
 * entscheidet (siehe {@code RechnungRepository}).
 *
 * <p><b>Verschwinden heisst gekennzeichnet</b> (#411, Kriterium 6; Plan #415, E16, E17). Die Zeile
 * bleibt mit Zeitpunkt und Konto der Loeschung stehen, damit ein spaeterer Zugriff beides nennen
 * kann; ihre Positionen werden hart geloescht ({@link Rechnung#geloescht}). Davor steht der
 * Vergleich wie an jedem anderen Weg: Ein inzwischen geaenderter Entwurf wird nicht geloescht
 * ({@link RechnungGeaendert}), ein schon geloeschter nicht ein zweites Mal ({@link
 * RechnungGeloescht}).
 */
@Service
@Transactional
public class RechnungLoeschenUseCase {

  private final RechnungRepository rechnungen;
  private final Clock clock;

  RechnungLoeschenUseCase(final RechnungRepository rechnungen, final Clock clock) {
    this.rechnungen = rechnungen;
    this.clock = clock;
  }

  /**
   * Kennzeichnet den Entwurf als geloescht und loescht seine Positionen.
   *
   * @param rechnungId Kennung der Rechnung
   * @param version der vorgelegte Stand aus {@code If-Match}
   * @param konto Kennung des handelnden Kontos
   * @throws RechnungNichtGefunden wenn es die Rechnung nicht gibt
   * @throws RechnungGeloescht wenn die Rechnung inzwischen geloescht ist
   * @throws RechnungGeaendert wenn die Rechnung inzwischen einen anderen Stand hat als den
   *     vorgelegten
   * @throws RechnungszustandPasstNicht wenn die Rechnung schon gestellt ist
   */
  public void loesche(final long rechnungId, final long version, final long konto) {
    final Rechnung rechnung =
        rechnungen.findById(rechnungId).orElseThrow(RechnungNichtGefunden::new);
    Rechnungsvergleich.pruefe(rechnung, version, konto);
    if (rechnung.zustand() != Rechnungszustand.ENTWURF) {
      throw new RechnungszustandPasstNicht();
    }
    rechnungen.save(rechnung.geloescht(konto, clock.instant()));
  }
}
