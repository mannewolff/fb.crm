package org.mwolff.fbcrm.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Verhalten des Kontos: Passwortwechsel und Pruefung der Sitzungs-Generation. */
class AccountTest {

  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");
  private static final Instant GEAENDERT = Instant.parse("2026-09-18T12:00:00Z");

  private static Account kontoMitGeneration(final int generation) {
    return new Account(
        7L, "manne@example.org", "Manne", "alter-hash", Role.ADMIN, generation, ANGELEGT, ANGELEGT);
  }

  @Test
  void changePassword_thenIncrementsSessionGeneration() {
    // Given
    final Account bestand = kontoMitGeneration(3);

    // When
    final Account geaendert = bestand.changePassword("neuer-hash", GEAENDERT);

    // Then
    assertThat(geaendert.sessionGeneration()).isEqualTo(4);
  }

  @Test
  void changePassword_thenCarriesTheNewHash() {
    // Given
    final Account bestand = kontoMitGeneration(0);

    // When
    final Account geaendert = bestand.changePassword("neuer-hash", GEAENDERT);

    // Then
    assertThat(geaendert.passwordHash()).isEqualTo("neuer-hash");
  }

  @Test
  void changePassword_thenStampsTheChangeTime() {
    // Given
    final Account bestand = kontoMitGeneration(0);

    // When
    final Account geaendert = bestand.changePassword("neuer-hash", GEAENDERT);

    // Then
    assertThat(geaendert.updatedAt()).isEqualTo(GEAENDERT);
  }

  @Test
  void changePassword_thenLeavesIdentityRoleAndCreationUntouched() {
    // Given
    final Account bestand = kontoMitGeneration(1);

    // When
    final Account geaendert = bestand.changePassword("neuer-hash", GEAENDERT);

    // Then
    assertThat(geaendert)
        .satisfies(
            konto -> assertThat(konto.id()).isEqualTo(7L),
            konto -> assertThat(konto.email()).isEqualTo("manne@example.org"),
            konto -> assertThat(konto.displayName()).isEqualTo("Manne"),
            konto -> assertThat(konto.role()).isEqualTo(Role.ADMIN),
            konto -> assertThat(konto.createdAt()).isEqualTo(ANGELEGT));
  }

  @Test
  void changePassword_thenLeavesTheOriginalUntouched() {
    // Given
    final Account bestand = kontoMitGeneration(1);

    // When
    bestand.changePassword("neuer-hash", GEAENDERT);

    // Then
    assertThat(bestand.passwordHash()).isEqualTo("alter-hash");
  }

  @Test
  void matchesGeneration_givenTheCurrentGeneration_thenTrue() {
    // Given
    final Account bestand = kontoMitGeneration(5);

    // When
    final boolean passt = bestand.matchesGeneration(5L);

    // Then
    assertThat(passt).isTrue();
  }

  @Test
  void matchesGeneration_givenAnOlderGeneration_thenFalse() {
    // Given
    final Account bestand = kontoMitGeneration(5);

    // When
    final boolean passt = bestand.matchesGeneration(4L);

    // Then
    assertThat(passt).isFalse();
  }

  @Test
  void role_thenAdminIsTheOnlyKnownValue() {
    // When / Then
    assertThat(Role.values()).containsExactly(Role.ADMIN);
  }
}
