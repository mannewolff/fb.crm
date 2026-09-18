package org.mwolff.fbcrm.auth.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Die Eingabe der Seite „Passwort vergessen".
 *
 * <p>Geprueft wird nur die <b>Form</b> der Adresse. Ob es zu ihr ein Konto gibt, beantwortet die
 * Antwort nicht — sie ist fuer jede formal gueltige Adresse dieselbe (K7).
 *
 * <p>Die Laengengrenze ist dieselbe wie bei {@link LoginRequest} und der Spalte {@code
 * account.email}.
 *
 * @param email die Adresse, an die der Link gehen soll
 */
public record PasswordResetRequest(@NotBlank @Email @Size(max = 320) String email) {}
