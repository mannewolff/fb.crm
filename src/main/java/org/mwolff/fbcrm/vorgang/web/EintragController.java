package org.mwolff.fbcrm.vorgang.web;

import jakarta.validation.Valid;
import java.io.IOException;
import org.mwolff.fbcrm.vorgang.application.EintragAendernUseCase;
import org.mwolff.fbcrm.vorgang.application.EintragHinzufuegenUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Die Schreibwege der Historie: einen Eintrag hinzufuegen und einen vorhandenen aendern.
 *
 * <p>Ein eigener Controller neben {@link VorgangController}, weil der Eintrag eine eigene Ressource
 * unter dem Vorgang ist — ein Pfad, eine Klasse.
 *
 * <p><b>Warum zwei Formate.</b> Das Hinzufuegen nimmt Formulardaten, weil eine Datei dabei sein
 * kann; das Aendern nimmt JSON, weil die Datei nicht austauschbar ist und damit nichts Binaeres
 * mitkommt. Beide antworten ohne Rumpf: Die Historie steht in der Detailantwort des Vorgangs, und
 * die Oberflaeche laedt sie danach neu (E25).
 */
@RestController
@RequestMapping("/api/vorgaenge/{vorgangId}/eintraege")
public class EintragController {

  private final EintragHinzufuegenUseCase hinzufuegenUseCase;
  private final EintragAendernUseCase aendernUseCase;

  public EintragController(
      final EintragHinzufuegenUseCase hinzufuegenUseCase,
      final EintragAendernUseCase aendernUseCase) {
    this.hinzufuegenUseCase = hinzufuegenUseCase;
    this.aendernUseCase = aendernUseCase;
  }

  /**
   * Fuegt der Historie einen Kommentar oder einen Anhang hinzu (Kriterien 13, 14, 18).
   *
   * @throws IOException wenn sich der Datenstrom der hochgeladenen Datei nicht oeffnen laesst
   */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public void hinzufuegen(
      @PathVariable final long vorgangId, @Valid @ModelAttribute final EintragRequest anfrage)
      throws IOException {
    hinzufuegenUseCase.hinzufuegen(vorgangId, anfrage.daten());
  }

  /** Schreibt Text und Zeitpunkt eines Eintrags fort (Kriterien 18, 19). */
  @PutMapping("/{eintragId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void aendern(
      @PathVariable final long vorgangId,
      @PathVariable final long eintragId,
      @Valid @RequestBody final EintragAenderungRequest anfrage) {
    aendernUseCase.aendern(vorgangId, eintragId, anfrage.text(), anfrage.geschehenAm());
  }
}
