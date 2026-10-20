package org.mwolff.fbcrm.auth.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Die Eingaben der Einrichtungsseite, wenn der Code der Einrichtung angefordert wird.
 *
 * <p>Geprueft wird nur die Form, nie der Inhalt: Ob der Einmal-Schluessel stimmt und ob die Instanz
 * ueberhaupt noch frei ist, beantwortet ausschliesslich {@code SetupAccountUseCase}. Eine
 * Wiederholung der Adresse gibt es hier nicht: Wer sich vertippt, bekommt bei eingeschaltetem
 * Mailversand keinen Code, und es entsteht kein Konto (Kriterium 6).
 *
 * <p>Die Laengengrenzen sind dieselben wie bei {@link SetupRequest}.
 *
 * @param email die Adresse, an die der Code geht und unter der das Konto entstehen soll
 * @param displayName der Anzeigename in der Oberflaeche
 * @param bootstrapToken der Einmal-Schluessel aus {@code FBCRM_BOOTSTRAP_ADMIN_TOKEN}
 */
public record SetupCodeRequest(
    @NotBlank @Email @Size(max = 320) String email,
    @NotBlank @Size(max = 200) String displayName,
    @NotBlank @Size(max = 512) String bootstrapToken) {}
