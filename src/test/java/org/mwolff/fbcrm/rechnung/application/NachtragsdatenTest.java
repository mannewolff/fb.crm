package org.mwolff.fbcrm.rechnung.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Die Nummer der nachgetragenen Rechnung wird genau einmal getrimmt — beim Bauen (Plan #259, E10).
 */
class NachtragsdatenTest {

  @Test
  void construct_withBlanksAroundTheNummer_thenTheyAreGone() {
    // When
    final Nachtragsdaten daten =
        new Nachtragsdaten(
            4L, " \tRE-1 ", LocalDate.of(2026, 2, 1), BigDecimal.TEN, BigDecimal.TEN);

    // Then
    assertThat(daten.nummer()).isEqualTo("RE-1");
  }
}
