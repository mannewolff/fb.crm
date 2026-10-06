package org.mwolff.fbcrm.rechnung.application;

import java.time.Clock;
import org.mwolff.fbcrm.rechnung.domain.Rechnung;
import org.mwolff.fbcrm.rechnung.domain.RechnungRepository;
import org.mwolff.fbcrm.rechnung.domain.Rechnungszustand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Der Ausgang einer gestellten Rechnung: bezahlt, abgeschrieben, oder zurueck auf gestellt (Issue
 * #253).
 *
 * <p>Drei Zeilen Ablauf und keine Entscheidung: Gelesen, umgestellt, geschrieben. <b>Welcher
 * Uebergang zulaessig ist, entscheidet die Domaene</b> ({@link
 * Rechnung#mitZustand(Rechnungszustand, java.time.Instant)}) — hier stuende die Regel ein zweites
 * Mal, und ein Weg an ihr vorbei waere der Weg ueber einen anderen Anwendungsfall.
 *
 * <p><b>Ohne Sperre auf der Zeile</b>, anders als beim Stellen: Hier wird keine Nummer gezogen, und
 * nichts geht verloren, wenn zwei Aufrufe dieselbe Rechnung umstellen. Gegen gleichzeitiges
 * Bearbeiten schuetzt sich fb.crm ohnehin nicht (CLAUDE.md, „Betriebsform").
 */
@Service
@Transactional
public class RechnungZustandSetzenUseCase {

  private final RechnungRepository rechnungen;
  private final Clock clock;

  RechnungZustandSetzenUseCase(final RechnungRepository rechnungen, final Clock clock) {
    this.rechnungen = rechnungen;
    this.clock = clock;
  }

  /**
   * Stellt die Rechnung auf den neuen Zustand um und liefert sie zurueck.
   *
   * @param rechnungId Kennung der Rechnung
   * @param ziel der neue Zustand
   * @throws RechnungNichtGefunden wenn es die Rechnung nicht gibt
   * @throws RechnungszustandPasstNicht wenn der Uebergang nicht zulaessig ist
   */
  public Rechnung setze(final long rechnungId, final Rechnungszustand ziel) {
    final Rechnung rechnung =
        rechnungen.findById(rechnungId).orElseThrow(RechnungNichtGefunden::new);
    return rechnungen.save(rechnung.mitZustand(ziel, clock.instant()));
  }
}
