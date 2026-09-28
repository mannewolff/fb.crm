package org.mwolff.fbcrm.auftrag.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.auftrag.application.AuftragDaten;

/**
 * Die Eingaben der Maske beim Anlegen eines Auftrags (Kriterien 3, 4; F2).
 *
 * <p>Die Liste ist die vollstaendige Wahl in der gewuenschten Reihenfolge, und sie darf nicht leer
 * sein: Ein Auftrag ohne Position ist kein Auftrag. Der Anwender darf Positionen weglassen und
 * Mengen verringern — hinzufuegen, Mengen erhoehen oder Preise aendern kann er nicht, weil die
 * Anfrage dafuer keine Felder hat (Plan E7).
 *
 * <p><b>Kein Feld fuer den Status und keines fuer die Nummer.</b> Ein frischer Auftrag ist {@code
 * OFFEN} (Kriterium 6), und die Nummer kommt aus dem Nummernkreis (Kriterium 3); je ein Feld dafuer
 * waere ein zweiter, widersprechbarer Ort.
 *
 * <p>{@code auftragDatum} darf fehlen: Dann setzt der Anwendungsfall den heutigen Tag in {@code
 * common.Geschaeftszone} — nicht den des Browsers, dessen Uhr niemand kennt.
 *
 * @param auftragDatum Datum des Auftrags, oder {@code null}
 * @param kundenbestellnummer Bestellnummer des Kunden, oder {@code null}
 * @param leistungAb erster Tag des Leistungszeitraums, oder {@code null}
 * @param leistungBis letzter Tag des Leistungszeitraums, oder {@code null}
 * @param positionen die uebernommenen Positionen; mindestens eine
 */
@LeistungszeitraumConstraint
public record AuftragAnlegenRequest(
    @Nullable LocalDate auftragDatum,
    @Nullable @Size(max = 100) String kundenbestellnummer,
    @Nullable LocalDate leistungAb,
    @Nullable LocalDate leistungBis,
    @NotNull @Size(min = 1) @Valid List<AuftragPositionRequest> positionen) {

  /** Dieselben Angaben in der Sprache der Fachschicht. */
  AuftragDaten daten() {
    return new AuftragDaten(
        auftragDatum,
        kundenbestellnummer,
        leistungAb,
        leistungBis,
        positionen.stream().map(AuftragPositionRequest::wahl).toList());
  }
}
