package org.mwolff.fbcrm.arbeitszeit.web;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.arbeitszeit.application.Buchungsposition;

/**
 * Eine Angebotsposition, wie die Zeiterfassung sie der Oberflaeche nennt (Plan #194, A15, A20).
 *
 * <p>Ein Record fuer beide Lesewege: die Zeile der Monatsliste und die Auswahlliste des Dialogs.
 * Die Oberflaeche liest damit eine Form und nicht zwei. Den Titel des Angebots zeigt nur die
 * Auswahl des Dialogs, die Monatsliste laesst ihn liegen (Issue #328; Plan #323, E5).
 *
 * @param id Kennung der Angebotsposition
 * @param bezeichnung die Leistung, wie das Angebot sie nennt
 * @param angebotId Kennung des Angebots, zu dem die Position gehoert
 * @param angebotDatum Datum dieses Angebots
 * @param angebotTitel Titel dieses Angebots, {@code null}, wo es keinen traegt; {@code
 *     angebotTitel} und nicht {@code titel}, weil neben der {@code bezeichnung} der Position ein
 *     nacktes {@code titel} zweideutig waere (Plan #323, E1)
 * @param intern ob das Angebot die eigene interne Arbeit festhaelt (Issue #229, Kriterium 2 von
 *     #207)
 * @param firmaName Name der Firma, an die das Angebot geht
 */
public record BuchungspositionResponse(
    long id,
    String bezeichnung,
    long angebotId,
    LocalDate angebotDatum,
    @Nullable String angebotTitel,
    boolean intern,
    String firmaName) {

  /** Dieselbe Position in der Sprache der Schnittstelle. */
  static BuchungspositionResponse of(final Buchungsposition position) {
    return new BuchungspositionResponse(
        position.id(),
        position.bezeichnung(),
        position.angebotId(),
        position.angebotDatum(),
        position.angebotTitel(),
        position.intern(),
        position.firmaName());
  }
}
