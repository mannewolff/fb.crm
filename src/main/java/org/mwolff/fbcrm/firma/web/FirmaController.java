package org.mwolff.fbcrm.firma.web;

import jakarta.validation.Valid;
import org.mwolff.fbcrm.firma.application.FirmaAendernUseCase;
import org.mwolff.fbcrm.firma.application.FirmaAnlegenUseCase;
import org.mwolff.fbcrm.firma.application.FirmaLesenUseCase;
import org.mwolff.fbcrm.firma.application.FirmaStilllegenUseCase;
import org.mwolff.fbcrm.firma.application.FirmenUebersichtUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Die Wege der Firma: Uebersicht, Detail, Anlegen, Aendern, Stilllegen, Wiederaktivieren.
 *
 * <p>Der Controller entscheidet nichts: Er nimmt die Eingaben, reicht sie in der Sprache der
 * Anwendungsschicht weiter und uebersetzt das Ergebnis in Statuscode und Antwortrumpf
 * (CLAUDE-java.md §6.3).
 *
 * <p><b>Warum die drei Schreibwege ohne Rumpf antworten.</b> Die Detailansicht einer Firma enthaelt
 * ihre Ansprechpartner; die drei Anwendungsfaelle laden die aber nicht, weil sie sie nicht
 * brauchen. Eine Antwort mit leerer Liste waere an dieser Stelle gelogen — deshalb 204, und die
 * Oberflaeche liest den neuen Stand ueber {@code GET /api/firmen/{id}}. Nur das Anlegen antwortet
 * mit einem Rumpf: Die Oberflaeche braucht die Kennung fuer den Weg zur Detailansicht (Kriterium
 * 7), und eine frisch angelegte Firma hat wirklich keine Ansprechpartner.
 *
 * <p>Einen eigenen Weg zu den Ansprechpartnern gibt es hier nicht — sie kommen eingebettet (E7).
 */
@RestController
@RequestMapping("/api/firmen")
public class FirmaController {

  private final FirmenUebersichtUseCase uebersichtUseCase;
  private final FirmaLesenUseCase lesenUseCase;
  private final FirmaAnlegenUseCase anlegenUseCase;
  private final FirmaAendernUseCase aendernUseCase;
  private final FirmaStilllegenUseCase stilllegenUseCase;

  public FirmaController(
      final FirmenUebersichtUseCase uebersichtUseCase,
      final FirmaLesenUseCase lesenUseCase,
      final FirmaAnlegenUseCase anlegenUseCase,
      final FirmaAendernUseCase aendernUseCase,
      final FirmaStilllegenUseCase stilllegenUseCase) {
    this.uebersichtUseCase = uebersichtUseCase;
    this.lesenUseCase = lesenUseCase;
    this.anlegenUseCase = anlegenUseCase;
    this.aendernUseCase = aendernUseCase;
    this.stilllegenUseCase = stilllegenUseCase;
  }

  /**
   * Die Uebersicht (Kriterien 2, 3, 13).
   *
   * @param suche Teil des Namens; ohne Angabe trifft die Suche jede Firma
   * @param auchStillgelegte ohne Angabe bleiben stillgelegte Firmen aussen vor
   */
  @GetMapping
  public FirmenUebersichtResponse uebersicht(
      @RequestParam(defaultValue = "") final String suche,
      @RequestParam(defaultValue = "false") final boolean auchStillgelegte) {
    return FirmenUebersichtResponse.of(uebersichtUseCase.uebersicht(suche, auchStillgelegte));
  }

  /** Legt eine Firma an (Kriterien 4, 7). */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public FirmaResponse anlegen(@Valid @RequestBody final FirmaRequest anfrage) {
    return FirmaResponse.ohneAnsprechpartner(anlegenUseCase.anlegen(anfrage.daten()));
  }

  /** Die Detailansicht samt Ansprechpartnern (Kriterien 5, 10). */
  @GetMapping("/{id}")
  public FirmaResponse lesen(@PathVariable final long id) {
    return FirmaResponse.of(lesenUseCase.lese(id));
  }

  /** Schreibt die Angaben fort (Kriterium 8). */
  @PutMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void aendern(@PathVariable final long id, @Valid @RequestBody final FirmaRequest anfrage) {
    aendernUseCase.aendern(id, anfrage.daten());
  }

  /** Legt die Firma still (Kriterium 13). */
  @PostMapping("/{id}/stilllegen")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void stilllegen(@PathVariable final long id) {
    stilllegenUseCase.stilllegen(id);
  }

  /** Nimmt die Firma wieder in Betrieb (Kriterium 13). */
  @PostMapping("/{id}/aktivieren")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void aktivieren(@PathVariable final long id) {
    stilllegenUseCase.aktivieren(id);
  }
}
