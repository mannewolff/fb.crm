package org.mwolff.fbcrm.firma.web;

import jakarta.validation.Valid;
import org.mwolff.fbcrm.common.web.Bedingungskopf;
import org.mwolff.fbcrm.firma.application.FirmaAendernUseCase;
import org.mwolff.fbcrm.firma.application.FirmaAnlegenUseCase;
import org.mwolff.fbcrm.firma.application.FirmaLesenUseCase;
import org.mwolff.fbcrm.firma.application.FirmaStilllegenUseCase;
import org.mwolff.fbcrm.firma.application.FirmenUebersichtUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
 * <p><b>Aendern, Stilllegen und Wiederaktivieren legen den Stand vor</b> (#411, Kriterium 4; Plan
 * #415, E2, E3, E14, E18): Sie lesen {@link Bedingungskopf} aus {@code If-Match} und das handelnde
 * Konto aus der Sitzung. Fehlt der Kopf, antwortet der Weg mit 428, ohne den Anwendungsfall zu
 * fragen. Das Anlegen bleibt frei (#411, Kriterium 7).
 *
 * <p><b>Warum die drei Schreibwege die ganze Detailansicht liefern.</b> Die Detailansicht einer
 * Firma enthaelt ihre Ansprechpartner; die drei Anwendungsfaelle laden die aber nicht, weil sie sie
 * nicht brauchen. Darum liest der Weg nach dem Schreiben die Firma neu, wie {@code GET
 * /api/firmen/{id}} — so traegt die Antwort die erhoehte Version, die die Oberflaeche beim
 * naechsten Schritt vorlegt, und keine leere Liste, die an dieser Stelle gelogen waere. Das Anlegen
 * antwortet ohne Ansprechpartner: Eine frisch angelegte Firma hat wirklich keine.
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

  /**
   * Schreibt die Angaben fort (Kriterium 8) und liefert den neuen Stand.
   *
   * <p>Der Kopf steht vor dem Rumpf: Fehlt er, weist der Weg mit 428 ab, bevor der Rumpf gelesen
   * und geprueft wird (Plan #415, E3).
   */
  @PutMapping("/{id}")
  public FirmaResponse aendern(
      @PathVariable final long id,
      final Bedingungskopf stand,
      @AuthenticationPrincipal final Long accountId,
      @Valid @RequestBody final FirmaRequest anfrage) {
    aendernUseCase.aendern(id, anfrage.daten(), stand.version(), accountId);
    return lesen(id);
  }

  /** Legt die Firma still (Kriterium 13) und liefert den neuen Stand. */
  @PostMapping("/{id}/stilllegen")
  public FirmaResponse stilllegen(
      @PathVariable final long id,
      final Bedingungskopf stand,
      @AuthenticationPrincipal final Long accountId) {
    stilllegenUseCase.stilllegen(id, stand.version(), accountId);
    return lesen(id);
  }

  /** Nimmt die Firma wieder in Betrieb (Kriterium 13) und liefert den neuen Stand. */
  @PostMapping("/{id}/aktivieren")
  public FirmaResponse aktivieren(
      @PathVariable final long id,
      final Bedingungskopf stand,
      @AuthenticationPrincipal final Long accountId) {
    stilllegenUseCase.aktivieren(id, stand.version(), accountId);
    return lesen(id);
  }
}
