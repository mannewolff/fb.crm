package org.mwolff.fbcrm.auftrag.web;

import jakarta.validation.Valid;
import org.mwolff.fbcrm.auftrag.application.AuftragLesenUseCase;
import org.mwolff.fbcrm.auftrag.application.AuftragLoeschenUseCase;
import org.mwolff.fbcrm.auftrag.application.AuftragPflegenUseCase;
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
 * Die Wege am einzelnen Auftrag: lesen, pflegen, loeschen (Kriterien 3 bis 7, 15).
 *
 * <p><b>Ein Schreibweg und keine drei</b> (Plan E8): {@code PUT} traegt alle vier aenderbaren
 * Angaben, den Status eingeschlossen. Eigene Wege je Zielzustand — {@code /in-arbeit}, {@code
 * /abschliessen} — waeren das Muster von „annehmen" und „ablehnen" am Angebot; dort ist der
 * Uebergang ein Lebenszyklusschritt mit eigenem Verb, hier nennt Kriterium 7 den Status in einem
 * Atemzug mit den drei anderen Feldern und laesst ihn in jede Richtung frei setzen (F6).
 *
 * <p><b>Warum {@code PUT} mit einem Rumpf antwortet und {@code DELETE} ohne.</b> Nach dem Pflegen
 * soll die Maske den neuen Stand ohne zweiten Aufruf zeigen; nach dem Loeschen gibt es nichts mehr
 * zurueckzugeben, deshalb 204. Die Rueckfrage vor dem Loeschen sitzt in der Oberflaeche (Kriterium
 * 15).
 *
 * <p>Der Controller entscheidet nichts (CLAUDE-java.md §6.3). Insbesondere prueft er den
 * Abschlussstand des Vorgangs nicht: Die Sperre aus Kriterium 11 reicht nur bis zum Anlegen (Plan
 * E14) — dass dieser Klasse der Vorgang gar nicht bekannt ist, ist die einfachste Form dieser
 * Zusage.
 *
 * <p>Der siebte Weg der Schnittstelle, der Auftragsbestand, kommt mit dem Paket, das ihn braucht.
 */
@RestController
@RequestMapping("/api/auftraege/{id}")
public class AuftragController {

  private final AuftragLesenUseCase lesenUseCase;
  private final AuftragPflegenUseCase pflegenUseCase;
  private final AuftragLoeschenUseCase loeschenUseCase;

  public AuftragController(
      final AuftragLesenUseCase lesenUseCase,
      final AuftragPflegenUseCase pflegenUseCase,
      final AuftragLoeschenUseCase loeschenUseCase) {
    this.lesenUseCase = lesenUseCase;
    this.pflegenUseCase = pflegenUseCase;
    this.loeschenUseCase = loeschenUseCase;
  }

  /** Der Auftrag samt seinen Positionen und der Nummer seines Quell-Angebots. */
  @GetMapping
  public AuftragResponse lesen(@PathVariable final long id) {
    return AuftragResponse.of(lesenUseCase.lese(id));
  }

  /**
   * Schreibt die vier aenderbaren Angaben fort und liefert den neuen Stand (Kriterium 7, Plan E8).
   *
   * @param id Kennung des Auftrags
   * @param anfrage die vier Angaben; die Positionen kommen darin nicht vor (F3)
   */
  @PutMapping
  public AuftragResponse pflegen(
      @PathVariable final long id, @Valid @RequestBody final AuftragPflegeRequest anfrage) {
    return AuftragResponse.of(pflegenUseCase.pflege(id, anfrage.daten()));
  }

  /** Loescht den Auftrag samt seinen Positionen (Kriterium 15). */
  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void loeschen(@PathVariable final long id) {
    loeschenUseCase.loesche(id);
  }
}
