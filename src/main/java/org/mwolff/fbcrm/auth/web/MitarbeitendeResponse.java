package org.mwolff.fbcrm.auth.web;

import java.util.List;
import org.mwolff.fbcrm.auth.domain.Account;

/**
 * Die Liste der Konten, aktiv und stillgelegt, nach Anzeigenamen sortiert (fachliche Quelle #409,
 * Kriterium 3).
 *
 * <p>Ein Objekt statt einer nackten Liste, wie bei den uebrigen Uebersichten: So laesst sich die
 * Antwort spaeter erweitern, ohne die Form zu brechen.
 *
 * @param mitarbeitende die Konten in der Reihenfolge des Bestands
 */
public record MitarbeitendeResponse(List<MitarbeiterResponse> mitarbeitende) {

  /** Die Sicht der Oberflaeche auf die Liste. */
  static MitarbeitendeResponse of(final List<Account> konten) {
    return new MitarbeitendeResponse(konten.stream().map(MitarbeiterResponse::of).toList());
  }
}
