package org.mwolff.fbcrm.auth.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.mwolff.fbcrm.auth.domain.Role;

/**
 * Die Eingaben der Maske eines Kontos — fuer das Anlegen und das Aendern dieselben (fachliche
 * Quelle #409, Kriterien 4 und 6).
 *
 * <p>Alle drei Angaben sind Pflicht. Die Feldnamen sind dieselben wie in {@link AccountResponse},
 * und die Laengengrenzen dieselben wie bei {@link SetupRequest} und der Tabelle {@code account};
 * ohne sie antwortete die Anwendung auf eine zu lange Eingabe mit einem Datenbankfehler statt mit
 * einer Meldung am Feld. Ob die Adresse frei ist, prueft erst {@code MitarbeitendeVerwaltung}.
 *
 * @param email Anmeldeadresse
 * @param displayName Anzeigename
 * @param role Rolle des Kontos
 */
public record MitarbeiterRequest(
    @NotBlank @Email @Size(max = 320) String email,
    @NotBlank @Size(max = 200) String displayName,
    @NotNull Role role) {}
