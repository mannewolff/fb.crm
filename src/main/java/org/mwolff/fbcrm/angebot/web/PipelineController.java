package org.mwolff.fbcrm.angebot.web;

import org.mwolff.fbcrm.angebot.application.PipelineUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Der Weg zur Auswertung „Pipeline" (Kriterien 23, 24, 26).
 *
 * <p>Der Pfad liegt nicht unter {@code /api/angebote}, obwohl das Modul {@code angebot} ihn haelt:
 * Die Pipeline ist eine Auswertung und kein Unterweg eines Angebots — dieselbe Ueberlegung wie bei
 * {@code VorgangAngeboteController}, nur in die andere Richtung. Gehalten wird sie hier, weil sie
 * aus Angeboten besteht und das Modul sie ohne Rueckkante vom Vorgang beantworten kann.
 *
 * <p>Der Controller entscheidet nichts: Er reicht weiter und uebersetzt das Ergebnis in den
 * Antwortrumpf (CLAUDE-java.md §6.3). Ohne Parameter — die Pipeline zeigt immer alles Offene; eine
 * Einschraenkung verlangt die fachliche Quelle nicht.
 */
@RestController
@RequestMapping("/api/pipeline")
public class PipelineController {

  private final PipelineUseCase useCase;

  public PipelineController(final PipelineUseCase useCase) {
    this.useCase = useCase;
  }

  /** Die offenen Angebote mit der ungewichteten und der gewichteten Summe. */
  @GetMapping
  public PipelineResponse pipeline() {
    return PipelineResponse.of(useCase.pipeline());
  }
}
