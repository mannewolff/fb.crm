package org.mwolff.fbcrm.angebot.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.angebot.application.AngebotDaten;

/**
 * Die Eingaben der Maske — das Angebot als Ganzes (Issue #127, E8).
 *
 * <p>Ein {@code PUT} mit Datum, Titel, Ansprechpartner, Beschreibung und der vollstaendigen
 * Positionsliste in der gewuenschten Reihenfolge; Hinzufuegen, Aendern, Loeschen und Verschieben
 * fallen daraus heraus. Eine leere Liste und eine leere Beschreibung sind zulaessig (Kriterium 9).
 *
 * <p><b>Kein Feld fuer die Firma und den Status.</b> Die Firma bleibt die, bei der das Angebot
 * angelegt wurde (Kriterium 5), und der Status hat seine eigenen Wege (Kriterium 4) — beim Wechsel
 * der Art folgt er ihr von selbst (Issue #227).
 *
 * <p>{@code intern} darf fehlen und ist dann {@code false}: Das Angebot an einen Kunden ist der
 * gewoehnliche Fall, und ein {@code null} soll denselben Weg gehen wie ein fehlendes Feld —
 * dieselbe Zusage wie in {@link AngebotAnlegenRequest} (Issue #226).
 *
 * <p>Die Beschreibung traegt keine Laengengrenze — sie steht in einer {@code text}-Spalte. Der
 * Titel dagegen hat eine: hoechstens 200 Zeichen, und er ist Pflicht (Issue #317; #314, Kriterien 1
 * und 3). Gemessen wird der eingereichte Text, nicht der getrimmte — die Maske sendet ihn getrimmt,
 * und so messen Maske, Anfrage und Spalte denselben Text (Plan #315, E7).
 *
 * @param angebotDatum Datum des Angebots
 * @param titel die einzeilige Benennung des Angebots, hoechstens 200 Zeichen
 * @param ansprechpartnerId Kennung des Ansprechpartners bei der Firma, oder {@code null}
 * @param beschreibung der Text des Angebots, oder {@code null}
 * @param intern ob das Angebot die eigene interne Arbeit festhaelt; {@code null} gilt als extern
 * @param positionen die vollstaendige Positionsliste in der gewuenschten Reihenfolge
 */
public record AngebotRequest(
    @NotNull LocalDate angebotDatum,
    @NotBlank @Size(max = 200) String titel,
    @Nullable Long ansprechpartnerId,
    @Nullable String beschreibung,
    @Nullable Boolean intern,
    @NotNull @Valid List<AngebotPositionRequest> positionen) {

  /** Dieselben Angaben in der Sprache der Anwendungsschicht. */
  AngebotDaten daten() {
    return new AngebotDaten(
        angebotDatum,
        ansprechpartnerId,
        titel,
        beschreibung,
        Boolean.TRUE.equals(intern),
        positionen.stream().map(AngebotPositionRequest::angabe).toList());
  }
}
