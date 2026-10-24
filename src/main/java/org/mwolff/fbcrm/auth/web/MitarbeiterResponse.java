package org.mwolff.fbcrm.auth.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.auth.application.MitarbeitendeVerwaltung;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.Role;

/**
 * Ein Konto in der Ansicht „Mitarbeitende" (fachliche Quelle #409, Kriterium 3).
 *
 * <p>Die Felder wie in {@link AccountResponse}, dazu der Zustand. Die Sitzungs-Generation steht
 * auch hier nicht drin.
 *
 * <p>{@code version} ist der Stand, den die Oberflaeche beim Aendern, Stilllegen und
 * Wiederaktivieren als {@code If-Match} vorlegt (Plan #415, E19).
 *
 * <p>{@code begruessungMailVersandt} traegt nur die Antwort auf das Anlegen: Ging keine Mail
 * hinaus, weil der Versand ausgeschaltet ist, sagt die Maske es (Plan #414, A13). In der Liste und
 * nach einer Aenderung fehlt das Feld ganz, statt mit {@code null} etwas anzudeuten.
 *
 * @param id technische Id des Kontos
 * @param displayName Anzeigename
 * @param email Anmeldeadresse
 * @param role Rolle des Kontos
 * @param aktiv ob sich das Konto anmelden darf; {@code false} heisst stillgelegt
 * @param version der Stand des Kontos aus Sicht der Kontenverwaltung
 * @param begruessungMailVersandt beim Anlegen, ob eine Begruessung hinausging; sonst {@code null}
 */
public record MitarbeiterResponse(
    long id,
    String displayName,
    String email,
    Role role,
    boolean aktiv,
    long version,
    @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable Boolean begruessungMailVersandt) {

  /** Die Sicht der Oberflaeche auf ein Konto. */
  static MitarbeiterResponse of(final Account konto) {
    return baue(konto, null);
  }

  /** Die Antwort auf das Anlegen, mit der Auskunft ueber die Begruessung. */
  static MitarbeiterResponse angelegt(final MitarbeitendeVerwaltung.Angelegt ergebnis) {
    return baue(ergebnis.konto(), ergebnis.begruessungMailVersandt());
  }

  private static MitarbeiterResponse baue(
      final Account konto, final @Nullable Boolean begruessungMailVersandt) {
    return new MitarbeiterResponse(
        konto.requireId(),
        konto.displayName(),
        konto.email(),
        konto.role(),
        konto.aktiv(),
        konto.version(),
        begruessungMailVersandt);
  }
}
