package org.mwolff.fbcrm.arbeitszeit.web;

import java.time.LocalDate;
import org.mwolff.fbcrm.arbeitszeit.application.Buchungsposition;

/**
 * Eine Angebotsposition, wie die Zeiterfassung sie der Oberflaeche nennt (Plan #194, A15, A20).
 *
 * <p>Ein Record fuer beide Lesewege: die Zeile der Monatsliste und die Auswahlliste des Dialogs.
 * Die Oberflaeche liest damit eine Form und nicht zwei — zum Gruppieren nach Firma und Angebot
 * braucht sie in beiden Faellen dieselben Angaben.
 *
 * @param id Kennung der Angebotsposition
 * @param bezeichnung die Leistung, wie das Angebot sie nennt
 * @param angebotId Kennung des Angebots, zu dem die Position gehoert
 * @param angebotDatum Datum dieses Angebots
 * @param intern ob das Angebot die eigene interne Arbeit festhaelt (Issue #229, Kriterium 2 von
 *     #207)
 * @param firmaName Name der Firma, an die das Angebot geht
 */
public record BuchungspositionResponse(
    long id,
    String bezeichnung,
    long angebotId,
    LocalDate angebotDatum,
    boolean intern,
    String firmaName) {

  /** Dieselbe Position in der Sprache der Schnittstelle. */
  static BuchungspositionResponse of(final Buchungsposition position) {
    return new BuchungspositionResponse(
        position.id(),
        position.bezeichnung(),
        position.angebotId(),
        position.angebotDatum(),
        position.intern(),
        position.firmaName());
  }
}
