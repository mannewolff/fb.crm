package org.mwolff.fbcrm.arbeitszeit.application;

import org.mwolff.fbcrm.auth.domain.Account;

/**
 * Eine Person, unter der die Ansicht „Arbeitszeit" waehlen darf — im Umschalter und im
 * Erfassungsdialog (Plan #426, E11).
 *
 * <p>Der Name ist der aktuelle Anzeigename des Kontos und keine Kopie: Nach einer Umbenennung zeigt
 * die Auswahl den neuen Namen (fachliche Quelle #410, Kriterium 9).
 *
 * @param id Kennung des Kontos
 * @param name aktueller Anzeigename des Kontos
 * @param aktiv ob das Konto aktiv ist; {@code false} heisst stillgelegt
 */
public record WaehlbarePerson(long id, String name, boolean aktiv) {

  /** Die Person zu einem Konto. */
  static WaehlbarePerson aus(final Account konto) {
    return new WaehlbarePerson(konto.requireId(), konto.displayName(), konto.aktiv());
  }
}
