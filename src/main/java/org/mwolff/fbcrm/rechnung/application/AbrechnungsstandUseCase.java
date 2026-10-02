package org.mwolff.fbcrm.rechnung.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.mwolff.fbcrm.angebot.application.AngebotNichtGefunden;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.arbeitszeit.application.Arbeitszeitauskunft;
import org.mwolff.fbcrm.arbeitszeit.application.Buchbarkeit;
import org.mwolff.fbcrm.rechnung.domain.Abrechnungsstand;
import org.mwolff.fbcrm.rechnung.domain.Positionsstand;
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
 * <p>Dazu kommt je Position, was die Zeiterfassung ueber sie sagt (Issue #193, Kriterien 7, 8, 11;
 * Plan #194, A12): ob sie Stunden traegt und wie viele insgesamt angefallen sind. Gefragt wird
 * ueber {@link Arbeitszeitauskunft} — der Port der Zeiten gehoert jenem Modul —, und was buchbar
 * ist, entscheidet {@link Buchbarkeit} und nicht diese Klasse. <b>Ohne Blick auf den Status des
 * Angebots</b>: Das Stellen der letzten Rechnung setzt es selbst auf „abgerechnet", und gerade dann
 * sollen die angefallenen Stunden und die Ueberschreitung stehen bleiben.
 *
 * <p>Drei Zuege in den Bestand und keiner je Position — das Angebot bringt seine Positionen mit,
 * die Rechnungen kommen in einem Aufruf, und die Auskunft nennt die Stunden aller Positionen auf
 * einmal. Die Einstellungen kommen dazu, weil ein Entwurf mit dem Satz von jetzt rechnet ({@link
 * GeltenderSteuersatz}).
 */
@Service
@Transactional(readOnly = true)
public class AbrechnungsstandUseCase {

  private final AngebotRepository angebote;
  private final RechnungRepository rechnungen;
  private final RechnungseinstellungenRepository einstellungen;
  private final Arbeitszeitauskunft arbeitszeit;

  AbrechnungsstandUseCase(
      final AngebotRepository angebote,
      final RechnungRepository rechnungen,
      final RechnungseinstellungenRepository einstellungen,
      final Arbeitszeitauskunft arbeitszeit) {
    this.angebote = angebote;
    this.rechnungen = rechnungen;
    this.einstellungen = einstellungen;
    this.arbeitszeit = arbeitszeit;
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
    final Map<Long, BigDecimal> angefallen = arbeitszeit.angefallen(angebotId);
    return new Angebotsabrechnung(
        Abrechnungsstand.fuer(angebot.positionen(), dazu).positionen().stream()
            .map(stand -> mitArbeitszeit(angebot, stand, angefallen))
            .toList(),
        dazu.stream()
            .sorted(Rechnungsreihenfolge.NEUESTE_ZUERST)
            .map(
                rechnung ->
                    new RechnungMitBetrag(
                        rechnung,
                        rechnung.brutto(GeltenderSteuersatz.fuer(rechnung, aktuellerSatz))))
            .toList());
  }

  /*
   * An einer nicht buchbaren Position stehen keine Stunden, auch wenn die Auskunft dort etwas
   * meldet: Sie antwortet zu jeder Position des Angebots, und ob eine Stunden tragen darf, sagt
   * Buchbarkeit. Was die Auskunft nicht nennt, ist 0 — dieselbe Lesart wie im Anlegen des Entwurfs.
   *
   * Das Angebot gehoert zur Frage, seit die interne Arbeit jede ihrer Positionen Stunden tragen
   * laesst (Issue #229, E11). Eine eigene Sperre fuer sie steht hier nicht: Aus der internen Arbeit
   * entsteht keine Rechnung, aber der Abrechnungsstand ist eine Lesesicht und sperrt nichts.
   */
  private static Positionsabrechnung mitArbeitszeit(
      final Angebot angebot, final Positionsstand stand, final Map<Long, BigDecimal> angefallen) {
    final boolean buchbar = Buchbarkeit.buchbar(angebot, stand.position());
    return new Positionsabrechnung(
        stand,
        buchbar,
        buchbar
            ? angefallen.getOrDefault(stand.position().requireId(), BigDecimal.ZERO)
            : BigDecimal.ZERO);
  }
}
