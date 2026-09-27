package org.mwolff.fbcrm.angebot.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Die Schreibweise der Angebotsnummer — Kriterium 11.
 *
 * <p>Drei Stellen sind das Mindeste und keine Obergrenze: Das tausendste Angebot eines Jahres
 * bekommt vier Stellen und nicht die Nummer 000.
 */
class AngebotsnummerTest {

  @Test
  void formatiere_givenTheFirstOfTheYear_thenPadsToThreeDigits() {
    // When
    final String nummer = Angebotsnummer.formatiere(2026, 1L);

    // Then
    assertThat(nummer).isEqualTo("A-2026-001");
  }

  @Test
  void formatiere_givenThreeDigits_thenLeavesThemUntouched() {
    // When
    final String nummer = Angebotsnummer.formatiere(2026, 999L);

    // Then
    assertThat(nummer).isEqualTo("A-2026-999");
  }

  @Test
  void formatiere_givenMoreThanThreeDigits_thenDoesNotTruncate() {
    // When
    final String nummer = Angebotsnummer.formatiere(2026, 1000L);

    // Then
    assertThat(nummer).isEqualTo("A-2026-1000");
  }
}
