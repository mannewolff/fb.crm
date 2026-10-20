package org.mwolff.fbcrm.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Der Einmalcode der Anmeldung (E1, E2).
 *
 * <p>Einloesbar ist er genau bis vor seine Ablaufzeit. Der Grenzfall gehoert dazu: Im Augenblick
 * des Ablaufs gilt er <b>nicht</b> mehr — sonst haenge die Gueltigkeit an der Aufloesung der Uhr.
 */
class LoginCodeTest {

  private static final Instant AUSGESTELLT = Instant.parse("2026-10-20T10:00:00Z");
  private static final Instant ABLAUF = Instant.parse("2026-10-20T10:10:00Z");
  private static final String ADRESSE = "manne@example.org";
  private static final int GRENZE = 5;

  private static LoginCode code(final int fehlversuche) {
    return new LoginCode(7L, ADRESSE, "hash", ABLAUF, fehlversuche, AUSGESTELLT);
  }

  @Test
  void ausgestellt_thenExpiresAfterTheGivenLifetime() {
    // When
    final LoginCode ausgestellt =
        LoginCode.ausgestellt(ADRESSE, "hash", AUSGESTELLT, Duration.ofMinutes(10));

    // Then
    assertThat(ausgestellt.expiresAt()).isEqualTo(ABLAUF);
  }

  @Test
  void ausgestellt_thenBelongsToTheAddressAndCarriesOnlyTheHashWithoutFailedAttempts() {
    // When
    final LoginCode ausgestellt =
        LoginCode.ausgestellt(ADRESSE, "hash", AUSGESTELLT, Duration.ofMinutes(10));

    // Then
    assertThat(ausgestellt)
        .extracting(
            LoginCode::id,
            LoginCode::email,
            LoginCode::codeHash,
            LoginCode::failedAttempts,
            LoginCode::createdAt)
        .containsExactly(null, ADRESSE, "hash", 0, AUSGESTELLT);
  }

  @Test
  void istEinloesbar_givenAMomentBeforeTheExpiry_thenTrue() {
    // When / Then
    assertThat(code(0).istEinloesbar(Instant.parse("2026-10-20T10:09:59Z"))).isTrue();
  }

  @Test
  void istEinloesbar_givenExactlyTheExpiryInstant_thenFalse() {
    // When / Then — im Augenblick des Ablaufs gilt der Code nicht mehr.
    assertThat(code(0).istEinloesbar(ABLAUF)).isFalse();
  }

  @Test
  void istEinloesbar_givenOneSecondAfterTheExpiry_thenFalse() {
    // When / Then
    assertThat(code(0).istEinloesbar(Instant.parse("2026-10-20T10:10:01Z"))).isFalse();
  }

  @Test
  void mitFehlversuch_thenCountsOneMoreFailedAttempt() {
    // When
    final LoginCode danach = code(2).mitFehlversuch();

    // Then
    assertThat(danach.failedAttempts()).isEqualTo(3);
  }

  @Test
  void mitFehlversuch_thenLeavesTheOriginalUntouched() {
    // Given
    final LoginCode vorher = code(2);

    // When
    vorher.mitFehlversuch();

    // Then
    assertThat(vorher).isEqualTo(code(2));
  }

  @Test
  void mitFehlversuch_thenLeavesEverythingElseUntouched() {
    // When
    final LoginCode danach = code(2).mitFehlversuch();

    // Then
    assertThat(danach)
        .extracting(
            LoginCode::id,
            LoginCode::email,
            LoginCode::codeHash,
            LoginCode::expiresAt,
            LoginCode::createdAt)
        .containsExactly(7L, ADRESSE, "hash", ABLAUF, AUSGESTELLT);
  }

  @Test
  void istVerfallen_givenOneAttemptBelowTheLimit_thenFalse() {
    // When / Then
    assertThat(code(GRENZE - 1).istVerfallen(GRENZE)).isFalse();
  }

  @Test
  void istVerfallen_givenTheLimitReached_thenTrue() {
    // When / Then — die fuenfte Fehleingabe laesst den Code verfallen (Kriterium 4).
    assertThat(code(GRENZE).istVerfallen(GRENZE)).isTrue();
  }

  @Test
  void istVerfallen_givenTheLimitReachedByTheLastFailedAttempt_thenTrue() {
    // When / Then
    assertThat(code(GRENZE - 1).mitFehlversuch().istVerfallen(GRENZE)).isTrue();
  }
}
