package org.mwolff.fbcrm.vorgang.web;

import org.mwolff.fbcrm.vorgang.application.VorgaengeUebersichtUseCase;
import org.mwolff.fbcrm.vorgang.application.VorgangLesenUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Die Lesewege des Vorgangs: Uebersicht und Detailansicht.
 *
 * <p>Der Controller entscheidet nichts: Er nimmt die Eingaben, reicht sie in der Sprache der
 * Anwendungsschicht weiter und uebersetzt das Ergebnis in Statuscode und Antwortrumpf
 * (CLAUDE-java.md §6.3). Auch die Nummernerkennung des Suchtexts ist Sache der Anwendungsschicht
 * (E17) — hier kommt der Text unveraendert an.
 *
 * <p>Die Schreibwege gehoeren in dieselbe Klasse und kommen als eigene Methoden hinzu: Ein Pfad,
 * eine Klasse — das Modul {@code firma} haelt es genauso.
 */
@RestController
@RequestMapping("/api/vorgaenge")
public class VorgangController {

  private final VorgaengeUebersichtUseCase uebersichtUseCase;
  private final VorgangLesenUseCase lesenUseCase;

  public VorgangController(
      final VorgaengeUebersichtUseCase uebersichtUseCase, final VorgangLesenUseCase lesenUseCase) {
    this.uebersichtUseCase = uebersichtUseCase;
    this.lesenUseCase = lesenUseCase;
  }

  /**
   * Die Uebersicht (Kriterien 2, 3, 20).
   *
   * @param suche Teil des Titels, des Firmennamens oder die Nummer mit oder ohne {@code #}; ohne
   *     Angabe trifft die Suche jeden Vorgang
   * @param auchAbgeschlossene ohne Angabe bleiben abgeschlossene Vorgaenge aussen vor
   */
  @GetMapping
  public VorgaengeUebersichtResponse uebersicht(
      @RequestParam(defaultValue = "") final String suche,
      @RequestParam(defaultValue = "false") final boolean auchAbgeschlossene) {
    return VorgaengeUebersichtResponse.of(uebersichtUseCase.uebersicht(suche, auchAbgeschlossene));
  }

  /** Die Detailansicht samt Historie (Kriterien 9, 15, 23). */
  @GetMapping("/{id}")
  public VorgangResponse lesen(@PathVariable final long id) {
    return VorgangResponse.of(lesenUseCase.lese(id));
  }
}
