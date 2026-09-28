package org.mwolff.fbcrm.auftrag.web;

import org.mwolff.fbcrm.auftrag.application.AuftraegeDesVorgangsUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Der Weg am Vorgang: seine Auftragsliste (Kriterium 9).
 *
 * <p>Eine eigene Klasse und keine Methode im {@link AuftragController}: Dessen Pfadpraefix steht
 * auf {@code /api/auftraege/{id}}, und dieser Weg liegt unter dem Vorgang — dasselbe Muster wie
 * {@code angebot.web.VorgangAngeboteController}. Gehalten wird er vom Modul {@code auftrag}, damit
 * {@code vorgang} nichts vom Auftrag wissen muss.
 *
 * <p>Der Controller entscheidet nichts (CLAUDE-java.md §6.3) — insbesondere nicht die Reihenfolge:
 * Die ist eine Aussage der Anwendungsschicht (Plan E13) und wird hier nur durchgereicht.
 */
@RestController
@RequestMapping("/api/vorgaenge/{vorgangId}/auftraege")
public class VorgangAuftraegeController {

  private final AuftraegeDesVorgangsUseCase listeUseCase;

  public VorgangAuftraegeController(final AuftraegeDesVorgangsUseCase listeUseCase) {
    this.listeUseCase = listeUseCase;
  }

  /** Die Auftraege des Vorgangs, der juengste oben (Kriterium 9). */
  @GetMapping
  public VorgangAuftraegeResponse auftraege(@PathVariable final long vorgangId) {
    return VorgangAuftraegeResponse.of(listeUseCase.auftraege(vorgangId));
  }
}
