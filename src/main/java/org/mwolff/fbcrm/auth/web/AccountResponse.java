package org.mwolff.fbcrm.auth.web;

import org.mwolff.fbcrm.auth.domain.Account;

/**
 * Das angemeldete Konto, so wie die Oberflaeche es braucht.
 *
 * <p>Drei Felder, mehr nicht: Aus dem Anzeigenamen baut die Oberflaeche das Kuerzel im Nutzer-Mal
 * (K13, E12). Der Passwort-Hash, die Rolle und die Sitzungs-Generation gehen niemanden etwas an und
 * stehen deshalb nicht drin.
 *
 * @param id technische Id des Kontos
 * @param displayName Anzeigename
 * @param email Anmeldeadresse
 */
public record AccountResponse(long id, String displayName, String email) {

  /** Die Sicht der Oberflaeche auf ein Konto. */
  static AccountResponse of(final Account konto) {
    return new AccountResponse(konto.requireId(), konto.displayName(), konto.email());
  }
}
