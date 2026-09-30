package org.mwolff.fbcrm.rechnung.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.mwolff.fbcrm.angebot.application.AngebotNichtGefunden;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.rechnung.domain.Abrechnungsstand;
import org.mwolff.fbcrm.rechnung.domain.Rechnung;
import org.mwolff.fbcrm.rechnung.domain.RechnungRepository;
import org.mwolff.fbcrm.rechnung.domain.Rechnungsposition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Die einzelne Rechnung samt den Zeilen ihrer Maske (Plan #169, E5, E6).
 *
 * <p>Gelesen wird gegen das Angebot und nicht gegen die Rechnung: Die Maske zeigt jede Position des
 * Angebots, damit der Freiberufler beim Wiederoeffnen auch die sieht, die er beim ersten Mal
 * weggelassen hat. Der Abrechnungsstand nimmt diese Rechnung aus — ihre eigene Menge steht in der
 * Spalte „jetzt abrechnen" und nicht in „bereits abgerechnet" (#160, Kriterien 4 und 6).
 */
@Service
@Transactional(readOnly = true)
public class RechnungLesenUseCase {

  private final RechnungRepository rechnungen;
  private final AngebotRepository angebote;

  RechnungLesenUseCase(final RechnungRepository rechnungen, final AngebotRepository angebote) {
    this.rechnungen = rechnungen;
    this.angebote = angebote;
  }

  /**
   * Die Rechnung zu einer Kennung mit dem Stand jeder Position ihres Angebots.
   *
   * @param rechnungId Kennung der Rechnung
   * @throws RechnungNichtGefunden wenn es die Rechnung nicht gibt
   * @throws AngebotNichtGefunden wenn es das Angebot der Rechnung nicht gibt — Angebote werden nie
   *     geloescht, das waere ein Widerspruch im Bestand
   */
  public Rechnungsansicht lese(final long rechnungId) {
    final Rechnung rechnung =
        rechnungen.findById(rechnungId).orElseThrow(RechnungNichtGefunden::new);
    final Angebot angebot =
        angebote.findById(rechnung.angebotId()).orElseThrow(AngebotNichtGefunden::new);
    final Abrechnungsstand stand =
        Abrechnungsstand.ohne(
            angebot.positionen(), rechnungen.findByAngebot(rechnung.angebotId()), rechnungId);
    final Map<Long, BigDecimal> jetzt =
        rechnung.positionen().stream()
            .collect(
                Collectors.toMap(Rechnungsposition::angebotPositionId, Rechnungsposition::menge));
    final List<Abrechnungszeile> zeilen =
        stand.positionen().stream()
            .map(
                positionsstand -> {
                  final BigDecimal menge =
                      jetzt.getOrDefault(positionsstand.position().requireId(), BigDecimal.ZERO);
                  return new Abrechnungszeile(
                      positionsstand.position(),
                      positionsstand.abgerechnet(),
                      positionsstand.offen(),
                      positionsstand.ueberschreitungMit(menge),
                      menge);
                })
            .toList();
    return new Rechnungsansicht(rechnung, zeilen);
  }
}
