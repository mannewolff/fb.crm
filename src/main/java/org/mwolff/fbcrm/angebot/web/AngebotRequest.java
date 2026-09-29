package org.mwolff.fbcrm.angebot.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.angebot.application.AngebotDaten;

/**
 * Die Eingaben der Maske — das Angebot als Ganzes (Issue #127, E8).
 *
 * <p>Ein {@code PUT} mit Datum, Ansprechpartner, Beschreibung und der vollstaendigen Positionsliste
 * in der gewuenschten Reihenfolge; Hinzufuegen, Aendern, Loeschen und Verschieben fallen daraus
 * heraus. Eine leere Liste und eine leere Beschreibung sind zulaessig (Kriterium 9).
 *
 * <p><b>Kein Feld fuer die Firma und den Status.</b> Die Firma bleibt die, bei der das Angebot
 * angelegt wurde (Kriterium 5), und der Status hat seine eigenen Wege (Kriterium 4).
 *
 * <p>Die Beschreibung traegt keine Laengengrenze — sie steht in einer {@code text}-Spalte.
 *
 * @param angebotDatum Datum des Angebots
 * @param ansprechpartnerId Kennung des Ansprechpartners bei der Firma, oder {@code null}
 * @param beschreibung der Text des Angebots, oder {@code null}
 * @param positionen die vollstaendige Positionsliste in der gewuenschten Reihenfolge
 */
public record AngebotRequest(
    @NotNull LocalDate angebotDatum,
    @Nullable Long ansprechpartnerId,
    @Nullable String beschreibung,
    @NotNull @Valid List<AngebotPositionRequest> positionen) {

  /** Dieselben Angaben in der Sprache der Anwendungsschicht. */
  AngebotDaten daten() {
    return new AngebotDaten(
        angebotDatum,
        ansprechpartnerId,
        beschreibung,
        positionen.stream().map(AngebotPositionRequest::position).toList());
  }
}
