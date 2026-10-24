package org.mwolff.fbcrm.eigeneangaben.web;

import jakarta.validation.Valid;
import org.mwolff.fbcrm.common.web.Bedingungskopf;
import org.mwolff.fbcrm.eigeneangaben.application.EigeneAngabenLesenUseCase;
import org.mwolff.fbcrm.eigeneangaben.application.EigeneAngabenPflegenUseCase;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Die zwei Wege der eigenen Angaben: lesen und fortschreiben (Kriterium 1).
 *
 * <p>Der Controller entscheidet nichts: Er nimmt die Eingaben, reicht sie in der Sprache der
 * Fachschicht weiter und uebersetzt das Ergebnis in Statuscode und Antwortrumpf (CLAUDE-java.md
 * §6.3).
 *
 * <p>Kein {@code POST} und kein {@code DELETE}: Die eine Zeile gibt es von der Migration an, und
 * sie verschwindet nicht wieder.
 *
 * <p><b>{@code PUT} legt den Stand vor</b> (#411, Kriterium 4; Plan #415, E2, E3, E14): Er liest
 * {@link Bedingungskopf} aus {@code If-Match} und das handelnde Konto aus der Sitzung. Fehlt der
 * Kopf, antwortet der Weg mit 428, ohne den Anwendungsfall zu fragen. Er antwortet mit 200 und dem
 * neuen Stand: Das Speichern erhoeht die Version, und die Maske legt sie beim naechsten Speichern
 * vor.
 */
@RestController
@RequestMapping("/api/eigene-angaben")
public class EigeneAngabenController {

  private final EigeneAngabenLesenUseCase lesenUseCase;
  private final EigeneAngabenPflegenUseCase pflegenUseCase;

  public EigeneAngabenController(
      final EigeneAngabenLesenUseCase lesenUseCase,
      final EigeneAngabenPflegenUseCase pflegenUseCase) {
    this.lesenUseCase = lesenUseCase;
    this.pflegenUseCase = pflegenUseCase;
  }

  /** Die eigenen Angaben; auf einer frischen Instanz mit lauter leeren Feldern. */
  @GetMapping
  public EigeneAngabenResponse lesen() {
    return EigeneAngabenResponse.of(lesenUseCase.lese());
  }

  /**
   * Schreibt die Angaben fort und antwortet mit dem neuen Stand.
   *
   * <p>Der Kopf steht vor dem Rumpf: Fehlt er, weist der Weg mit 428 ab, bevor der Rumpf gelesen
   * und geprueft wird (Plan #415, E3).
   */
  @PutMapping
  public EigeneAngabenResponse pflegen(
      final Bedingungskopf stand,
      @AuthenticationPrincipal final Long accountId,
      @Valid @RequestBody final EigeneAngabenRequest anfrage) {
    return EigeneAngabenResponse.of(
        pflegenUseCase.pflege(anfrage.angaben(), stand.version(), accountId));
  }
}
