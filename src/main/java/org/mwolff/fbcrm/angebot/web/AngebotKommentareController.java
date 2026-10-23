package org.mwolff.fbcrm.angebot.web;

import jakarta.validation.Valid;
import org.mwolff.fbcrm.angebot.application.AngebotskommentarUseCase;
import org.mwolff.fbcrm.common.web.Bedingungskopf;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
 *
 * <p>Der Verfasser eines neuen Kommentars ist das angemeldete Konto: Seine Kennung kommt aus der
 * Sitzung ({@link AuthenticationPrincipal}) und nicht aus dem Rumpf (Plan #414, A7).
 *
 * <p><b>Aendern und Loeschen legen den Stand vor</b> (#411, Kriterien 4 und 6; Plan #415, E2, E3,
 * E8, E14): {@code PUT} und {@code DELETE} lesen {@link Bedingungskopf} und das angemeldete Konto;
 * ohne {@code If-Match} antworten sie 428, ohne den Anwendungsfall zu fragen. {@code POST} bleibt
 * ohne Kopf — ein neuer Kommentar ist nie geschuetzt (Kriterium 7).
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
      @AuthenticationPrincipal final Long accountId,
      @Valid @RequestBody final AngebotKommentarRequest anfrage) {
    return AngebotKommentarResponse.of(useCase.schreibe(angebotId, accountId, anfrage.text()));
  }

  /**
   * Aendert den Text eines Kommentars (Kriterium 9) auf dem vorgelegten Stand.
   *
   * <p>Der Kopf steht vor dem Rumpf: Fehlt er, weist der Weg mit 428 ab, bevor der Rumpf gelesen
   * und geprueft wird (Plan #415, E3).
   */
  @PutMapping("/{kommentarId}")
  public AngebotKommentarResponse aendern(
      @PathVariable final long angebotId,
      @PathVariable final long kommentarId,
      final Bedingungskopf stand,
      @AuthenticationPrincipal final Long accountId,
      @Valid @RequestBody final AngebotKommentarRequest anfrage) {
    return AngebotKommentarResponse.of(
        useCase.aendere(angebotId, kommentarId, stand.version(), accountId, anfrage.text()));
  }

  /** Loescht den Kommentar mit Kennzeichen auf dem vorgelegten Stand (Kriterium 10; E17). */
  @DeleteMapping("/{kommentarId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void loeschen(
      @PathVariable final long angebotId,
      @PathVariable final long kommentarId,
      final Bedingungskopf stand,
      @AuthenticationPrincipal final Long accountId) {
    useCase.loesche(angebotId, kommentarId, stand.version(), accountId);
  }
}
