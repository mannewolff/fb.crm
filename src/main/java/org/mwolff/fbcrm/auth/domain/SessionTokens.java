package org.mwolff.fbcrm.auth.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * Port auf das Ausstellen und Pruefen von Session-Tokens.
 *
 * <p>Er existiert aus demselben Grund wie {@link PasswordHasher}: Anwendungsschicht und
 * Weboberflaeche uebersetzen gegen diesen Namen, nicht gegen das Format (CLAUDE-java.md §6.1).
 * Welches Format dahintersteht, entscheidet die Infrastruktur — heute {@code
 * base64url(payload).base64url(hmacSha256)}.
 */
public interface SessionTokens {

  /**
   * Ein signiertes Token fuer eine Sitzung.
   *
   * @param accountId Konto, dem die Sitzung gehoert
   * @param sessionGeneration Generation des Kontos zum Zeitpunkt der Ausstellung
   * @param issuedAt Ausstellungszeitpunkt
   * @param ttl Laufzeit ab Ausstellung
   */
  String encode(long accountId, long sessionGeneration, Instant issuedAt, Duration ttl);

  /**
   * Prueft ein vorgelegtes Token.
   *
   * @return einer der vier Faelle aus {@link SessionTokenDecoding} — nie {@code null}
   */
  SessionTokenDecoding decode(String token);
}
