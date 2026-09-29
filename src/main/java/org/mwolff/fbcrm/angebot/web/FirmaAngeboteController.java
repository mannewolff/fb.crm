package org.mwolff.fbcrm.angebot.web;

import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.angebot.application.AngebotAnlegenUseCase;
import org.mwolff.fbcrm.angebot.application.AngeboteDerFirmaUseCase;
import org.mwolff.fbcrm.angebot.application.KundenangabenUseCase;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Die Wege an der Firma: ihre Angebotsliste und das Anlegen eines Angebots (Issues #126, #127).
 *
 * <p>Eine eigene Klasse und keine Methode im {@link AngebotController}: Dessen Pfadpraefix steht
 * auf {@code /api/angebote}, und diese beiden Wege liegen unter der Firma. Gehalten werden sie vom
 * Modul {@code angebot}, damit {@code firma} nichts vom Angebot wissen muss; die Detailansicht der
 * Firma holt die Liste zusaetzlich zu ihrer eigenen Antwort.
 *
 * <p>Der Controller entscheidet nichts: Er nimmt die Eingaben, reicht sie in der Sprache der
 * Anwendungsschicht weiter und uebersetzt das Ergebnis in Statuscode und Antwortrumpf
 * (CLAUDE-java.md §6.3).
 *
 * <p><b>Warum das Anlegen mit einem Rumpf antwortet.</b> Kennung, Angebotsdatum und Status
 * entstehen erst im Anwendungsfall; die Maske soll sie ohne zweiten Aufruf zeigen koennen.
 */
@RestController
@RequestMapping("/api/firmen/{firmaId}/angebote")
public class FirmaAngeboteController {

  private final AngeboteDerFirmaUseCase listeUseCase;
  private final AngebotAnlegenUseCase anlegenUseCase;
  private final KundenangabenUseCase kundenUseCase;

  public FirmaAngeboteController(
      final AngeboteDerFirmaUseCase listeUseCase,
      final AngebotAnlegenUseCase anlegenUseCase,
      final KundenangabenUseCase kundenUseCase) {
    this.listeUseCase = listeUseCase;
    this.anlegenUseCase = anlegenUseCase;
    this.kundenUseCase = kundenUseCase;
  }

  /** Die Angebote der Firma, neueste zuerst (Kriterium 7). */
  @GetMapping
  public FirmaAngeboteResponse angebote(@PathVariable final long firmaId) {
    return FirmaAngeboteResponse.of(listeUseCase.angebote(firmaId));
  }

  /**
   * Legt an die Firma ein Angebot an (Kriterium 2).
   *
   * @param firmaId Kennung der Firma
   * @param anfrage der Rumpf mit dem optionalen Ansprechpartner; er darf ganz fehlen
   */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public AngebotResponse anlegen(
      @PathVariable final long firmaId,
      @Valid @RequestBody(required = false) final @Nullable AngebotAnlegenRequest anfrage) {
    final Angebot angebot =
        anlegenUseCase.anlegen(firmaId, anfrage == null ? null : anfrage.ansprechpartnerId());
    return AngebotResponse.of(angebot, kundenUseCase.zu(angebot));
  }
}
