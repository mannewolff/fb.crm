package org.mwolff.fbcrm.auth.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Die Eingaben der Seite zum Setzen eines neuen Passworts.
 *
 * <p>Das neue Passwort geht durch dieselbe {@link PasswordConstraint} wie das bei der Einrichtung —
 * K6 gilt beim Anlegen <b>und</b> beim Neusetzen, und eine zweite Regel an zweiter Stelle liefe
 * frueher oder spaeter von der ersten weg.
 *
 * <p>Die Laengengrenze des Tokens haelt eine Eingabe von der Datenbank fern, die ohnehin nie ein
 * Token sein kann: Ausgegeben werden 43 Zeichen.
 *
 * @param token das Token aus dem Link der Mail
 * @param password das neue Passwort im Klartext; gespeichert wird nur sein Hash
 */
public record PasswordResetConfirmRequest(
    @NotBlank @Size(max = 512) String token,
    @PasswordConstraint @Size(max = 200) String password) {}
