package org.mwolff.fbcrm.auth.domain;

import java.time.Duration;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Identifiable;

/**
 * Der Einmal-Token des Passwort-Resets (K7, E24).
 *
 * <p>Gespeichert wird nie das Token selbst, sondern sein SHA-256-Hash ({@code SecureTokens}) — wer
 * die Tabelle liest, kann daraus keinen benutzbaren Link bauen.
 *
 * <p>Einloesbar ist er genau dann, wenn er weder benutzt noch abgelaufen ist. Im Augenblick des
 * Ablaufs gilt er <b>nicht</b> mehr; ein {@code isBefore} statt eines {@code isAfter} auf der
 * anderen Seite haenge die Gueltigkeit sonst an der Aufloesung der Uhr.
 *
 * <p>Unveraenderlich: {@link #redeemed} liefert einen neuen Token, statt diesen zu aendern. Der
 * Zeitpunkt kommt von aussen, weil die Domaene keine Uhr kennt (CLAUDE-java.md §6.2).
 *
 * @param id technische Id — {@code null}, solange der Token nicht gespeichert ist
 * @param accountId Konto, fuer das der Token gilt
 * @param tokenHash SHA-256-Hash des Tokens; nie das Token selbst
 * @param expiresAt Zeitpunkt, ab dem der Token nicht mehr gilt
 * @param usedAt Zeitpunkt der Einloesung — {@code null}, solange er unbenutzt ist
 * @param createdAt Zeitpunkt der Ausstellung
 */
public record PasswordResetToken(
    @Nullable Long id,
    long accountId,
    String tokenHash,
    Instant expiresAt,
    @Nullable Instant usedAt,
    Instant createdAt)
    implements Identifiable {

  /**
   * Ein frisch ausgestellter, unbenutzter Token.
   *
   * @param accountId Konto, fuer das er gilt
   * @param tokenHash SHA-256-Hash des ausgegebenen Tokens
   * @param jetzt Zeitpunkt der Ausstellung
   * @param laufzeit Zeitspanne, nach der er verfaellt
   */
  public static PasswordResetToken issued(
      final long accountId, final String tokenHash, final Instant jetzt, final Duration laufzeit) {
    return new PasswordResetToken(null, accountId, tokenHash, jetzt.plus(laufzeit), null, jetzt);
  }

  /**
   * Ob der Token jetzt noch eingeloest werden kann.
   *
   * @param jetzt Zeitpunkt der Pruefung
   */
  public boolean isRedeemable(final Instant jetzt) {
    return usedAt == null && jetzt.isBefore(expiresAt);
  }

  /**
   * Der Token als eingeloest — ein zweites Mal gilt er nicht (K7).
   *
   * @param jetzt Zeitpunkt der Einloesung
   */
  public PasswordResetToken redeemed(final Instant jetzt) {
    return new PasswordResetToken(id, accountId, tokenHash, expiresAt, jetzt, createdAt);
  }
}
