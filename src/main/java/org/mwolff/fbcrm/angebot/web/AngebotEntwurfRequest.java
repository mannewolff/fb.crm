package org.mwolff.fbcrm.angebot.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.angebot.application.EntwurfDaten;

/**
 * Die Eingaben der Entwurfsmaske — der Entwurf als Ganzes (E8).
 *
 * <p>Ein {@code PUT} mit Texten und der vollstaendigen Positionsliste in der gewuenschten
 * Reihenfolge; Hinzufuegen, Aendern, Loeschen und Verschieben fallen daraus heraus. Eine leere
 * Liste ist zulaessig: Ein Entwurf darf halbfertig sein, und was zum Versenden fehlt, nennt die
 * Versandpruefung (E27).
 *
 * <p><b>Kein Feld fuer das Angebotsdatum, den Zustand oder die Nummer.</b> Das Datum entsteht beim
 * Anlegen (Kriterium 3), der Zustand hat seine eigenen Wege, und die Nummer kommt beim Versenden
 * aus dem Nummernkreis (Kriterium 11); je ein Feld dafuer waere ein zweiter, widersprechbarer Ort.
 *
 * <p>Die Texte tragen keine Laengengrenze — sie stehen in {@code text}-Spalten. Gegen einen
 * uebergrossen Rumpf steht der Riegel des Containers (E10).
 *
 * @param gueltigBis letzter Tag der Gueltigkeit
 * @param leistungsbeschreibung einleitender Text, oder {@code null}
 * @param zahlungsbedingungen Zahlungsbedingungen, oder {@code null}
 * @param positionen die vollstaendige Positionsliste in der gewuenschten Reihenfolge
 */
public record AngebotEntwurfRequest(
    @NotNull LocalDate gueltigBis,
    @Nullable String leistungsbeschreibung,
    @Nullable String zahlungsbedingungen,
    @NotNull @Valid List<AngebotPositionRequest> positionen) {

  /** Dieselben Angaben in der Sprache der Fachschicht. */
  EntwurfDaten daten() {
    return new EntwurfDaten(
        gueltigBis,
        leistungsbeschreibung,
        zahlungsbedingungen,
        positionen.stream().map(AngebotPositionRequest::position).toList());
  }
}
