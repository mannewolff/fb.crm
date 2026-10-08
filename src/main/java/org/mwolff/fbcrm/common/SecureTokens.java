package org.mwolff.fbcrm.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Einmal-Tokens fuer E-Mail-Verifikation, Passwort-Reset und Bootstrap-Admin.
 *
 * <p>Erzeugt werden 256 Bit aus {@link SecureRandom}, ausgegeben als Base64url ohne
 * Auffuellzeichen. Gespeichert wird nie das Token selbst, sondern sein SHA-256-Hash; verglichen
 * wird mit {@link MessageDigest#isEqual} und damit ohne frueh abbrechenden Vergleich
 * (CLAUDE-security.md).
 *
 * <p>Die Zufallsquelle steckt im Konstruktor, damit Tests sie ersetzen koennen.
 */
public final class SecureTokens {

  private static final int TOKEN_BYTES = 32;
  private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

  private final SecureRandom random;

  /** Nutzt die Zufallsquelle der Plattform. */
  public SecureTokens() {
    this(new SecureRandom());
  }

  public SecureTokens(final SecureRandom random) {
    this.random = random;
  }

  /** Ein frisches, nicht ratbares Token. */
  public String newToken() {
    final byte[] bytes = new byte[TOKEN_BYTES];
    random.nextBytes(bytes);
    return ENCODER.encodeToString(bytes);
  }

  /** Der SHA-256-Hash eines Tokens — das, was in die Datenbank gehoert. */
  public String hash(final String token) {
    return ENCODER.encodeToString(sha256().digest(token.getBytes(StandardCharsets.UTF_8)));
  }

  /** Ob das vorgelegte Token zu dem gespeicherten Hash gehoert. */
  public boolean matches(final String token, final String expectedHash) {
    return MessageDigest.isEqual(
        hash(token).getBytes(StandardCharsets.UTF_8),
        expectedHash.getBytes(StandardCharsets.UTF_8));
  }

  /*
   * Nicht erreichbarer Zweig: SHA-256 ist fuer jede JCA-Implementierung Pflicht, die
   * NoSuchAlgorithmException kann nicht auftreten. Methodengenaue Ausnahme nach
   * CLAUDE-java.md §5.4 — der Rest der Klasse bleibt in Abdeckung und Mutationstest.
   */
  @ExcludeFromJacocoGeneratedReport
  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (final NoSuchAlgorithmException ursache) {
      throw new IllegalStateException("SHA-256 ist in dieser JVM nicht verfuegbar.", ursache);
    }
  }
}
