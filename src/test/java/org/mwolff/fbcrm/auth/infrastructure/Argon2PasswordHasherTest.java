package org.mwolff.fbcrm.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Passwort-Hashing mit Argon2id (CLAUDE-security.md). */
class Argon2PasswordHasherTest {

  private static final String PASSWORT = "ein-hinreichend-langes-passwort";

  private final Argon2PasswordHasher hasher = new Argon2PasswordHasher();

  @Test
  void hash_givenTheSamePasswordTwice_thenProducesDifferentHashes() {
    // When
    final String erster = hasher.hash(PASSWORT);
    final String zweiter = hasher.hash(PASSWORT);

    // Then
    assertThat(erster).isNotEqualTo(zweiter);
  }

  @Test
  void hash_thenNeverContainsThePasswordItself() {
    // When
    final String hash = hasher.hash(PASSWORT);

    // Then
    assertThat(hash).doesNotContain(PASSWORT).startsWith("$argon2id$");
  }

  @Test
  void matches_givenTheCorrectPassword_thenTrue() {
    // Given
    final String hash = hasher.hash(PASSWORT);

    // When
    final boolean passt = hasher.matches(PASSWORT, hash);

    // Then
    assertThat(passt).isTrue();
  }

  @Test
  void matches_givenAWrongPassword_thenFalse() {
    // Given
    final String hash = hasher.hash(PASSWORT);

    // When
    final boolean passt = hasher.matches("ein-ganz-anderes-passwort", hash);

    // Then
    assertThat(passt).isFalse();
  }
}
