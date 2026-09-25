package org.mwolff.fbcrm.vorgang.web;

import jakarta.validation.Valid;
import java.io.IOException;
import org.mwolff.fbcrm.vorgang.application.AnhangInhalt;
import org.mwolff.fbcrm.vorgang.application.AnhangLesenUseCase;
import org.mwolff.fbcrm.vorgang.application.EintragAendernUseCase;
import org.mwolff.fbcrm.vorgang.application.EintragHinzufuegenUseCase;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
 *
 * <p>Dazu der Weg zurueck: {@code GET …/datei} gibt den Anhang wieder heraus (Kriterium 17). Er
 * liegt hier und nicht bei den Lesewegen des Vorgangs, weil er dieselbe Ressource adressiert wie
 * die beiden Schreibwege — ein Pfad, eine Klasse.
 */
@RestController
@RequestMapping("/api/vorgaenge/{vorgangId}/eintraege")
public class EintragController {

  private final EintragHinzufuegenUseCase hinzufuegenUseCase;
  private final EintragAendernUseCase aendernUseCase;
  private final AnhangLesenUseCase anhangLesenUseCase;

  public EintragController(
      final EintragHinzufuegenUseCase hinzufuegenUseCase,
      final EintragAendernUseCase aendernUseCase,
      final AnhangLesenUseCase anhangLesenUseCase) {
    this.hinzufuegenUseCase = hinzufuegenUseCase;
    this.aendernUseCase = aendernUseCase;
    this.anhangLesenUseCase = anhangLesenUseCase;
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

  /**
   * Gibt die Datei eines Anhangs heraus (Kriterium 17).
   *
   * <p>Vier Kopfzeilen halten die Grenze, die dieser Weg aufmacht (E11): {@code attachment} statt
   * Anzeige, {@code application/octet-stream} statt des hochgeladenen Typs, die Sandbox-Regel als
   * letzte Schranke — und {@code X-Content-Type-Options: nosniff}, das nicht hier steht, weil es
   * bereits aus den Spring-Security-Defaults auf jede Antwort geht. Zusammen sorgen sie dafuer,
   * dass ein hochgeladenes HTML oder SVG nie in der Origin der Anwendung ausgefuehrt wird.
   *
   * <p>Der Rumpf ist ein {@link InputStreamResource} und damit der Datenstrom aus dem
   * Objektspeicher: Er wird durchgereicht, nicht eingesammelt. Die Laenge steht aus der Zeile
   * daneben, damit der Konverter den Strom nicht zusaetzlich abmessen muss.
   */
  @GetMapping("/{eintragId}/datei")
  public ResponseEntity<Resource> datei(
      @PathVariable final long vorgangId, @PathVariable final long eintragId) {
    final AnhangInhalt anhang = anhangLesenUseCase.lese(vorgangId, eintragId);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, Anhangkopf.contentDisposition(anhang.dateiName()))
        .header(Anhangkopf.INHALTSREGEL, Anhangkopf.SANDKASTEN)
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .contentLength(anhang.groesse())
        .body(new InputStreamResource(anhang.inhalt()));
  }
}
