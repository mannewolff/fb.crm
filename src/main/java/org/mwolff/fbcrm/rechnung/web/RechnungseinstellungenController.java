package org.mwolff.fbcrm.rechnung.web;

import jakarta.validation.Valid;
import org.mwolff.fbcrm.common.web.Bedingungskopf;
import org.mwolff.fbcrm.rechnung.application.RechnungseinstellungenLesenUseCase;
import org.mwolff.fbcrm.rechnung.application.RechnungseinstellungenPflegenUseCase;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Die zwei Wege der Rechnungseinstellungen: lesen und fortschreiben (Plan #161, E6).
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
 * neuen Stand statt 204: Das Speichern erhoeht die Version, und die Maske legt sie beim naechsten
 * Speichern vor.
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

  /**
   * Schreibt die Einstellungen fort und antwortet mit dem neuen Stand.
   *
   * <p>Der Kopf steht vor dem Rumpf: Fehlt er, weist der Weg mit 428 ab, bevor der Rumpf gelesen
   * und geprueft wird (Plan #415, E3).
   */
  @PutMapping
  public RechnungseinstellungenResponse pflegen(
      final Bedingungskopf stand,
      @AuthenticationPrincipal final Long accountId,
      @Valid @RequestBody final RechnungseinstellungenRequest anfrage) {
    return RechnungseinstellungenResponse.of(
        pflegenUseCase.pflege(
            anfrage.einstellungen(), anfrage.naechsteNummer(), stand.version(), accountId));
  }
}
