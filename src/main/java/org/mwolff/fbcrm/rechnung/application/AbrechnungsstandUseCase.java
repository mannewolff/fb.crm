package org.mwolff.fbcrm.rechnung.application;

import java.math.BigDecimal;
import java.util.List;
import org.mwolff.fbcrm.angebot.application.AngebotNichtGefunden;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.rechnung.domain.Abrechnungsstand;
import org.mwolff.fbcrm.rechnung.domain.Rechnung;
import org.mwolff.fbcrm.rechnung.domain.RechnungRepository;
import org.mwolff.fbcrm.rechnung.domain.RechnungseinstellungenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Der Abrechnungsstand eines Angebots (#160, Kriterium 26; Plan #169, E6).
 *
 * <p>Die Ansicht des Angebots zeigt damit, was von ihm abgerechnet ist und welche Rechnungen daraus
 * entstanden sind. Gerechnet wird ueber <b>alle</b> Rechnungen des Angebots, Entwuerfe wie
 * gestellte: Ein Entwurf, der den Rest schon traegt, ist kein offener Rest mehr (Kriterium 6).
 *
 * <p>Zwei Zuege in den Bestand und keiner je Position — das Angebot bringt seine Positionen mit,
 * und die Rechnungen kommen in einem Aufruf. Die Einstellungen kommen dazu, weil ein Entwurf mit
 * dem Satz von jetzt rechnet ({@link GeltenderSteuersatz}).
 */
@Service
@Transactional(readOnly = true)
public class AbrechnungsstandUseCase {

  private final AngebotRepository angebote;
  private final RechnungRepository rechnungen;
  private final RechnungseinstellungenRepository einstellungen;

  AbrechnungsstandUseCase(
      final AngebotRepository angebote,
      final RechnungRepository rechnungen,
      final RechnungseinstellungenRepository einstellungen) {
    this.angebote = angebote;
    this.rechnungen = rechnungen;
    this.einstellungen = einstellungen;
  }

  /**
   * Der Stand je Position des Angebots und die Rechnungen dazu, neueste zuerst.
   *
   * @param angebotId Kennung des Angebots
   * @throws AngebotNichtGefunden wenn es das Angebot nicht gibt
   */
  public Angebotsabrechnung zu(final long angebotId) {
    final Angebot angebot = angebote.findById(angebotId).orElseThrow(AngebotNichtGefunden::new);
    final List<Rechnung> dazu = rechnungen.findByAngebot(angebotId);
    final BigDecimal aktuellerSatz = einstellungen.lies().steuersatz();
    return new Angebotsabrechnung(
        Abrechnungsstand.fuer(angebot.positionen(), dazu),
        dazu.stream()
            .sorted(Rechnungsreihenfolge.NEUESTE_ZUERST)
            .map(
                rechnung ->
                    new RechnungMitBetrag(
                        rechnung,
                        rechnung.brutto(GeltenderSteuersatz.fuer(rechnung, aktuellerSatz))))
            .toList());
  }
}
