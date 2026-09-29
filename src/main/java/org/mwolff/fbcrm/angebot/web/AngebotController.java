package org.mwolff.fbcrm.angebot.web;

import jakarta.validation.Valid;
import org.mwolff.fbcrm.angebot.application.AngebotAendernUseCase;
import org.mwolff.fbcrm.angebot.application.AngebotLesenUseCase;
import org.mwolff.fbcrm.angebot.application.AngebotStatusUseCase;
import org.mwolff.fbcrm.angebot.application.KundenangabenUseCase;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Die Wege am einzelnen Angebot: lesen, aendern, Status weiter und zurueck (Issue #127).
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
 */
@RestController
@RequestMapping("/api/angebote/{id}")
public class AngebotController {

  private final AngebotLesenUseCase lesenUseCase;
  private final AngebotAendernUseCase aendernUseCase;
  private final AngebotStatusUseCase statusUseCase;
  private final KundenangabenUseCase kundenUseCase;

  public AngebotController(
      final AngebotLesenUseCase lesenUseCase,
      final AngebotAendernUseCase aendernUseCase,
      final AngebotStatusUseCase statusUseCase,
      final KundenangabenUseCase kundenUseCase) {
    this.lesenUseCase = lesenUseCase;
    this.aendernUseCase = aendernUseCase;
    this.statusUseCase = statusUseCase;
    this.kundenUseCase = kundenUseCase;
  }

  /** Das Angebot samt seinen Positionen. */
  @GetMapping
  public AngebotResponse lesen(@PathVariable final long id) {
    return antwort(lesenUseCase.lese(id));
  }

  /** Aendert das Angebot als Ganzes und liefert seinen neuen Stand (Kriterium 5, E8). */
  @PutMapping
  public AngebotResponse aendern(
      @PathVariable final long id, @Valid @RequestBody final AngebotRequest anfrage) {
    return antwort(aendernUseCase.aendere(id, anfrage.daten()));
  }

  /** Schaltet den Status eine Stufe weiter (Kriterium 4). */
  @PostMapping("/status/weiter")
  public AngebotResponse statusWeiter(@PathVariable final long id) {
    return antwort(statusUseCase.weiter(id));
  }

  /** Schaltet den Status eine Stufe zurueck (Kriterium 4). */
  @PostMapping("/status/zurueck")
  public AngebotResponse statusZurueck(@PathVariable final long id) {
    return antwort(statusUseCase.zurueck(id));
  }

  private AngebotResponse antwort(final Angebot angebot) {
    return AngebotResponse.of(angebot, kundenUseCase.zu(angebot));
  }
}
