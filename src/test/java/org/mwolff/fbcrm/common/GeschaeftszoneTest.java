package org.mwolff.fbcrm.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/**
 * Die feste Zone jeder Datumsgrenze (E12).
 *
 * <p>Die beiden letzten Faelle sind der eigentliche Gegenstand: Dieselbe Sekunde ist in Berlin
 * schon der naechste Tag und nach UTC noch der vorige. Wer eine Frist oder einen Jahreswechsel
 * gegen UTC rechnet, laesst ein Angebot einen Tag zu lange gueltig sein und vergibt eine
 * Belegnummer im falschen Jahr.
 */
class GeschaeftszoneTest {

  /** Silvester, 23:30 Uhr in Berlin — nach UTC noch eine halbe Stunde vor Mitternacht. */
  private static final Instant JAHRESWECHSEL = Instant.parse("2026-12-31T23:30:00Z");

  @Test
  void zone_thenEuropeBerlin() {
    // When / Then
    assertThat(ZoneId.of("Europe/Berlin")).isEqualTo(Geschaeftszone.ZONE);
  }

  @Test
  void heute_givenHalfAnHourBeforeMidnightInUtc_thenAlreadyTheNextYearInTheZone() {
    // Given
    final Clock uhr = Clock.fixed(JAHRESWECHSEL, Geschaeftszone.ZONE);

    // When / Then
    assertThat(LocalDate.now(uhr)).isEqualTo(LocalDate.of(2027, 1, 1));
  }

  @Test
  void heute_givenTheSameInstantInUtc_thenStillTheOldYear() {
    // Given — derselbe Augenblick, gegen den Nullmeridian gerechnet.
    final Clock uhr = Clock.fixed(JAHRESWECHSEL, ZoneId.of("UTC"));

    // When / Then — der Unterschied, den E12 ausschliesst.
    assertThat(LocalDate.now(uhr)).isEqualTo(LocalDate.of(2026, 12, 31));
  }
}
