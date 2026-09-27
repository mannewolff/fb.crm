package org.mwolff.fbcrm.eigeneangaben.web;

import jakarta.validation.Valid;
import org.mwolff.fbcrm.eigeneangaben.application.EigeneAngabenLesenUseCase;
import org.mwolff.fbcrm.eigeneangaben.application.EigeneAngabenPflegenUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Die zwei Wege der eigenen Angaben: lesen und fortschreiben (Kriterium 1).
 *
 * <p>Der Controller entscheidet nichts: Er nimmt die Eingaben, reicht sie in der Sprache der
 * Fachschicht weiter und uebersetzt das Ergebnis in Statuscode und Antwortrumpf (CLAUDE-java.md
 * §6.3).
 *
 * <p>Kein {@code POST} und kein {@code DELETE}: Die eine Zeile gibt es von der Migration an, und
 * sie verschwindet nicht wieder. {@code PUT} antwortet ohne Rumpf; den neuen Stand liest die
 * Oberflaeche ueber {@code GET /api/eigene-angaben}, wie bei der Firma.
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

  /** Schreibt die Angaben fort. */
  @PutMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void pflegen(@Valid @RequestBody final EigeneAngabenRequest anfrage) {
    pflegenUseCase.pflege(anfrage.angaben());
  }
}
