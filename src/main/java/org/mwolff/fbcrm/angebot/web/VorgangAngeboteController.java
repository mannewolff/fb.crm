package org.mwolff.fbcrm.angebot.web;

import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.angebot.application.AngebotAnlegenUseCase;
import org.mwolff.fbcrm.angebot.application.AngeboteDesVorgangsUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Die Wege am Vorgang: seine Angebotsliste und das Anlegen eines Entwurfs (Kriterien 2, 8, 20).
 *
 * <p>Eine eigene Klasse und keine Methode im {@link AngebotController}: Dessen Pfadpraefix steht
 * auf {@code /api/angebote}, und diese beiden Wege liegen unter dem Vorgang — dasselbe Muster wie
 * {@code FirmaVorgaengeController}. Gehalten werden sie vom Modul {@code angebot}, damit {@code
 * vorgang} nichts vom Angebot wissen muss; die Detailansicht des Vorgangs holt die Liste
 * zusaetzlich zu ihrer eigenen Antwort.
 *
 * <p>Der Controller entscheidet nichts: Er nimmt die Eingaben, reicht sie in der Sprache der
 * Anwendungsschicht weiter und uebersetzt das Ergebnis in Statuscode und Antwortrumpf
 * (CLAUDE-java.md §6.3).
 *
 * <p><b>Warum das Anlegen mit einem Rumpf antwortet.</b> Kennung, Angebotsdatum, Gueltigkeit und
 * die vorbelegten Zahlungsbedingungen entstehen erst im Anwendungsfall (Kriterium 3); die Maske
 * soll sie ohne zweiten Aufruf zeigen koennen.
 */
@RestController
@RequestMapping("/api/vorgaenge/{vorgangId}/angebote")
public class VorgangAngeboteController {

  private final AngeboteDesVorgangsUseCase listeUseCase;
  private final AngebotAnlegenUseCase anlegenUseCase;

  public VorgangAngeboteController(
      final AngeboteDesVorgangsUseCase listeUseCase, final AngebotAnlegenUseCase anlegenUseCase) {
    this.listeUseCase = listeUseCase;
    this.anlegenUseCase = anlegenUseCase;
  }

  /** Die Angebote des Vorgangs, Entwuerfe zuerst (Kriterium 20). */
  @GetMapping
  public VorgangAngeboteResponse angebote(@PathVariable final long vorgangId) {
    return VorgangAngeboteResponse.of(listeUseCase.angebote(vorgangId));
  }

  /**
   * Legt am Vorgang einen Angebotsentwurf an (Kriterien 2, 3, 8).
   *
   * @param vorgangId Kennung des Vorgangs
   * @param anfrage der Rumpf mit der optionalen Vorlage; er darf ganz fehlen
   */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public AngebotResponse anlegen(
      @PathVariable final long vorgangId,
      @Valid @RequestBody(required = false) final @Nullable AngebotAnlegenRequest anfrage) {
    return AngebotResponse.of(
        anlegenUseCase.anlegen(vorgangId, anfrage == null ? null : anfrage.vorlageAngebotId()));
  }
}
