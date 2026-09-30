package org.mwolff.fbcrm.rechnung.application;

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
 */
@Service
@Transactional
public class RechnungLoeschenUseCase {

  private final RechnungRepository rechnungen;

  RechnungLoeschenUseCase(final RechnungRepository rechnungen) {
    this.rechnungen = rechnungen;
  }

  /**
   * Loescht den Entwurf samt seinen Positionen.
   *
   * @param rechnungId Kennung der Rechnung
   * @throws RechnungNichtGefunden wenn es die Rechnung nicht gibt
   * @throws RechnungszustandPasstNicht wenn die Rechnung schon gestellt ist
   */
  public void loesche(final long rechnungId) {
    final Rechnung rechnung =
        rechnungen.findById(rechnungId).orElseThrow(RechnungNichtGefunden::new);
    if (rechnung.zustand() != Rechnungszustand.ENTWURF) {
      throw new RechnungszustandPasstNicht();
    }
    rechnungen.delete(rechnungId);
  }
}
