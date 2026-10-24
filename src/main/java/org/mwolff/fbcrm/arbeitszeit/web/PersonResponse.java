package org.mwolff.fbcrm.arbeitszeit.web;

import org.mwolff.fbcrm.arbeitszeit.application.WaehlbarePerson;

/**
 * Eine waehlbare Person, wie die Zeiterfassung sie der Oberflaeche nennt (Plan #426, E11).
 *
 * @param id Kennung des Kontos
 * @param name aktueller Anzeigename des Kontos
 * @param aktiv ob das Konto aktiv ist; {@code false} heisst stillgelegt
 */
public record PersonResponse(long id, String name, boolean aktiv) {

  /** Dieselbe Person in der Sprache der Schnittstelle. */
  static PersonResponse of(final WaehlbarePerson person) {
    return new PersonResponse(person.id(), person.name(), person.aktiv());
  }
}
