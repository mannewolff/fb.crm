package org.mwolff.fbcrm.vorgang.web;

import org.mwolff.fbcrm.vorgang.application.VorgaengeDerFirmaUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Die Vorgaenge einer Firma (Kriterium 12).
 *
 * <p>Ein eigener Weg unter dem Pfad der Firma, gehalten vom Modul {@code vorgang} (E2): Eingebettet
 * in {@code FirmaResponse} — wie die Ansprechpartner — waere {@code firma} von {@code vorgang}
 * abhaengig, und weil {@code vorgang} die Firma liest, entstuende ein Paketzyklus. Die
 * Detailansicht der Firma holt die Liste deshalb zusaetzlich zu ihrer eigenen Antwort.
 *
 * <p>Eine eigene Klasse und keine Methode im {@link VorgangController}: Dessen Pfadpraefix steht
 * auf {@code /api/vorgaenge}, und dieser Weg liegt woanders.
 */
@RestController
public class FirmaVorgaengeController {

  private final VorgaengeDerFirmaUseCase vorgaengeUseCase;

  public FirmaVorgaengeController(final VorgaengeDerFirmaUseCase vorgaengeUseCase) {
    this.vorgaengeUseCase = vorgaengeUseCase;
  }

  /** Die offenen und die abgeschlossenen Vorgaenge einer Firma, getrennt (Kriterium 12). */
  @GetMapping("/api/firmen/{firmaId}/vorgaenge")
  public VorgaengeDerFirmaResponse vorgaenge(@PathVariable final long firmaId) {
    return VorgaengeDerFirmaResponse.of(vorgaengeUseCase.vorgaenge(firmaId));
  }
}
