package org.mwolff.fbcrm.firma.web;

import java.util.List;
import org.mwolff.fbcrm.common.Laenderliste;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Die Laenderliste fuer die Oberflaeche (Plan #461, E11, E21).
 *
 * <p>Die Liste ist dieselbe, gegen die {@code FirmaRequest} das Land prueft: eine einzige Quelle,
 * damit Auswahl und Pruefung nicht auseinanderlaufen. Der Weg liegt bei der Firma, weil sie ihr
 * einziger Nutzer ist. Lesen darf ihn jede angemeldete Rolle — die Liste ist oeffentliches Wissen
 * ohne Personenbezug (E21); einen schreibenden Weg gibt es nicht.
 */
@RestController
public class LaenderController {

  /** Alle Laender, sortiert nach dem deutschen Namen. */
  @GetMapping("/api/laender")
  public List<LandResponse> alle() {
    return Laenderliste.alle().stream().map(LandResponse::of).toList();
  }
}
