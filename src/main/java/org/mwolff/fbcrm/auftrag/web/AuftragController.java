package org.mwolff.fbcrm.auftrag.web;

import org.mwolff.fbcrm.auftrag.application.AuftragLesenUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Der Weg am einzelnen Auftrag: lesen (Kriterien 3 bis 7).
 *
 * <p>Der Controller entscheidet nichts (CLAUDE-java.md §6.3). Die uebrigen Wege des Auftrags —
 * pflegen, loeschen, die Liste am Vorgang, der Auftragsbestand — kommen mit den Paketen, die sie
 * brauchen.
 */
@RestController
@RequestMapping("/api/auftraege/{id}")
public class AuftragController {

  private final AuftragLesenUseCase lesenUseCase;

  public AuftragController(final AuftragLesenUseCase lesenUseCase) {
    this.lesenUseCase = lesenUseCase;
  }

  /** Der Auftrag samt seinen Positionen und der Nummer seines Quell-Angebots. */
  @GetMapping
  public AuftragResponse lesen(@PathVariable final long id) {
    return AuftragResponse.of(lesenUseCase.lese(id));
  }
}
