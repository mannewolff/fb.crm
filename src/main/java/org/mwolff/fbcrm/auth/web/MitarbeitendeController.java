package org.mwolff.fbcrm.auth.web;

import jakarta.validation.Valid;
import org.mwolff.fbcrm.auth.application.MitarbeitendeVerwaltung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Die Wege der Kontenverwaltung: Liste, Anlegen, Aendern, Stilllegen, Wiederaktivieren (Plan #414,
 * A12; fachliche Quelle #409, Kriterien 3 bis 8).
 *
 * <p>Nur fuer C-Level, mit jeder Methode, auch lesend — das sagt die Zugangsregel in {@link
 * SecurityConfig}, nicht dieser Controller.
 *
 * <p>Der Controller entscheidet nichts: Er nimmt die Eingaben, delegiert an {@link
 * MitarbeitendeVerwaltung} und uebersetzt das Ergebnis in Statuscode und Antwortrumpf
 * (CLAUDE-java.md §6.3). Wie bei der Firma antworten Stilllegen und Wiederaktivieren ohne Rumpf;
 * die Oberflaeche liest den neuen Stand ueber die Liste.
 */
@RestController
@RequestMapping("/api/mitarbeitende")
public class MitarbeitendeController {

  private final MitarbeitendeVerwaltung verwaltung;

  public MitarbeitendeController(final MitarbeitendeVerwaltung verwaltung) {
    this.verwaltung = verwaltung;
  }

  /** Jedes Konto, aktiv oder stillgelegt (Kriterium 3). */
  @GetMapping
  public MitarbeitendeResponse liste() {
    return MitarbeitendeResponse.of(verwaltung.liste());
  }

  /** Legt ein Konto an und stellt die Begruessung ein (Kriterium 4). */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public MitarbeiterResponse anlegen(@Valid @RequestBody final MitarbeiterRequest anfrage) {
    return MitarbeiterResponse.angelegt(
        verwaltung.anlegen(anfrage.email(), anfrage.displayName(), anfrage.role()));
  }

  /** Schreibt Adresse, Name und Rolle fort (Kriterium 6). */
  @PutMapping("/{id}")
  public MitarbeiterResponse aendern(
      @PathVariable final long id, @Valid @RequestBody final MitarbeiterRequest anfrage) {
    return MitarbeiterResponse.of(
        verwaltung.aendern(id, anfrage.email(), anfrage.displayName(), anfrage.role()));
  }

  /** Legt das Konto still (Kriterium 7). */
  @PostMapping("/{id}/stilllegen")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void stilllegen(@PathVariable final long id) {
    verwaltung.stilllegen(id);
  }

  /** Nimmt das Konto wieder in Betrieb (Kriterium 7). */
  @PostMapping("/{id}/aktivieren")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void aktivieren(@PathVariable final long id) {
    verwaltung.aktivieren(id);
  }
}
