package org.mwolff.fbcrm.auftrag.web;

import org.mwolff.fbcrm.auftrag.application.AuftragsbestandUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Der Weg zur Auswertung „Auftragsbestand" (Kriterien 12, 13) — der siebte und letzte Weg der
 * Schnittstelle des Auftrags.
 *
 * <p>Der Pfad liegt nicht unter {@code /api/auftraege}, obwohl das Modul {@code auftrag} ihn haelt:
 * Der Bestand ist eine Auswertung und kein Unterweg eines Auftrags — dieselbe Ueberlegung wie bei
 * {@code PipelineController}.
 *
 * <p>Der Controller entscheidet nichts: Er reicht weiter und uebersetzt das Ergebnis in den
 * Antwortrumpf (CLAUDE-java.md §6.3). Ohne Parameter — der Bestand zeigt immer alles Laufende.
 */
@RestController
@RequestMapping("/api/auftragsbestand")
public class AuftragsbestandController {

  private final AuftragsbestandUseCase useCase;

  public AuftragsbestandController(final AuftragsbestandUseCase useCase) {
    this.useCase = useCase;
  }

  /** Die laufenden Auftraege mit „Beauftragt" und „Noch offen". */
  @GetMapping
  public AuftragsbestandResponse auftragsbestand() {
    return AuftragsbestandResponse.of(useCase.bestand());
  }
}
