package org.mwolff.fbcrm.auth.domain;

import java.time.Instant;

/**
 * Das Ergebnis von {@link SessionTokens#decode(String)}.
 *
 * <p>Ein Summentyp statt {@code null} oder einer Exception: Ein vorgelegtes Token, das nicht gilt,
 * ist der Normalfall und keine Ausnahmelage (CLAUDE-java.md §6.2). Der Aufrufer muss jeden der vier
 * Faelle behandeln, weil der Typ versiegelt ist.
 */
public sealed interface SessionTokenDecoding {

  /**
   * Das Token traegt eine gueltige Signatur und ist nicht abgelaufen.
   *
   * <p>Ob die Sitzungs-Generation noch zum Konto passt, sagt dieser Typ <b>nicht</b> — das
   * entscheidet das Konto selbst ({@link Account#matchesGeneration(long)}).
   */
  record Valid(long accountId, long sessionGeneration, Instant issuedAt, Instant expiresAt)
      implements SessionTokenDecoding {}

  /** Signatur und Payload passen nicht zusammen — das Token wurde veraendert oder fremd erzeugt. */
  record SignatureMismatch() implements SessionTokenDecoding {}

  /** Die Signatur stimmt, die Laufzeit ist abgelaufen. */
  record Expired() implements SessionTokenDecoding {}

  /** Das Token hat nicht die Form {@code base64url(payload).base64url(signatur)}. */
  record Malformed() implements SessionTokenDecoding {}
}
