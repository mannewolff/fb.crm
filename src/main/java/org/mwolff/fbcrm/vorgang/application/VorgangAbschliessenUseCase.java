package org.mwolff.fbcrm.vorgang.application;

import java.time.Clock;
import org.mwolff.fbcrm.vorgang.domain.Vorgang;
import org.mwolff.fbcrm.vorgang.domain.VorgangRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Abschliessen und Wiedereroeffnen eines Vorgangs (Kriterien 20, 21).
 *
 * <p>Beide Richtungen in einer Klasse, weil es dieselbe Umschaltung ist — dasselbe Muster wie
 * {@code FirmaStilllegenUseCase}. Einen Grund verlangt die Anwendung nicht, und geloescht wird
 * nichts: Der Abschluss ist ein Schalter (E5), damit ein Vorgang ohne Auftrag mit seiner Historie
 * stehen bleibt.
 *
 * <p>Titel, Zuordnung, Nummer und Phase bleiben unberuehrt (Kriterium 21). Der Schalter wirkt
 * allein auf die Uebersicht und auf die Kennzeichnung in der Detailansicht.
 */
@Service
@Transactional
public class VorgangAbschliessenUseCase {

  private final VorgangRepository vorgaenge;
  private final Clock clock;

  public VorgangAbschliessenUseCase(final VorgangRepository vorgaenge, final Clock clock) {
    this.vorgaenge = vorgaenge;
    this.clock = clock;
  }

  /**
   * Schliesst den Vorgang ab.
   *
   * @param id technische Id des Vorgangs
   * @throws VorgangNichtGefunden wenn es den Vorgang nicht gibt
   */
  public void abschliessen(final long id) {
    vorgaenge.save(vorhandener(id).abgeschlossen(clock.instant()));
  }

  /**
   * Oeffnet den Vorgang wieder.
   *
   * @param id technische Id des Vorgangs
   * @throws VorgangNichtGefunden wenn es den Vorgang nicht gibt
   */
  public void wiederEroeffnen(final long id) {
    vorgaenge.save(vorhandener(id).wiederEroeffnet(clock.instant()));
  }

  private Vorgang vorhandener(final long id) {
    return vorgaenge.findById(id).orElseThrow(VorgangNichtGefunden::new);
  }
}
