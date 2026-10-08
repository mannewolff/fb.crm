package org.mwolff.fbcrm.arbeitszeit.web;

import jakarta.validation.Valid;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.mwolff.fbcrm.arbeitszeit.application.ArbeitszeitMonatUseCase;
import org.mwolff.fbcrm.arbeitszeit.application.BuchbarePositionenUseCase;
import org.mwolff.fbcrm.arbeitszeit.application.ZeiteintragUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Die Wege der Arbeitszeit (Issue #193, Kriterien 1, 5 und 6).
 *
 * <p>Ein eigener Bestand mit eigener Kennung und darum eigene Wege unter {@code /api/arbeitszeit} —
 * nicht unter dem Angebot: Die Position steht im Rumpf, weil sie sich beim Aendern wechseln laesst
 * (Plan #194, A7), und die Ansicht liest einen Monat quer ueber alle Angebote.
 *
 * <p>Der Controller entscheidet nichts (CLAUDE-java.md §6.3). Warum {@code POST} und {@code PUT}
 * mit einem Rumpf antworten, steht in {@link ZeiteintragResponse}.
 *
 * <p><b>Der Monat ist optional.</b> Fehlt er, fragt der Controller ohne Monat, und welcher der
 * laufende ist, entscheidet der Anwendungsfall an seiner Uhr in der Geschaeftszone (E4) — der
 * Controller entscheidet nichts. Ein Wert, der kein Monat ist, laesst sich nicht wandeln und kommt
 * als 400 zurueck ({@code GlobalExceptionHandler}); eine eigene Pruefung dafuer waere eine zweite
 * Abschrift derselben Regel.
 *
 * <p>Keine Aenderung an {@code SecurityConfig}: Die Wege fallen unter das bestehende {@code
 * /api/**} fuer angemeldete Benutzer.
 */
@RestController
@RequestMapping("/api/arbeitszeit")
public class ArbeitszeitController {

  private final ZeiteintragUseCase useCase;
  private final ArbeitszeitMonatUseCase monate;
  private final BuchbarePositionenUseCase buchbare;

  public ArbeitszeitController(
      final ZeiteintragUseCase useCase,
      final ArbeitszeitMonatUseCase monate,
      final BuchbarePositionenUseCase buchbare) {
    this.useCase = useCase;
    this.monate = monate;
    this.buchbare = buchbare;
  }

  /**
   * Die Eintraege eines Monats nach Tagen, mit den Summen (Kriterium 5).
   *
   * @param monat der gesuchte Monat als {@code JJJJ-MM}, oder weggelassen fuer den laufenden
   */
  @GetMapping
  public ArbeitszeitMonatResponse monat(
      @RequestParam(required = false) final Optional<YearMonth> monat) {
    return ArbeitszeitMonatResponse.of(monate.monat(monat));
  }

  /** Die Positionen, auf die gebucht werden darf — die Auswahlliste des Dialogs (A15). */
  @GetMapping("/buchbare-positionen")
  public List<BuchungspositionResponse> buchbarePositionen() {
    return buchbare.positionen().stream().map(BuchungspositionResponse::of).toList();
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
