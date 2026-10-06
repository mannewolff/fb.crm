package org.mwolff.fbcrm.startseite.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Year;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mwolff.fbcrm.startseite.application.Zeitraum;

/**
 * Die Wandlung des Adressparameters {@code zeitraum} (Plan #274, E3).
 *
 * <p>Was eine Zeichenkette zum Zeitraum macht, belegt {@code ZeitraumTest} an {@link
 * Zeitraum#aus(String)}. Hier steht nur, dass der Converter genau diese Regel nimmt und eine nicht
 * wandelbare Angabe als Wurf meldet — nur so wird daraus die 400 im {@code GlobalExceptionHandler}
 * und nicht ein stiller Rueckfall auf den laufenden Monat.
 */
class ZeitraumConverterTest {

  private final ZeitraumConverter converter = new ZeitraumConverter();

  @Test
  void convert_withJjjjMm_thenAMonat() {
    assertThat(converter.convert("2026-10")).isEqualTo(new Zeitraum.Monat(YearMonth.of(2026, 10)));
  }

  @Test
  void convert_withJjjj_thenAJahr() {
    assertThat(converter.convert("2026")).isEqualTo(new Zeitraum.Jahr(Year.of(2026)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"unsinn", "2026-13", "26", ""})
  void convert_withAValueThatIsNoZeitraum_thenThrows(final String roh) {
    assertThatThrownBy(() -> converter.convert(roh)).isInstanceOf(IllegalArgumentException.class);
  }
}
