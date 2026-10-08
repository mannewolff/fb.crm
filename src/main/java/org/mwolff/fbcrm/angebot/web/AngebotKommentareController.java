package org.mwolff.fbcrm.angebot.web;

import jakarta.validation.Valid;
import org.mwolff.fbcrm.angebot.application.AngebotskommentarUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Die Wege der Kommentare am Angebot (Issue #140, Plan #141 E4).
 *
 * <p>Eigene Wege unter {@code /api/angebote/{angebotId}/kommentare} und keine Liste in {@code
 * AngebotResponse}: Die Angebots-Antwort kommt auch nach dem Aendern und nach jedem Statuswechsel
 * zurueck und truege die Kommentare sonst bei jedem dieser Wege mit.
 *
 * <p>Eine eigene Klasse und keine Methode im {@link AngebotController}, obwohl der Pfad unter
 * dessen Praefix liegt: Dort steht das Angebot selbst, hier ein Bestand daneben mit eigener Kennung
 * im Pfad.
 *
 * <p>Der Controller entscheidet nichts (CLAUDE-java.md §6.3). Warum {@code POST} und {@code PUT}
 * mit einem Rumpf antworten: Kennung und Zeitpunkt entstehen erst im Anwendungsfall, und der
 * Bereich zeigt den neuen Kommentar ohne Neuladen (Kriterien 2, 4).
 *
 * <p>Keine Aenderung an {@code SecurityConfig}: Die Wege fallen unter das bestehende {@code
 * /api/**} fuer angemeldete Benutzer (E11).
 */
@RestController
@RequestMapping("/api/angebote/{angebotId}/kommentare")
public class AngebotKommentareController {

  private final AngebotskommentarUseCase useCase;

  public AngebotKommentareController(final AngebotskommentarUseCase useCase) {
    this.useCase = useCase;
  }

  /** Die Kommentare des Angebots, neuester zuerst (Kriterium 4). */
  @GetMapping
  public AngebotKommentareResponse liste(@PathVariable final long angebotId) {
    return AngebotKommentareResponse.of(useCase.liste(angebotId));
  }

  /** Schreibt einen Kommentar an das Angebot (Kriterium 2). */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public AngebotKommentarResponse schreiben(
      @PathVariable final long angebotId,
      @Valid @RequestBody final AngebotKommentarRequest anfrage) {
    return AngebotKommentarResponse.of(useCase.schreibe(angebotId, anfrage.text()));
  }

  /** Aendert den Text eines Kommentars (Kriterium 9). */
  @PutMapping("/{kommentarId}")
  public AngebotKommentarResponse aendern(
      @PathVariable final long angebotId,
      @PathVariable final long kommentarId,
      @Valid @RequestBody final AngebotKommentarRequest anfrage) {
    return AngebotKommentarResponse.of(useCase.aendere(angebotId, kommentarId, anfrage.text()));
  }

  /** Loescht den Kommentar (Kriterium 10). */
  @DeleteMapping("/{kommentarId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void loeschen(@PathVariable final long angebotId, @PathVariable final long kommentarId) {
    useCase.loesche(angebotId, kommentarId);
  }
}
