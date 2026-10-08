package org.mwolff.fbcrm.auth.domain;

/**
 * Port auf das Passwort-Hashing.
 *
 * <p>Er existiert, damit die Anwendungsschicht gegen diesen Namen uebersetzt und nicht gegen Spring
 * Security (CLAUDE-java.md §6.1). Welches Verfahren dahintersteht, entscheidet die Infrastruktur —
 * heute Argon2id.
 */
public interface PasswordHasher {

  /** Der Hash eines Passworts, inklusive eigenem Salt. */
  String hash(String rawPassword);

  /** Ob das vorgelegte Passwort zu dem gespeicherten Hash gehoert. */
  boolean matches(String rawPassword, String passwordHash);
}
