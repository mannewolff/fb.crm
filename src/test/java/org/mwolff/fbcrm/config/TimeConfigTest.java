package org.mwolff.fbcrm.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

/**
 * Die Uhr der Anwendung laeuft in UTC — wie die Zeitstempel in der Datenbank — und tickt nur in
 * Mikrosekunden, weil Postgres {@code timestamptz} nicht feiner speichert.
 */
class TimeConfigTest {

  @Test
  void clock_thenRunsInUtc() {
    // When / Then
    assertThat(new TimeConfig().clock().getZone()).isEqualTo(ZoneOffset.UTC);
  }

  @Test
  void clock_thenTicksInMicroseconds() {
    // Given
    final Instant jetzt = new TimeConfig().clock().instant();

    // When / Then
    assertThat(jetzt).isEqualTo(jetzt.truncatedTo(ChronoUnit.MICROS));
  }

  @Test
  void mikrosekundengenau_whenBasisHasNanoseconds_thenCutsToMicroseconds() {
    // Given
    final Clock basis =
        Clock.fixed(Instant.parse("2026-10-05T18:26:55.206945024Z"), ZoneOffset.UTC);

    // When
    final Instant gekuerzt = TimeConfig.mikrosekundengenau(basis).instant();

    // Then
    assertThat(gekuerzt).isEqualTo(Instant.parse("2026-10-05T18:26:55.206945Z"));
  }

  @Test
  void mikrosekundengenau_whenBasisAlreadyInMicroseconds_thenKeepsValue() {
    // Given
    final Instant schonMikrosekunden = Instant.parse("2026-10-05T18:26:55.206945Z");
    final Clock basis = Clock.fixed(schonMikrosekunden, ZoneOffset.UTC);

    // When
    final Instant unveraendert = TimeConfig.mikrosekundengenau(basis).instant();

    // Then
    assertThat(unveraendert).isEqualTo(schonMikrosekunden);
  }

  @Test
  void mikrosekundengenau_thenKeepsZoneOfBasis() {
    // Given
    final ZoneId zone = ZoneId.of("Europe/Berlin");
    final Clock basis = Clock.fixed(Instant.parse("2026-10-05T18:26:55.206945024Z"), zone);

    // When / Then
    assertThat(TimeConfig.mikrosekundengenau(basis).getZone()).isEqualTo(zone);
  }
}
