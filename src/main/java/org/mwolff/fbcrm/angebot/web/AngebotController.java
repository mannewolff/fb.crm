package org.mwolff.fbcrm.angebot.web;

import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.angebot.application.AngebotAendernUseCase;
import org.mwolff.fbcrm.angebot.application.AngebotLesenUseCase;
import org.mwolff.fbcrm.angebot.application.AngebotStatusUseCase;
import org.mwolff.fbcrm.angebot.application.AngebotStornoUseCase;
import org.mwolff.fbcrm.angebot.application.KundenangabenUseCase;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.common.web.Bedingungskopf;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Die Wege am einzelnen Angebot: lesen, aendern, Status weiter und zurueck (Issue #127), stornieren
 * und das Storno zuruecknehmen (Issue #334).
 *
 * <p>Der Controller entscheidet nichts (CLAUDE-java.md §6.3). Jede Antwort traegt das Angebot samt
 * den Namen seines Kunden; die fragt er ueber {@link KundenangabenUseCase} hinzu.
 *
 * <p><b>Warum {@code PUT} mit einem Rumpf antwortet.</b> Nach dem Aendern haben sich die
 * gerechneten Werte geaendert — Summe und Positionsbetraege —, und die Maske soll sie ohne zweiten
 * Aufruf zeigen.
 *
 * <p><b>Warum es „weiter" und „zurueck" als eigene Pfade gibt</b> und nicht einen Weg mit dem
 * Zielstatus im Rumpf: Der Wechsel geht immer genau eine Stufe (Kriterium 4), und der Pfad macht
 * ihn im Zugriffsprotokoll lesbar. Die Antwort traegt das Angebot im neuen Status.
 *
 * <p><b>Warum das Storno eine Unterressource mit {@code POST} und {@code DELETE} ist</b> (Plan
 * #332, E5): Das Storno traegt einen Grund und braucht darum einen Rumpf, die Ruecknahme braucht
 * keinen und ist als {@code DELETE} auf denselben Pfad ablesbar. Beide antworten mit dem Angebot in
 * seinem neuen Stand, wie die Statuswege.
 *
 * <p><b>Jeder schreibende Weg legt den Stand vor</b> (#411, Kriterien 4 und 5; Plan #415, E2, E3,
 * E14): Aendern, beide Statuswege, Storno und Ruecknahme lesen {@link Bedingungskopf} aus {@code
 * If-Match} und das handelnde Konto aus der Sitzung. Fehlt der Kopf, antwortet der Weg mit 428,
 * ohne den Anwendungsfall zu fragen.
 */
@RestController
@RequestMapping("/api/angebote/{id}")
public class AngebotController {

  private final AngebotLesenUseCase lesenUseCase;
  private final AngebotAendernUseCase aendernUseCase;
  private final AngebotStatusUseCase statusUseCase;
  private final AngebotStornoUseCase stornoUseCase;
  private final KundenangabenUseCase kundenUseCase;

  public AngebotController(
      final AngebotLesenUseCase lesenUseCase,
      final AngebotAendernUseCase aendernUseCase,
      final AngebotStatusUseCase statusUseCase,
      final AngebotStornoUseCase stornoUseCase,
      final KundenangabenUseCase kundenUseCase) {
    this.lesenUseCase = lesenUseCase;
    this.aendernUseCase = aendernUseCase;
    this.statusUseCase = statusUseCase;
    this.stornoUseCase = stornoUseCase;
    this.kundenUseCase = kundenUseCase;
  }

  /** Das Angebot samt seinen Positionen. */
  @GetMapping
  public AngebotResponse lesen(@PathVariable final long id) {
    return antwort(lesenUseCase.lese(id));
  }

  /**
   * Aendert das Angebot als Ganzes und liefert seinen neuen Stand (Kriterium 5, E8).
   *
   * <p>Der vorgelegte Stand kommt aus {@code If-Match}, das handelnde Konto aus der Sitzung (Plan
   * #415, E2, E14). Der Kopf steht vor dem Rumpf: Fehlt er, weist der Weg mit 428 ab, bevor der
   * Rumpf gelesen und geprueft wird (E3).
   */
  @PutMapping
  public AngebotResponse aendern(
      @PathVariable final long id,
      final Bedingungskopf stand,
      @AuthenticationPrincipal final Long accountId,
      @Valid @RequestBody final AngebotRequest anfrage) {
    return antwort(aendernUseCase.aendere(id, anfrage.daten(stand.version(), accountId)));
  }

  /** Schaltet den Status eine Stufe weiter (Kriterium 4), auf dem vorgelegten Stand. */
  @PostMapping("/status/weiter")
  public AngebotResponse statusWeiter(
      @PathVariable final long id,
      final Bedingungskopf stand,
      @AuthenticationPrincipal final Long accountId) {
    return antwort(statusUseCase.weiter(id, stand.version(), accountId));
  }

  /** Schaltet den Status eine Stufe zurueck (Kriterium 4), auf dem vorgelegten Stand. */
  @PostMapping("/status/zurueck")
  public AngebotResponse statusZurueck(
      @PathVariable final long id,
      final Bedingungskopf stand,
      @AuthenticationPrincipal final Long accountId) {
    return antwort(statusUseCase.zurueck(id, stand.version(), accountId));
  }

  /**
   * Storniert das Angebot, mit oder ohne Grund (#330, Kriterien 1 und 5). Ein fehlender Rumpf
   * heisst „ohne Grund".
   */
  @PostMapping("/storno")
  public AngebotResponse stornieren(
      @PathVariable final long id,
      final Bedingungskopf stand,
      @AuthenticationPrincipal final Long accountId,
      @Valid @RequestBody(required = false) final @Nullable AngebotStornoRequest anfrage) {
    return antwort(
        stornoUseCase.storniere(
            id, anfrage == null ? null : anfrage.grund(), stand.version(), accountId));
  }

  /** Nimmt das Storno zurueck (#330, Kriterium 9), auf dem vorgelegten Stand. */
  @DeleteMapping("/storno")
  public AngebotResponse stornoZuruecknehmen(
      @PathVariable final long id,
      final Bedingungskopf stand,
      @AuthenticationPrincipal final Long accountId) {
    return antwort(stornoUseCase.nimmStornoZurueck(id, stand.version(), accountId));
  }

  private AngebotResponse antwort(final Angebot angebot) {
    return AngebotResponse.of(angebot, kundenUseCase.zu(angebot));
  }
}
