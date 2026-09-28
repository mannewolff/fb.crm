package org.mwolff.fbcrm.auftrag.web;

import jakarta.validation.Valid;
import org.mwolff.fbcrm.auftrag.application.AuftragAnlegenUseCase;
import org.mwolff.fbcrm.auftrag.application.AuftragLesenUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Die beiden Wege am Angebot: der Auftrag dazu und das Anlegen eines neuen (Kriterien 1, 2, 6, 11).
 *
 * <p><b>Eine eigene Klasse in diesem Modul und keine Methode im Angebot-Modul</b> (Plan E3): Die
 * Angebotsansicht braucht zwei Auskuenfte ueber den Auftrag, und gehalten werden sie vom juengeren
 * Modul — dieselbe Richtung, die {@code angebot.web.VorgangAngeboteController} gegenueber {@code
 * vorgang} nimmt. Ein Rueckverweis in {@code AngebotResponse} waere die Rueckkante, die {@code
 * ArchitectureTest.modules_thenFreeOfCycles} abweist.
 *
 * <p><b>Warum der Leseweg zwei Angaben traegt.</b> Die Ansicht zeigt entweder den Auftrag oder die
 * Taste, die ihn anlegt, und die Entscheidung darueber braucht beides: {@code auftrag} und {@code
 * anlegbar}. Zwei Aufrufe waeren zwei Zeitpunkte.
 *
 * <p><b>Warum das Anlegen mit einem Rumpf antwortet.</b> Kennung, Nummer und die gerechneten Werte
 * entstehen erst im Anwendungsfall (Kriterien 3, 5); die Maske soll sie ohne zweiten Aufruf zeigen.
 *
 * <p>Der Controller entscheidet nichts (CLAUDE-java.md §6.3) — insbesondere prueft er weder den
 * Zustand des Angebots noch den Abschlussstand des Vorgangs.
 */
@RestController
@RequestMapping("/api/angebote/{angebotId}/auftrag")
public class AngebotAuftragController {

  private final AuftragLesenUseCase lesenUseCase;
  private final AuftragAnlegenUseCase anlegenUseCase;

  public AngebotAuftragController(
      final AuftragLesenUseCase lesenUseCase, final AuftragAnlegenUseCase anlegenUseCase) {
    this.lesenUseCase = lesenUseCase;
    this.anlegenUseCase = anlegenUseCase;
  }

  /** Der Auftrag zu diesem Angebot — oder {@code null} — samt der Auskunft {@code anlegbar}. */
  @GetMapping
  public AngebotAuftragResponse auftrag(@PathVariable final long angebotId) {
    return AngebotAuftragResponse.of(lesenUseCase.zuAngebot(angebotId));
  }

  /**
   * Legt aus dem Angebot einen Auftrag an (Kriterien 1, 2, 6).
   *
   * @param angebotId Kennung des Angebots
   * @param anfrage die Angaben samt der Positionswahl; ohne Position gibt es keinen Auftrag
   */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public AuftragResponse anlegen(
      @PathVariable final long angebotId, @Valid @RequestBody final AuftragAnlegenRequest anfrage) {
    return AuftragResponse.of(anlegenUseCase.anlegen(angebotId, anfrage.daten()));
  }
}
