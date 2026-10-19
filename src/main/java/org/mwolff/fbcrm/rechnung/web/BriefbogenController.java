package org.mwolff.fbcrm.rechnung.web;

import jakarta.validation.Valid;
import java.io.IOException;
import java.util.Objects;
import org.mwolff.fbcrm.rechnung.application.BriefbogenLesenUseCase;
import org.mwolff.fbcrm.rechnung.application.BriefbogenPflegenUseCase;
import org.mwolff.fbcrm.rechnung.application.BriefpapierUseCase;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Stand und Pflege des Briefbogens und das Briefpapier selbst (fachliche Quelle #361; Plan #363,
 * E1).
 *
 * <p>Der Weg liegt im Modul {@code rechnung}, weil die Randpruefung mit dem Rechnungssatz misst;
 * die Oberflaeche bleibt bei den eigenen Angaben. Der Weg fuer den Probedruck kommt dazu, wenn es
 * ihn gibt.
 *
 * <p>Der Controller entscheidet nichts (CLAUDE-java.md §6.3). {@code PUT} und beide Wege auf {@code
 * /datei} antworten mit dem neuen Stand. Keine eigene Regel in {@code SecurityConfig}: Der Pfad
 * faellt unter {@code /api/**} fuer angemeldete Benutzer.
 */
@RestController
@RequestMapping("/api/briefpapier")
public class BriefbogenController {

  private final BriefbogenLesenUseCase lesenUseCase;
  private final BriefbogenPflegenUseCase pflegenUseCase;
  private final BriefpapierUseCase briefpapierUseCase;

  public BriefbogenController(
      final BriefbogenLesenUseCase lesenUseCase,
      final BriefbogenPflegenUseCase pflegenUseCase,
      final BriefpapierUseCase briefpapierUseCase) {
    this.lesenUseCase = lesenUseCase;
    this.pflegenUseCase = pflegenUseCase;
    this.briefpapierUseCase = briefpapierUseCase;
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

  /**
   * Hinterlegt das Briefpapier oder ersetzt es (Kriterien 1 und 2) und antwortet mit dem neuen
   * Stand.
   *
   * <p>200 und nicht 201: Das Briefpapier ist keine eigene Ressource mit Kennung, sondern ein Teil
   * des Briefbogens. Ob die Datei ein PDF ist, entscheidet der Anwendungsfall am Inhalt; die
   * gemeldete Art des Teils liest dieser Controller nicht, den Dateinamen reicht er ungesaeubert
   * weiter — ein fehlender Name ist dort ein leerer.
   *
   * @param datei der Teil {@code datei} der Formulardaten
   * @throws IOException wenn sich der Inhalt der hochgeladenen Datei nicht lesen laesst
   */
  @PostMapping("/datei")
  public BriefbogenResponse hochladen(@RequestPart("datei") final MultipartFile datei)
      throws IOException {
    return BriefbogenResponse.of(
        briefpapierUseCase.ladeHoch(
            Objects.requireNonNullElse(datei.getOriginalFilename(), ""), datei.getBytes()));
  }

  /** Entfernt das Briefpapier; Schalter und Raender bleiben (Kriterium 16). */
  @DeleteMapping("/datei")
  public BriefbogenResponse entfernen() {
    return BriefbogenResponse.of(briefpapierUseCase.entferne());
  }
}
