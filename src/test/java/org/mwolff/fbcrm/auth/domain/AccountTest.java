package org.mwolff.fbcrm.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.auth.Konten;

/** Verhalten des Kontos: Generation, Fortschreibungen und die drei Rollen. */
class AccountTest {

  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");
  private static final Instant GEAENDERT = Instant.parse("2026-09-18T12:00:00Z");

  private static Account kontoMitGeneration(final int generation) {
    return Konten.gespeichert(7L, "manne@example.org", generation, ANGELEGT);
  }

  @Test
  void mitNaechsterGeneration_thenIncrementsSessionGeneration() {
    // Given
    final Account bestand = kontoMitGeneration(3);

    // When
    final Account geaendert = bestand.mitNaechsterGeneration(GEAENDERT);

    // Then
    assertThat(geaendert.sessionGeneration()).isEqualTo(4);
  }

  @Test
  void mitNaechsterGeneration_thenStampsTheChangeTime() {
    // Given
    final Account bestand = kontoMitGeneration(0);

    // When
    final Account geaendert = bestand.mitNaechsterGeneration(GEAENDERT);

    // Then
    assertThat(geaendert.updatedAt()).isEqualTo(GEAENDERT);
  }

  @Test
  void mitNaechsterGeneration_thenLeavesEverythingElseUntouched() {
    // Given
    final Account bestand = kontoMitGeneration(1);

    // When
    final Account geaendert = bestand.mitNaechsterGeneration(GEAENDERT);

    // Then
    assertThat(geaendert)
        .satisfies(
            konto -> assertThat(konto.id()).isEqualTo(7L),
            konto -> assertThat(konto.email()).isEqualTo("manne@example.org"),
            konto -> assertThat(konto.displayName()).isEqualTo("Manne"),
            konto -> assertThat(konto.role()).isEqualTo(Role.C_LEVEL),
            konto -> assertThat(konto.aktiv()).isTrue(),
            konto -> assertThat(konto.createdAt()).isEqualTo(ANGELEGT));
  }

  @Test
  void mitNaechsterGeneration_thenLeavesTheOriginalUntouched() {
    // Given
    final Account bestand = kontoMitGeneration(1);

    // When
    bestand.mitNaechsterGeneration(GEAENDERT);

    // Then
    assertThat(bestand.sessionGeneration()).isEqualTo(1);
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
  void role_thenCLevelEmployeeAndReadOnlyAreTheKnownValues() {
    // When / Then — E12: der Waechter bleibt und haelt die neue Zusage fest.
    assertThat(Role.values()).containsExactly(Role.C_LEVEL, Role.EMPLOYEE, Role.READ_ONLY);
  }

  @Test
  void mitRolle_thenCarriesTheNewRoleAndTheChangeTime() {
    // Given
    final Account bestand = kontoMitGeneration(2);

    // When
    final Account geaendert = bestand.mitRolle(Role.READ_ONLY, GEAENDERT);

    // Then
    assertThat(geaendert)
        .satisfies(
            konto -> assertThat(konto.role()).isEqualTo(Role.READ_ONLY),
            konto -> assertThat(konto.updatedAt()).isEqualTo(GEAENDERT));
  }

  @Test
  void mitRolle_thenKeepsTheSessionGeneration() {
    // Given
    final Account bestand = kontoMitGeneration(2);

    // When
    final Account geaendert = bestand.mitRolle(Role.EMPLOYEE, GEAENDERT);

    // Then — die Rolle wird je Anfrage gelesen; die Sitzung muss dafuer nicht enden.
    assertThat(geaendert)
        .usingRecursiveComparison()
        .ignoringFields("role", "updatedAt")
        .isEqualTo(bestand);
  }

  @Test
  void mitAdresse_thenCarriesTheNewAddressAndTheNextGeneration() {
    // Given
    final Account bestand = kontoMitGeneration(2);

    // When
    final Account geaendert = bestand.mitAdresse("anna@firma.de", GEAENDERT);

    // Then — nach einer neuen Adresse meldet sich die Person neu an (Kriterium 6 von #409).
    assertThat(geaendert)
        .satisfies(
            konto -> assertThat(konto.email()).isEqualTo("anna@firma.de"),
            konto -> assertThat(konto.sessionGeneration()).isEqualTo(3),
            konto -> assertThat(konto.updatedAt()).isEqualTo(GEAENDERT));
  }

  @Test
  void mitAdresse_thenLeavesEverythingElseUntouched() {
    // Given
    final Account bestand = kontoMitGeneration(2);

    // When
    final Account geaendert = bestand.mitAdresse("anna@firma.de", GEAENDERT);

    // Then
    assertThat(geaendert)
        .usingRecursiveComparison()
        .ignoringFields("email", "sessionGeneration", "updatedAt")
        .isEqualTo(bestand);
  }

  @Test
  void mitNamen_thenCarriesTheNewNameAndKeepsTheSessionGeneration() {
    // Given
    final Account bestand = kontoMitGeneration(2);

    // When
    final Account geaendert = bestand.mitNamen("Anna Adler", GEAENDERT);

    // Then
    assertThat(geaendert)
        .satisfies(
            konto -> assertThat(konto.displayName()).isEqualTo("Anna Adler"),
            konto -> assertThat(konto.sessionGeneration()).isEqualTo(2),
            konto -> assertThat(konto.updatedAt()).isEqualTo(GEAENDERT));
  }

  @Test
  void mitNamen_thenLeavesEverythingElseUntouched() {
    // Given
    final Account bestand = kontoMitGeneration(2);

    // When
    final Account geaendert = bestand.mitNamen("Anna Adler", GEAENDERT);

    // Then
    assertThat(geaendert)
        .usingRecursiveComparison()
        .ignoringFields("displayName", "updatedAt")
        .isEqualTo(bestand);
  }

  @Test
  void stillgelegt_thenIsNotActiveAndCarriesTheNextGeneration() {
    // Given
    final Account bestand = kontoMitGeneration(2);

    // When
    final Account geaendert = bestand.stillgelegt(GEAENDERT);

    // Then — die Person ist sofort abgemeldet (Kriterium 7 von #409).
    assertThat(geaendert)
        .satisfies(
            konto -> assertThat(konto.aktiv()).isFalse(),
            konto -> assertThat(konto.sessionGeneration()).isEqualTo(3),
            konto -> assertThat(konto.updatedAt()).isEqualTo(GEAENDERT));
  }

  @Test
  void stillgelegt_thenLeavesEverythingElseUntouched() {
    // Given
    final Account bestand = kontoMitGeneration(2);

    // When
    final Account geaendert = bestand.stillgelegt(GEAENDERT);

    // Then
    assertThat(geaendert)
        .usingRecursiveComparison()
        .ignoringFields("aktiv", "sessionGeneration", "updatedAt")
        .isEqualTo(bestand);
  }

  @Test
  void aktiviert_givenADeactivatedAccount_thenIsActiveAndKeepsTheSessionGeneration() {
    // Given
    final Account bestand = Konten.mit(7L, "manne@example.org", Role.EMPLOYEE, false, 4, ANGELEGT);

    // When
    final Account geaendert = bestand.aktiviert(GEAENDERT);

    // Then — die alten Sitzungen bleiben ungueltig, weil das Stilllegen sie schon beendet hat.
    assertThat(geaendert)
        .satisfies(
            konto -> assertThat(konto.aktiv()).isTrue(),
            konto -> assertThat(konto.sessionGeneration()).isEqualTo(4),
            konto -> assertThat(konto.role()).isEqualTo(Role.EMPLOYEE),
            konto -> assertThat(konto.updatedAt()).isEqualTo(GEAENDERT));
  }

  @Test
  void fortschreibungen_thenLeaveTheOriginalUntouched() {
    // Given
    final Account bestand = kontoMitGeneration(2);

    // When
    bestand.mitRolle(Role.READ_ONLY, GEAENDERT);
    bestand.mitAdresse("anna@firma.de", GEAENDERT);
    bestand.mitNamen("Anna Adler", GEAENDERT);
    bestand.stillgelegt(GEAENDERT);

    // Then
    assertThat(bestand).isEqualTo(kontoMitGeneration(2));
  }

  @Test
  void mitNaechsterGeneration_givenADeactivatedAccount_thenStaysDeactivated() {
    // Given
    final Account bestand = Konten.mit(7L, "manne@example.org", Role.READ_ONLY, false, 1, ANGELEGT);

    // When
    final Account geaendert = bestand.mitNaechsterGeneration(GEAENDERT);

    // Then
    assertThat(geaendert.aktiv()).isFalse();
  }
}
