package org.mwolff.fbcrm.firma.web;

import jakarta.validation.Valid;
import org.mwolff.fbcrm.common.web.Bedingungskopf;
import org.mwolff.fbcrm.firma.application.AnsprechpartnerAendernUseCase;
import org.mwolff.fbcrm.firma.application.AnsprechpartnerAnlegenUseCase;
import org.mwolff.fbcrm.firma.application.AnsprechpartnerStilllegenUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
 * ihrer Firma (E7). Jeder Schreibweg antwortet mit dem Ansprechpartner, wie er nach dem Schreiben
 * dasteht: das Anlegen mit der neuen Kennung, die drei anderen mit der erhoehten Version.
 *
 * <p><b>Aendern, Stilllegen und Wiederaktivieren legen den Stand vor</b> (#411, Kriterium 4; Plan
 * #415, E2, E3, E14, E18): Sie lesen {@link Bedingungskopf} aus {@code If-Match} und das handelnde
 * Konto aus der Sitzung. Fehlt der Kopf, antwortet der Weg mit 428, ohne den Anwendungsfall zu
 * fragen. Das Anlegen bleibt frei (#411, Kriterium 7).
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

  /**
   * Schreibt die Angaben fort (Kriterium 11) und liefert den neuen Stand.
   *
   * <p>Der Kopf steht vor dem Rumpf: Fehlt er, weist der Weg mit 428 ab, bevor der Rumpf gelesen
   * und geprueft wird (Plan #415, E3).
   */
  @PutMapping("/{id}")
  public AnsprechpartnerResponse aendern(
      @PathVariable final long firmaId,
      @PathVariable final long id,
      final Bedingungskopf stand,
      @AuthenticationPrincipal final Long accountId,
      @Valid @RequestBody final AnsprechpartnerRequest anfrage) {
    return AnsprechpartnerResponse.of(
        aendernUseCase.aendern(firmaId, id, anfrage.daten(), stand.version(), accountId));
  }

  /** Legt den Ansprechpartner still (Kriterium 15) und liefert den neuen Stand. */
  @PostMapping("/{id}/stilllegen")
  public AnsprechpartnerResponse stilllegen(
      @PathVariable final long firmaId,
      @PathVariable final long id,
      final Bedingungskopf stand,
      @AuthenticationPrincipal final Long accountId) {
    return AnsprechpartnerResponse.of(
        stilllegenUseCase.stilllegen(firmaId, id, stand.version(), accountId));
  }

  /** Nimmt den Ansprechpartner wieder in Betrieb (Kriterium 15) und liefert den neuen Stand. */
  @PostMapping("/{id}/aktivieren")
  public AnsprechpartnerResponse aktivieren(
      @PathVariable final long firmaId,
      @PathVariable final long id,
      final Bedingungskopf stand,
      @AuthenticationPrincipal final Long accountId) {
    return AnsprechpartnerResponse.of(
        stilllegenUseCase.aktivieren(firmaId, id, stand.version(), accountId));
  }
}
