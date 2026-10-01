package org.mwolff.fbcrm.rechnung.web;

import jakarta.validation.Valid;
import org.mwolff.fbcrm.rechnung.application.RechnungAendernUseCase;
import org.mwolff.fbcrm.rechnung.application.RechnungLesenUseCase;
import org.mwolff.fbcrm.rechnung.application.RechnungLoeschenUseCase;
import org.mwolff.fbcrm.rechnung.application.RechnungStellenUseCase;
import org.springframework.http.HttpStatus;
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
 * Die Wege an der einzelnen Rechnung: lesen, aendern, loeschen, stellen (Plan #169, E7, E11).
 *
 * <p>Der Controller entscheidet nichts (CLAUDE-java.md §6.3). Was der Zustand zulaesst, entscheidet
 * die Domaene: Eine gestellte Rechnung laesst sich weder aendern noch loeschen noch ein zweites Mal
 * stellen, und alles drei ist 409 ({@code RechnungszustandPasstNicht}).
 *
 * <p><b>Warum {@code PUT} mit einem Rumpf antwortet.</b> Nach dem Aendern haben sich die
 * gerechneten Werte geaendert — Netto, Steuer, Brutto und je Zeile offen und Ueberschreitung —, und
 * die Maske soll sie ohne zweiten Aufruf zeigen. Gelesen wird dafuer derselbe Weg wie beim {@code
 * GET}: Zwei Abbildungen auf dieselbe Ansicht liefen auseinander.
 */
@RestController
@RequestMapping("/api/rechnungen/{id}")
public class RechnungController {

  private final RechnungLesenUseCase lesenUseCase;
  private final RechnungAendernUseCase aendernUseCase;
  private final RechnungLoeschenUseCase loeschenUseCase;
  private final RechnungStellenUseCase stellenUseCase;

  public RechnungController(
      final RechnungLesenUseCase lesenUseCase,
      final RechnungAendernUseCase aendernUseCase,
      final RechnungLoeschenUseCase loeschenUseCase,
      final RechnungStellenUseCase stellenUseCase) {
    this.lesenUseCase = lesenUseCase;
    this.aendernUseCase = aendernUseCase;
    this.loeschenUseCase = loeschenUseCase;
    this.stellenUseCase = stellenUseCase;
  }

  /** Die Rechnung samt den Zeilen ihrer Maske. */
  @GetMapping
  public RechnungResponse lesen(@PathVariable final long id) {
    return RechnungResponse.of(lesenUseCase.lese(id));
  }

  /** Aendert den Entwurf als Ganzes und liefert seinen neuen Stand (Kriterien 4, 5, 10). */
  @PutMapping
  public RechnungResponse aendern(
      @PathVariable final long id, @Valid @RequestBody final RechnungRequest anfrage) {
    aendernUseCase.aendere(id, anfrage.daten());
    return RechnungResponse.of(lesenUseCase.lese(id));
  }

  /**
   * Stellt den Entwurf und liefert die gestellte Rechnung (Kriterien 13 bis 16, 18 und 27).
   *
   * <p>Gelesen wird dafuer derselbe Weg wie beim {@code GET}, damit die gestellte Rechnung nach dem
   * Stellen genauso aussieht wie nach dem Neuladen. 200 und nicht 201: Es entsteht keine neue
   * Ressource, die vorhandene wechselt ihren Zustand.
   */
  @PostMapping("/stellen")
  public RechnungResponse stellen(@PathVariable final long id) {
    stellenUseCase.stelle(id);
    return RechnungResponse.of(lesenUseCase.lese(id));
  }

  /** Loescht den Entwurf (Kriterium 12). */
  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void loeschen(@PathVariable final long id) {
    loeschenUseCase.loesche(id);
  }
}
