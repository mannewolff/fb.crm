package org.mwolff.fbcrm.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Der Einmal-Token des Passwort-Resets (K7, E24).
 *
 * <p>Einloesbar ist er genau dann, wenn er weder benutzt noch abgelaufen ist. Der Grenzfall gehoert
 * dazu: Im Augenblick des Ablaufs gilt er <b>nicht</b> mehr — sonst haenge die Gueltigkeit an der
 * Aufloesung der Uhr.
 */
class PasswordResetTokenTest {

  private static final Instant AUSGESTELLT = Instant.parse("2026-09-18T10:00:00Z");
  private static final Instant ABLAUF = Instant.parse("2026-09-18T11:00:00Z");
  private static final long KONTO_ID = 11L;

  private static PasswordResetToken token(final Instant benutztAm) {
    return new PasswordResetToken(7L, KONTO_ID, "hash", ABLAUF, benutztAm, AUSGESTELLT);
  }

  @Test
  void issued_thenExpiresAfterTheGivenLifetime() {
    // When
    final PasswordResetToken ausgestellt =
        PasswordResetToken.issued(KONTO_ID, "hash", AUSGESTELLT, Duration.ofHours(1));

    // Then
    assertThat(ausgestellt.expiresAt()).isEqualTo(ABLAUF);
  }

  @Test
  void issued_thenBelongsToTheAccountAndCarriesOnlyTheHash() {
    // When
    final PasswordResetToken ausgestellt =
        PasswordResetToken.issued(KONTO_ID, "hash", AUSGESTELLT, Duration.ofHours(1));

    // Then
    assertThat(ausgestellt)
        .extracting(
            PasswordResetToken::id,
            PasswordResetToken::accountId,
            PasswordResetToken::tokenHash,
            PasswordResetToken::usedAt,
            PasswordResetToken::createdAt)
        .containsExactly(null, KONTO_ID, "hash", null, AUSGESTELLT);
  }

  @Test
  void isRedeemable_givenAFreshTokenBeforeItsExpiry_thenTrue() {
    // When / Then
    assertThat(token(null).isRedeemable(Instant.parse("2026-09-18T10:59:59Z"))).isTrue();
  }

  @Test
  void isRedeemable_givenExactlyTheExpiryInstant_thenFalse() {
    // When / Then — im Augenblick des Ablaufs ist der Link verbraucht.
    assertThat(token(null).isRedeemable(ABLAUF)).isFalse();
  }

  @Test
  void isRedeemable_givenAnExpiredToken_thenFalse() {
    // When / Then
    assertThat(token(null).isRedeemable(Instant.parse("2026-09-18T11:00:01Z"))).isFalse();
  }

  @Test
  void isRedeemable_givenAnAlreadyUsedToken_thenFalse() {
    // When / Then — K7: der Link gilt genau einmal.
    assertThat(token(AUSGESTELLT).isRedeemable(Instant.parse("2026-09-18T10:30:00Z"))).isFalse();
  }

  @Test
  void redeemed_thenStampsTheMomentOfUse() {
    // When
    final PasswordResetToken eingeloest = token(null).redeemed(AUSGESTELLT);

    // Then
    assertThat(eingeloest.usedAt()).isEqualTo(AUSGESTELLT);
  }

  @Test
  void redeemed_thenIsNoLongerRedeemable() {
    // When
    final PasswordResetToken eingeloest = token(null).redeemed(AUSGESTELLT);

    // Then
    assertThat(eingeloest.isRedeemable(Instant.parse("2026-09-18T10:30:00Z"))).isFalse();
  }

  @Test
  void redeemed_thenLeavesEverythingElseUntouched() {
    // When
    final PasswordResetToken eingeloest = token(null).redeemed(AUSGESTELLT);

    // Then
    assertThat(eingeloest)
        .extracting(
            PasswordResetToken::id,
            PasswordResetToken::accountId,
            PasswordResetToken::tokenHash,
            PasswordResetToken::expiresAt,
            PasswordResetToken::createdAt)
        .containsExactly(7L, KONTO_ID, "hash", ABLAUF, AUSGESTELLT);
  }
}
