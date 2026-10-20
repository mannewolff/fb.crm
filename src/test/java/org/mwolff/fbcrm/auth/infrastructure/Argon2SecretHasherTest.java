package org.mwolff.fbcrm.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Passwort-Hashing mit Argon2id (CLAUDE-security.md). */
class Argon2SecretHasherTest {

  private static final String CODE = "042424";

  private final Argon2SecretHasher hasher = new Argon2SecretHasher();

  @Test
  void hash_givenTheSamePasswordTwice_thenProducesDifferentHashes() {
    // When
    final String erster = hasher.hash(CODE);
    final String zweiter = hasher.hash(CODE);

    // Then
    assertThat(erster).isNotEqualTo(zweiter);
  }

  @Test
  void hash_thenNeverContainsThePasswordItself() {
    // When
    final String hash = hasher.hash(CODE);

    // Then
    assertThat(hash).doesNotContain(CODE).startsWith("$argon2id$");
  }

  @Test
  void matches_givenTheCorrectPassword_thenTrue() {
    // Given
    final String hash = hasher.hash(CODE);

    // When
    final boolean passt = hasher.matches(CODE, hash);

    // Then
    assertThat(passt).isTrue();
  }

  @Test
  void matches_givenAWrongPassword_thenFalse() {
    // Given
    final String hash = hasher.hash(CODE);

    // When
    final boolean passt = hasher.matches("999999", hash);

    // Then
    assertThat(passt).isFalse();
  }
}
