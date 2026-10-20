package org.mwolff.fbcrm.auth.domain;

/**
 * Port auf das Hashing eines Geheimnisses: Einmalcode der Anmeldung, bis zur Umstellung Passwort.
 *
 * <p>Er existiert, damit die Anwendungsschicht gegen diesen Namen uebersetzt und nicht gegen Spring
 * Security (CLAUDE-java.md §6.1). Welches Verfahren dahintersteht, entscheidet die Infrastruktur —
 * heute Argon2id.
 */
public interface SecretHasher {

  /** Der Hash eines Geheimnisses, inklusive eigenem Salt. */
  String hash(String rawPassword);

  /** Ob das vorgelegte Geheimnis zu dem gespeicherten Hash gehoert. */
  boolean matches(String rawPassword, String passwordHash);
}
