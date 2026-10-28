package org.mwolff.fbcrm.firma.web;

import org.mwolff.fbcrm.common.Laenderliste;

/**
 * Ein Land der Laenderliste, wie die Auswahl der Firmenmaske es braucht (Plan #461, E11).
 *
 * @param kuerzel das Kuerzel nach ISO 3166-1, das die Firma speichert
 * @param name der deutsche Name, den die Auswahl zeigt
 */
public record LandResponse(String kuerzel, String name) {

  static LandResponse of(final Laenderliste.Land land) {
    return new LandResponse(land.kuerzel(), land.name());
  }
}
