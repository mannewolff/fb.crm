package org.mwolff.fbcrm.auftrag.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Die Schreibweise der Auftragsnummer — Kriterium 3.
 *
 * <p>Dieselbe Regel wie beim Angebot, nur mit dem Kuerzel {@code AU}: Drei Stellen sind das
 * Mindeste und keine Obergrenze — der tausendste Auftrag eines Jahres bekommt vier Stellen und
 * nicht die Nummer 000.
 */
class AuftragsnummerTest {

  @Test
  void formatiere_givenTheFirstOfTheYear_thenPadsToThreeDigits() {
    // When
    final String nummer = Auftragsnummer.formatiere(2026, 1L);

    // Then
    assertThat(nummer).isEqualTo("AU-2026-001");
  }

  @Test
  void formatiere_givenThreeDigits_thenLeavesThemUntouched() {
    // When
    final String nummer = Auftragsnummer.formatiere(2026, 999L);

    // Then
    assertThat(nummer).isEqualTo("AU-2026-999");
  }

  @Test
  void formatiere_givenMoreThanThreeDigits_thenDoesNotTruncate() {
    // When
    final String nummer = Auftragsnummer.formatiere(2026, 1000L);

    // Then
    assertThat(nummer).isEqualTo("AU-2026-1000");
  }

  @Test
  void formatiere_givenAnotherYear_thenCarriesItIntoTheNumber() {
    // When
    final String nummer = Auftragsnummer.formatiere(2027, 1L);

    // Then
    assertThat(nummer).isEqualTo("AU-2027-001");
  }
}
