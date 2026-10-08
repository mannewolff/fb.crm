package org.mwolff.fbcrm.firma.web;

import jakarta.validation.Valid;
import org.mwolff.fbcrm.firma.application.AnsprechpartnerAendernUseCase;
import org.mwolff.fbcrm.firma.application.AnsprechpartnerAnlegenUseCase;
import org.mwolff.fbcrm.firma.application.AnsprechpartnerStilllegenUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Die Schreibwege des Ansprechpartners: Anlegen, Aendern, Stilllegen, Wiederaktivieren.
 *
 * <p><b>Alles unter der Firma.</b> Jeder Weg liegt unter {@code
 * /api/firmen/{firmaId}/ansprechpartner}, und die Kennung aus dem Pfad geht in jeden Anwendungsfall
 * hinein. Der Rumpf traegt nichts ueber die Firma, und ein untergeschobenes Feld wird nicht gelesen
 * — damit gibt es keinen Weg, einen Ansprechpartner umzuhaengen (E7, Kriterium 12).
 *
 * <p>Einen Leseweg gibt es hier nicht: Die Ansprechpartner kommen eingebettet in der Detailantwort
 * ihrer Firma (E7). Aus demselben Grund antworten die drei Wege ohne Rumpf mit 204 — nur das
 * Anlegen bringt einen, weil ein frisch angelegter Ansprechpartner eine Kennung hat, die die
 * Oberflaeche noch nicht kennt.
 */
@RestController
@RequestMapping("/api/firmen/{firmaId}/ansprechpartner")
public class AnsprechpartnerController {

  private final AnsprechpartnerAnlegenUseCase anlegenUseCase;
  private final AnsprechpartnerAendernUseCase aendernUseCase;
  private final AnsprechpartnerStilllegenUseCase stilllegenUseCase;

  public AnsprechpartnerController(
      final AnsprechpartnerAnlegenUseCase anlegenUseCase,
      final AnsprechpartnerAendernUseCase aendernUseCase,
      final AnsprechpartnerStilllegenUseCase stilllegenUseCase) {
    this.anlegenUseCase = anlegenUseCase;
    this.aendernUseCase = aendernUseCase;
    this.stilllegenUseCase = stilllegenUseCase;
  }

  /** Legt einen Ansprechpartner unter der Firma an (Kriterium 9). */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public AnsprechpartnerResponse anlegen(
      @PathVariable final long firmaId, @Valid @RequestBody final AnsprechpartnerRequest anfrage) {
    return AnsprechpartnerResponse.of(anlegenUseCase.anlegen(firmaId, anfrage.daten()));
  }

  /** Schreibt die Angaben fort (Kriterium 11). */
  @PutMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void aendern(
      @PathVariable final long firmaId,
      @PathVariable final long id,
      @Valid @RequestBody final AnsprechpartnerRequest anfrage) {
    aendernUseCase.aendern(firmaId, id, anfrage.daten());
  }

  /** Legt den Ansprechpartner still (Kriterium 15). */
  @PostMapping("/{id}/stilllegen")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void stilllegen(@PathVariable final long firmaId, @PathVariable final long id) {
    stilllegenUseCase.stilllegen(firmaId, id);
  }

  /** Nimmt den Ansprechpartner wieder in Betrieb (Kriterium 15). */
  @PostMapping("/{id}/aktivieren")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void aktivieren(@PathVariable final long firmaId, @PathVariable final long id) {
    stilllegenUseCase.aktivieren(firmaId, id);
  }
}
