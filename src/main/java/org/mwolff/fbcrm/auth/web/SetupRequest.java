package org.mwolff.fbcrm.auth.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Die Eingaben der Einrichtungsseite.
 *
 * <p>Geprueft wird nur die Form, nie der Inhalt: Ob der Einmal-Schluessel stimmt und ob die Instanz
 * ueberhaupt noch frei ist, beantwortet ausschliesslich {@code SetupAccountUseCase} — und zwar fuer
 * beide Faelle gleich. Die Wiederholung der Adresse faengt den Vertipper ab, der den Betreiber
 * sonst aus seiner eigenen Instanz aussperrte (E6): Ohne Konto gibt es keinen Passwort-Reset, der
 * ihn zurueckholen koennte.
 *
 * <p>Die Laengengrenzen schuetzen Argon2 und die Datenbank vor unnoetig grossen Eingaben; sie sind
 * dieselben wie bei {@link LoginRequest}.
 *
 * @param email die Anmeldeadresse des ersten Kontos
 * @param emailRepeat die Wiederholung derselben Adresse
 * @param displayName der Anzeigename in der Oberflaeche
 * @param password das Passwort im Klartext; gespeichert wird nur sein Hash
 * @param bootstrapToken der Einmal-Schluessel aus {@code FBCRM_BOOTSTRAP_ADMIN_TOKEN}
 */
@EmailRepeatConstraint
public record SetupRequest(
    @NotBlank @Email @Size(max = 320) String email,
    @NotBlank @Email @Size(max = 320) String emailRepeat,
    @NotBlank @Size(max = 200) String displayName,
    @PasswordConstraint @Size(max = 200) String password,
    @NotBlank @Size(max = 512) String bootstrapToken) {}
