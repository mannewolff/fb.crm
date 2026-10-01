package org.mwolff.fbcrm.arbeitszeit.web;

import jakarta.validation.Valid;
import org.mwolff.fbcrm.arbeitszeit.application.ZeiteintragUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Die Schreibwege der Arbeitszeit (Issue #193, Kriterien 1 und 6).
 *
 * <p>Ein eigener Bestand mit eigener Kennung und darum eigene Wege unter {@code /api/arbeitszeit} —
 * nicht unter dem Angebot: Die Position steht im Rumpf, weil sie sich beim Aendern wechseln laesst
 * (Plan #194, A7), und die Ansicht liest einen Monat quer ueber alle Angebote.
 *
 * <p>Der Controller entscheidet nichts (CLAUDE-java.md §6.3). Warum {@code POST} und {@code PUT}
 * mit einem Rumpf antworten, steht in {@link ZeiteintragResponse}.
 *
 * <p>Keine Aenderung an {@code SecurityConfig}: Die Wege fallen unter das bestehende {@code
 * /api/**} fuer angemeldete Benutzer.
 */
@RestController
@RequestMapping("/api/arbeitszeit")
public class ArbeitszeitController {

  private final ZeiteintragUseCase useCase;

  public ArbeitszeitController(final ZeiteintragUseCase useCase) {
    this.useCase = useCase;
  }

  /** Erfasst eine Arbeitszeit (Kriterium 1). */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ZeiteintragResponse anlegen(@Valid @RequestBody final ZeiteintragRequest anfrage) {
    return ZeiteintragResponse.of(
        useCase.anlegen(anfrage.angebotPositionId(), anfrage.tag(), anfrage.von(), anfrage.bis()));
  }

  /** Aendert einen Zeiteintrag (Kriterium 6). */
  @PutMapping("/{id}")
  public ZeiteintragResponse aendern(
      @PathVariable final long id, @Valid @RequestBody final ZeiteintragRequest anfrage) {
    return ZeiteintragResponse.of(
        useCase.aendern(
            id, anfrage.angebotPositionId(), anfrage.tag(), anfrage.von(), anfrage.bis()));
  }

  /** Loescht einen Zeiteintrag (Kriterium 6, Antwort 4). */
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void loeschen(@PathVariable final long id) {
    useCase.loeschen(id);
  }
}
