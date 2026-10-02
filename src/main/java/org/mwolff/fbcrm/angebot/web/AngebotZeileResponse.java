package org.mwolff.fbcrm.angebot.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;

/**
 * Eine Zeile der Angebotsliste an der Firma (Issue #127, Kriterium 7).
 *
 * <p>Ohne Positionen: Die Liste zeigt die Summe, nicht die Posten. Die Summe wird gerechnet (E5).
 *
 * @param id technische Id
 * @param angebotDatum Datum des Angebots
 * @param intern ob das Angebot die eigene interne Arbeit festhaelt (Issue #226)
 * @param status wie weit das Angebot gediehen ist
 * @param summe die Netto-Summe, gerechnet
 */
public record AngebotZeileResponse(
    long id, LocalDate angebotDatum, boolean intern, Angebotsstatus status, BigDecimal summe) {

  /** Die Sicht der Liste auf ein Angebot. */
  static AngebotZeileResponse of(final Angebot angebot) {
    return new AngebotZeileResponse(
        angebot.requireId(),
        angebot.angebotDatum(),
        angebot.intern(),
        angebot.status(),
        angebot.summe());
  }
}
