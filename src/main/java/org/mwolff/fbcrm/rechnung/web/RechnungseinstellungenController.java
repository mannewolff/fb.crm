package org.mwolff.fbcrm.rechnung.web;

import jakarta.validation.Valid;
import org.mwolff.fbcrm.rechnung.application.RechnungseinstellungenLesenUseCase;
import org.mwolff.fbcrm.rechnung.application.RechnungseinstellungenPflegenUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Die zwei Wege der Rechnungseinstellungen: lesen und fortschreiben (Plan #161, E6).
 *
 * <p>Der Controller entscheidet nichts: Er nimmt die Eingaben, reicht sie in der Sprache der
 * Fachschicht weiter und uebersetzt das Ergebnis in Statuscode und Antwortrumpf (CLAUDE-java.md
 * §6.3).
 *
 * <p>Kein {@code POST} und kein {@code DELETE}: Die eine Zeile gibt es von der Migration an, und
 * sie verschwindet nicht wieder. {@code PUT} antwortet ohne Rumpf, wie {@code
 * EigeneAngabenController}; den neuen Stand liest die Oberflaeche ueber den {@code GET}.
 *
 * <p>Keine eigene Regel in {@code SecurityConfig}: Der Pfad faellt unter das bestehende {@code
 * /api/**} fuer angemeldete Benutzer (Plan #161, E8; #159, Kriterium 12).
 */
@RestController
@RequestMapping("/api/rechnung/einstellungen")
public class RechnungseinstellungenController {

  private final RechnungseinstellungenLesenUseCase lesenUseCase;
  private final RechnungseinstellungenPflegenUseCase pflegenUseCase;

  public RechnungseinstellungenController(
      final RechnungseinstellungenLesenUseCase lesenUseCase,
      final RechnungseinstellungenPflegenUseCase pflegenUseCase) {
    this.lesenUseCase = lesenUseCase;
    this.pflegenUseCase = pflegenUseCase;
  }

  /** Die Einstellungen; auf einer frischen Instanz die Vorbelegungen der Migration. */
  @GetMapping
  public RechnungseinstellungenResponse lesen() {
    return RechnungseinstellungenResponse.of(lesenUseCase.lese());
  }

  /** Schreibt die Einstellungen fort. */
  @PutMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void pflegen(@Valid @RequestBody final RechnungseinstellungenRequest anfrage) {
    pflegenUseCase.pflege(anfrage.einstellungen(), anfrage.naechsteNummer());
  }
}
