package org.mwolff.fbcrm.angebot.web;

import jakarta.validation.Valid;
import org.mwolff.fbcrm.angebot.application.AngebotEntwurfAendernUseCase;
import org.mwolff.fbcrm.angebot.application.AngebotLesenUseCase;
import org.mwolff.fbcrm.angebot.application.AngebotVerwerfenUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Die Wege am einzelnen Angebot: lesen, fortschreiben, verwerfen (Kriterien 5, 6, 7, 18).
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
 */
@RestController
@RequestMapping("/api/angebote/{id}")
public class AngebotController {

  private final AngebotLesenUseCase lesenUseCase;
  private final AngebotEntwurfAendernUseCase aendernUseCase;
  private final AngebotVerwerfenUseCase verwerfenUseCase;

  public AngebotController(
      final AngebotLesenUseCase lesenUseCase,
      final AngebotEntwurfAendernUseCase aendernUseCase,
      final AngebotVerwerfenUseCase verwerfenUseCase) {
    this.lesenUseCase = lesenUseCase;
    this.aendernUseCase = aendernUseCase;
    this.verwerfenUseCase = verwerfenUseCase;
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
}
