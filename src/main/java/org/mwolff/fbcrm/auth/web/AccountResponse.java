package org.mwolff.fbcrm.auth.web;

import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.Role;

/**
 * Das angemeldete Konto, so wie die Oberflaeche es braucht.
 *
 * <p>Vier Felder, mehr nicht: Aus dem Anzeigenamen baut die Oberflaeche das Kuerzel im Nutzer-Mal
 * (K13, E12). Die Rolle braucht sie, um nicht erlaubte Aktionen gar nicht erst zu zeigen (Plan
 * #414, A8); durchgesetzt wird die Rolle trotzdem allein von der Zugangsregel der API. Die
 * Sitzungs-Generation geht niemanden etwas an und steht deshalb nicht drin.
 *
 * @param id technische Id des Kontos
 * @param displayName Anzeigename
 * @param email Anmeldeadresse
 * @param role Rolle des Kontos, so wie es sie jetzt traegt
 */
public record AccountResponse(long id, String displayName, String email, Role role) {

  /** Die Sicht der Oberflaeche auf ein Konto. */
  static AccountResponse of(final Account konto) {
    return new AccountResponse(konto.requireId(), konto.displayName(), konto.email(), konto.role());
  }
}
