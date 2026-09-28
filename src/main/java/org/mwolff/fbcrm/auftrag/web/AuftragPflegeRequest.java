package org.mwolff.fbcrm.auftrag.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.auftrag.application.AuftragPflegedaten;
import org.mwolff.fbcrm.auftrag.domain.Auftragsstatus;

/**
 * Die Eingaben der Maske beim Pflegen eines Auftrags (Kriterium 7).
 *
 * <p><b>Kein Positionsfeld und keines fuer „Stunden je Personentag"</b> (F3, R4): Die Positionen
 * stehen ab der Anlage fest, und was sich nicht aendern darf, kommt gar nicht erst im Rumpf vor.
 * Das ist die staerkere Zusage als eine Pruefung, die man umgehen koennte — und sie steht hier als
 * Abwesenheit, nicht als Regel.
 *
 * <p><b>{@code auftragDatum} ist Pflicht</b>, anders als beim Anlegen: Dort darf es fehlen, weil
 * der Anwendungsfall dann den heutigen Tag setzt; hier gibt es nichts vorzubelegen — der Auftrag
 * traegt bereits einen Tag, und ein leeres Feld hiesse „loeschen", nicht „unveraendert lassen".
 *
 * <p>Der Status ist ebenfalls Pflicht und in jede Richtung frei (F6): Kriterium 7 nennt ihn in
 * einem Atemzug mit den drei anderen Angaben, und darum traegt ihn derselbe Weg (Plan E8).
 *
 * @param auftragDatum Datum des Auftrags
 * @param kundenbestellnummer Bestellnummer des Kunden, oder {@code null}
 * @param leistungAb erster Tag des Leistungszeitraums, oder {@code null}
 * @param leistungBis letzter Tag des Leistungszeitraums, oder {@code null}
 * @param status der Status, in den der Auftrag gesetzt wird
 */
@LeistungszeitraumConstraint
public record AuftragPflegeRequest(
    @NotNull LocalDate auftragDatum,
    @Nullable @Size(max = 100) String kundenbestellnummer,
    @Nullable LocalDate leistungAb,
    @Nullable LocalDate leistungBis,
    @NotNull Auftragsstatus status)
    implements Leistungszeitraum {

  /** Dieselben Angaben in der Sprache der Fachschicht. */
  AuftragPflegedaten daten() {
    return new AuftragPflegedaten(
        auftragDatum, kundenbestellnummer, leistungAb, leistungBis, status);
  }
}
