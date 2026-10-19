package org.mwolff.fbcrm.rechnung.web;

import jakarta.validation.Valid;
import org.mwolff.fbcrm.rechnung.application.BriefbogenLesenUseCase;
import org.mwolff.fbcrm.rechnung.application.BriefbogenPflegenUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stand und Pflege des Briefbogens (fachliche Quelle #361; Plan #363, E1).
 *
 * <p>Der Weg liegt im Modul {@code rechnung}, weil die Randpruefung mit dem Rechnungssatz misst;
 * die Oberflaeche bleibt bei den eigenen Angaben. Die Wege fuer die Datei und den Probedruck kommen
 * dazu, wenn es sie gibt.
 *
 * <p>Der Controller entscheidet nichts (CLAUDE-java.md §6.3). {@code PUT} antwortet mit dem neuen
 * Stand. Keine eigene Regel in {@code SecurityConfig}: Der Pfad faellt unter {@code /api/**} fuer
 * angemeldete Benutzer.
 */
@RestController
@RequestMapping("/api/briefpapier")
public class BriefbogenController {

  private final BriefbogenLesenUseCase lesenUseCase;
  private final BriefbogenPflegenUseCase pflegenUseCase;

  public BriefbogenController(
      final BriefbogenLesenUseCase lesenUseCase, final BriefbogenPflegenUseCase pflegenUseCase) {
    this.lesenUseCase = lesenUseCase;
    this.pflegenUseCase = pflegenUseCase;
  }

  /** Der Stand; auf einer frischen Instanz die Vorgaben der Migration ohne Briefpapier. */
  @GetMapping
  public BriefbogenResponse lesen() {
    return BriefbogenResponse.of(lesenUseCase.lese());
  }

  /** Schreibt Schalter und Raender fort und antwortet mit dem neuen Stand. */
  @PutMapping
  public BriefbogenResponse pflegen(@Valid @RequestBody final BriefbogenRequest anfrage) {
    return BriefbogenResponse.of(
        pflegenUseCase.pflege(
            anfrage.absenderzeile(), anfrage.steuerBankZeile(), anfrage.raender()));
  }
}
