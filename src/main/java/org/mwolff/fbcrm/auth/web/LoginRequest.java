package org.mwolff.fbcrm.auth.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Die Eingaben der Anmeldeseite.
 *
 * <p>Geprueft wird nur die Form, nie der Inhalt: Ob es die Adresse gibt und ob das Passwort stimmt,
 * beantwortet ausschliesslich {@code LoginUseCase} — und zwar fuer beide Faelle gleich (K5). Die
 * Laengengrenzen schuetzen Argon2 und die Datenbank vor unnoetig grossen Eingaben.
 *
 * @param email die eingegebene Adresse
 * @param password das eingegebene Passwort
 */
public record LoginRequest(
    @NotBlank @Email @Size(max = 320) String email, @NotBlank @Size(max = 200) String password) {}
