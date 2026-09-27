package org.mwolff.fbcrm.angebot.web;

import jakarta.validation.Valid;
import org.mwolff.fbcrm.angebot.application.AngebotEntwurfAendernUseCase;
import org.mwolff.fbcrm.angebot.application.AngebotLesenUseCase;
import org.mwolff.fbcrm.angebot.application.AngebotPdfLesenUseCase;
import org.mwolff.fbcrm.angebot.application.AngebotVersendenUseCase;
import org.mwolff.fbcrm.angebot.application.AngebotVerwerfenUseCase;
import org.mwolff.fbcrm.angebot.application.Belegdokument;
import org.springframework.http.ContentDisposition;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Die Wege am einzelnen Angebot: lesen, fortschreiben, verwerfen, versenden, Beleg oeffnen
 * (Kriterien 5, 6, 7, 10, 14, 18).
 *
 * <p>Der Controller entscheidet nichts (CLAUDE-java.md §6.3). Insbesondere prueft er den
 * Abschlussstand des Vorgangs nicht: Kriterium 9 sperrt das Anlegen und das Versenden, nicht die
 * Pflege eines Entwurfs (E13) — dass dieser Klasse der Vorgang gar nicht bekannt ist, ist die
 * einfachste Form dieser Zusage.
 *
 * <p><b>Warum {@code PUT} mit einem Rumpf antwortet und {@code DELETE} ohne.</b> Nach dem
 * Fortschreiben haben sich die gerechneten Werte geaendert — Summe und Positionsbetraege —, und die
 * Maske soll sie ohne zweiten Aufruf zeigen; nach dem Verwerfen gibt es nichts mehr zurueckzugeben,
 * deshalb 204 (E19). Die Rueckfrage vor dem Loeschen sitzt in der Oberflaeche.
 *
 * <p><b>Warum das Versenden ein {@code POST} ohne Rumpf ist.</b> Der Zustandsuebergang steht im
 * Pfad und ist damit aus dem Zugriffsprotokoll lesbar; einen Rumpf gibt es nicht, weil alles, was
 * in das festgeschriebene Dokument geht, schon im Bestand steht (Kriterium 10). Die Antwort traegt
 * das festgeschriebene Angebot, damit die Maske Nummer und Stand ohne zweiten Aufruf zeigt.
 *
 * <p><b>Warum der Beleg {@code inline} hinausgeht</b> (E17): Kriterium 14 sagt „oeffnen", nicht
 * „herunterladen". Der Dateiname ist die Angebotsnummer und damit reines ASCII aus dem Nummernkreis
 * — anders als beim Anhang eines Vorgangs, dessen Name vom Anwender kommt und deshalb die
 * RFC-6266-Doppelform aus {@code vorgang.web.Anhangkopf} braucht.
 */
@RestController
@RequestMapping("/api/angebote/{id}")
public class AngebotController {

  private final AngebotLesenUseCase lesenUseCase;
  private final AngebotEntwurfAendernUseCase aendernUseCase;
  private final AngebotVerwerfenUseCase verwerfenUseCase;
  private final AngebotVersendenUseCase versendenUseCase;
  private final AngebotPdfLesenUseCase pdfUseCase;

  public AngebotController(
      final AngebotLesenUseCase lesenUseCase,
      final AngebotEntwurfAendernUseCase aendernUseCase,
      final AngebotVerwerfenUseCase verwerfenUseCase,
      final AngebotVersendenUseCase versendenUseCase,
      final AngebotPdfLesenUseCase pdfUseCase) {
    this.lesenUseCase = lesenUseCase;
    this.aendernUseCase = aendernUseCase;
    this.verwerfenUseCase = verwerfenUseCase;
    this.versendenUseCase = versendenUseCase;
    this.pdfUseCase = pdfUseCase;
  }

  /** Das Angebot samt seinen Positionen (Kriterien 5, 18). */
  @GetMapping
  public AngebotResponse lesen(@PathVariable final long id) {
    return AngebotResponse.of(lesenUseCase.lese(id));
  }

  /** Schreibt den Entwurf als Ganzes fort und liefert seinen neuen Stand (Kriterium 6, E8). */
  @PutMapping
  public AngebotResponse aendern(
      @PathVariable final long id, @Valid @RequestBody final AngebotEntwurfRequest anfrage) {
    return AngebotResponse.of(aendernUseCase.aendere(id, anfrage.daten()));
  }

  /** Verwirft den Entwurf samt seinen Positionen (Kriterium 7, E19). */
  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void verwerfen(@PathVariable final long id) {
    verwerfenUseCase.verwirf(id);
  }

  /** Macht aus dem Entwurf ein festes Dokument (Kriterien 10 bis 16). */
  @PostMapping("/versenden")
  public AngebotResponse versenden(@PathVariable final long id) {
    return AngebotResponse.of(versendenUseCase.versende(id));
  }

  /** Der beim Versenden erzeugte Beleg, zum Ansehen im Browser (Kriterium 14, E17). */
  @GetMapping("/pdf")
  public ResponseEntity<byte[]> pdf(@PathVariable final long id) {
    final Belegdokument beleg = pdfUseCase.pdf(id);
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.inline().filename(beleg.dateiname()).build().toString())
        .contentType(MediaType.APPLICATION_PDF)
        .body(beleg.inhalt());
  }
}
