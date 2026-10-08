package org.mwolff.fbcrm.rechnung.web;

import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URI;
import org.mwolff.fbcrm.common.web.Anlagekopf;
import org.mwolff.fbcrm.rechnung.application.NachtragAendernUseCase;
import org.mwolff.fbcrm.rechnung.application.NachtragAnlegenUseCase;
import org.mwolff.fbcrm.rechnung.application.NachtragDokumentUseCase;
import org.mwolff.fbcrm.rechnung.application.NachtragLesenUseCase;
import org.mwolff.fbcrm.rechnung.application.NachtragLoeschenUseCase;
import org.mwolff.fbcrm.rechnung.application.NachtragZustandSetzenUseCase;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Die Wege der nachgetragenen Rechnung (#254; Plan #259, E11, E17, E21, E24, E26, E27).
 *
 * <p><b>Ein eigener Weg</b> neben {@code /api/rechnungen} (E21): Die Kennungsraeume der beiden
 * Rechnungsarten sind getrennt, und ein Pfad, der je nach Art etwas anderes bedeutete, waere eine
 * Verwechslung mit Datenbezug.
 *
 * <p><b>Angaben und Original gehen in zwei Aufrufen</b> (E11): erst Anlegen oder Aendern als JSON,
 * dann das PDF als {@code multipart} auf {@code …/dokument}, mit {@code POST} wie im einzigen
 * Multipart-Weg des Bestands, {@code AngebotAnlagenController} (E27). Ersetzen und Entfernen sind
 * damit ohnehin eigene Wege (Kriterium 10).
 *
 * <p>Der Controller entscheidet nichts (CLAUDE-java.md §6.3). Jeder Weg, der einen Stand
 * zurueckgibt, liest ihn danach ueber {@link NachtragLesenUseCase} — dieselbe Abbildung wie beim
 * {@code GET}, damit die Maske nach dem Speichern dasselbe zeigt wie nach dem Neuladen.
 *
 * <p>Keine Aenderung an {@code SecurityConfig}: Die Wege fallen unter das bestehende {@code
 * /api/**} fuer angemeldete Benutzer.
 */
@RestController
@RequestMapping(NachgetrageneRechnungController.PFAD)
public class NachgetrageneRechnungController {

  /** Der Wurzelpfad der nachgetragenen Rechnungen; auch der Anfang jeder {@code Location}. */
  static final String PFAD = "/api/nachgetragene-rechnungen";

  private final NachtragAnlegenUseCase anlegenUseCase;
  private final NachtragLesenUseCase lesenUseCase;
  private final NachtragAendernUseCase aendernUseCase;
  private final NachtragLoeschenUseCase loeschenUseCase;
  private final NachtragZustandSetzenUseCase zustandUseCase;
  private final NachtragDokumentUseCase dokumentUseCase;

  public NachgetrageneRechnungController(
      final NachtragAnlegenUseCase anlegenUseCase,
      final NachtragLesenUseCase lesenUseCase,
      final NachtragAendernUseCase aendernUseCase,
      final NachtragLoeschenUseCase loeschenUseCase,
      final NachtragZustandSetzenUseCase zustandUseCase,
      final NachtragDokumentUseCase dokumentUseCase) {
    this.anlegenUseCase = anlegenUseCase;
    this.lesenUseCase = lesenUseCase;
    this.aendernUseCase = aendernUseCase;
    this.loeschenUseCase = loeschenUseCase;
    this.zustandUseCase = zustandUseCase;
    this.dokumentUseCase = dokumentUseCase;
  }

  /**
   * Traegt eine Rechnung nach (Kriterium 2) — 201 mit dem Ort der neuen Ressource.
   *
   * <p>Die {@code Location} ist pfadrelativ: Die Anwendung kennt nur eine Herkunft, und der Host
   * davor gehoert dem Reverse-Proxy.
   */
  @PostMapping
  public ResponseEntity<NachtragResponse> anlegen(
      @Valid @RequestBody final NachtragRequest anfrage) {
    final long id = anlegenUseCase.anlege(anfrage.daten()).requireId();
    return ResponseEntity.created(URI.create(PFAD + "/" + id)).body(lies(id));
  }

  /** Die nachgetragene Rechnung samt dem heutigen Namen ihrer Firma. */
  @GetMapping("/{id}")
  public NachtragResponse lesen(@PathVariable final long id) {
    return lies(id);
  }

  /** Aendert die Eckdaten in jedem Zustand (Kriterium 10) und liefert den neuen Stand. */
  @PutMapping("/{id}")
  public NachtragResponse aendern(
      @PathVariable final long id, @Valid @RequestBody final NachtragRequest anfrage) {
    aendernUseCase.aendere(id, anfrage.daten());
    return lies(id);
  }

  /** Loescht die Rechnung samt ihrem Original (Kriterium 10). */
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void loeschen(@PathVariable final long id) {
    loeschenUseCase.loesche(id);
  }

  /**
   * Stellt den Zustand um, mit dem Rumpf der gestellten Rechnung (E24) — geteilt werden Regel und
   * Form, nicht der Weg.
   */
  @PutMapping("/{id}/zustand")
  public NachtragResponse zustand(
      @PathVariable final long id, @Valid @RequestBody final RechnungZustandRequest anfrage) {
    zustandUseCase.setze(id, anfrage.zustand());
    return lies(id);
  }

  /**
   * Gibt das hinterlegte Original heraus (Kriterium 7) — ohne Original 404 (E17).
   *
   * <p>Dieselben Kopfzeilen wie beim Dokument der gestellten Rechnung, und derselbe Name {@code
   * Rechnung-<nummer>.pdf} (E26): Der Name des hochgeladenen Originals wird nicht gespeichert.
   * {@code X-Content-Type-Options: nosniff} und {@code X-Frame-Options: DENY} kommen aus den
   * Spring-Security-Vorgaben; {@code NachgetrageneRechnungIT} prueft, dass sie auch hier ankommen.
   */
  @GetMapping("/{id}/dokument")
  public ResponseEntity<byte[]> dokument(@PathVariable final long id) {
    final String nummer = lesenUseCase.lese(id).rechnung().nummer();
    final byte[] inhalt = dokumentUseCase.lies(id);
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            Anlagekopf.contentDisposition(Rechnungsdateiname.fuer(nummer)))
        .header(Anlagekopf.INHALTSREGEL, Anlagekopf.SANDKASTEN)
        .contentType(MediaType.APPLICATION_PDF)
        .contentLength(inhalt.length)
        .body(inhalt);
  }

  /**
   * Legt das Original ab oder ersetzt es (Kriterien 2 und 10, E27) und liefert den neuen Stand.
   *
   * <p>200 und nicht 201: Das Original ist keine eigene Ressource mit Kennung, sondern eine Angabe
   * der Rechnung. Ob die Datei ein PDF ist, entscheidet der Anwendungsfall am Inhalt; Name und
   * gemeldete Art des Teils liest dieser Controller nicht.
   *
   * @throws IOException wenn sich der Inhalt der hochgeladenen Datei nicht lesen laesst
   */
  @PostMapping("/{id}/dokument")
  public NachtragResponse dokumentAblegen(
      @PathVariable final long id, @RequestPart("datei") final MultipartFile datei)
      throws IOException {
    dokumentUseCase.lege(id, datei.getBytes());
    return lies(id);
  }

  /** Entfernt das Original; die Rechnung bleibt (Kriterium 10). */
  @DeleteMapping("/{id}/dokument")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void dokumentEntfernen(@PathVariable final long id) {
    dokumentUseCase.entferne(id);
  }

  private NachtragResponse lies(final long id) {
    return NachtragResponse.of(lesenUseCase.lese(id));
  }
}
