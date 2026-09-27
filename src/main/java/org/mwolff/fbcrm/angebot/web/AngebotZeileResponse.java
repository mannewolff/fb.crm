package org.mwolff.fbcrm.angebot.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.angebot.application.AngebotAnsicht;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.Angebotsstand;

/**
 * Eine Zeile der Angebotsliste am Vorgang (Kriterium 20).
 *
 * <p>Nur, was die Liste zeigt: Nummer, Stand, die beiden Daten und die Summe. Die Positionen
 * bleiben draussen — die Liste zeigt sie nicht, und die Detailansicht holt sie ohnehin.
 *
 * @param id technische Id
 * @param nummer Angebotsnummer, oder {@code null} im Entwurf
 * @param stand der Stand, als der das Angebot heute gilt
 * @param angebotDatum Datum des Angebots
 * @param gueltigBis letzter Tag der Gueltigkeit
 * @param summe die Netto-Summe, gerechnet
 */
public record AngebotZeileResponse(
    long id,
    @Nullable String nummer,
    Angebotsstand stand,
    LocalDate angebotDatum,
    LocalDate gueltigBis,
    BigDecimal summe) {

  /** Die Sicht der Oberflaeche auf ein Angebot in der Liste. */
  static AngebotZeileResponse of(final AngebotAnsicht ansicht) {
    final Angebot angebot = ansicht.angebot();
    return new AngebotZeileResponse(
        angebot.requireId(),
        angebot.nummer(),
        ansicht.stand(),
        angebot.angebotDatum(),
        angebot.gueltigBis(),
        angebot.summe());
  }
}
