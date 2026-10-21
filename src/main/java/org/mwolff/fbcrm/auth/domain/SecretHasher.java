package org.mwolff.fbcrm.auth.domain;

/**
 * Port auf das Hashing eines Geheimnisses — heute des Einmalcodes der Anmeldung.
 *
 * <p>Er existiert, damit die Anwendungsschicht gegen diesen Namen uebersetzt und nicht gegen Spring
 * Security (CLAUDE-java.md §6.1). Welches Verfahren dahintersteht, entscheidet die Infrastruktur —
 * heute Argon2id.
 */
public interface SecretHasher {

  /** Der Hash eines Geheimnisses, inklusive eigenem Salt. */
  String hash(String rawSecret);

  /** Ob das vorgelegte Geheimnis zu dem gespeicherten Hash gehoert. */
  boolean matches(String rawSecret, String secretHash);
}
