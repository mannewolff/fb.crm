package org.mwolff.fbcrm.arbeitszeit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Die Rechenregeln und Invarianten eines Zeiteintrags.
 *
 * <p>Gegenstand sind die drei Zusagen des Records: die Dauer als Minuten und als Stunden mit zwei
 * Nachkommastellen (Plan #194, E5), die Ueberschneidung mit berührenden Grenzen als Nicht-Fall (A8)
 * und die Invariante aus Raster und {@code bis > von} (A19).
 */
class ZeiteintragTest {

  private static final LocalDate TAG = LocalDate.of(2026, 11, 12);
  private static final Instant ANGELEGT = Instant.parse("2026-11-12T08:00:00Z");
  private static final long POSITION = 7L;

  private static Zeiteintrag eintrag(final String von, final String bis) {
    return eintrag(TAG, von, bis);
  }

  private static Zeiteintrag eintrag(final LocalDate tag, final String von, final String bis) {
    return new Zeiteintrag(
        null, POSITION, tag, LocalTime.parse(von), LocalTime.parse(bis), ANGELEGT, ANGELEGT);
  }

  @Test
  void minuten_givenAnEntryFromNineToTenFortyFive_thenOneHundredAndFive() {
    // When
    final long minuten = eintrag("09:00", "10:45").minuten();

    // Then
    assertThat(minuten).isEqualTo(105L);
  }

  @Test
  void minuten_givenTheShortestPossibleEntry_thenFifteen() {
    // When — eine Viertelstunde ist die kleinste erfassbare Dauer (A4).
    final long minuten = eintrag("09:00", "09:15").minuten();

    // Then
    assertThat(minuten).isEqualTo(15L);
  }

  @Test
  void stunden_givenOneHourAndFortyFiveMinutes_thenOnePointSevenFive() {
    // When — das Beispiel aus Antwort 6 der fachlichen Quelle #193.
    final BigDecimal stunden = eintrag("09:00", "10:45").stunden();

    // Then
    assertThat(stunden).isEqualByComparingTo("1.75");
  }

  @Test
  void stunden_givenAFullHour_thenTwoDecimalPlaces() {
    // When — die Skala ist immer 2, auch wenn nichts zu runden war (E5).
    final BigDecimal stunden = eintrag("09:00", "10:00").stunden();

    // Then
    assertThat(stunden).hasToString("1.00");
  }

  @ParameterizedTest
  @CsvSource({"15, 0.25", "30, 0.50", "45, 0.75", "105, 1.75", "0, 0.00"})
  void stundenAus_givenMinutesOnTheQuarterHourGrid_thenAnExactValue(
      final long minuten, final String erwartet) {
    // When — der Weg, auf dem der Bestand seine Summen rechnet.
    final BigDecimal stunden = Zeiteintrag.stundenAus(minuten);

    // Then
    assertThat(stunden).hasToString(erwartet);
  }

  @Test
  void ueberschneidet_givenTheSameSpan_thenTrue() {
    // When
    final boolean ueberschneidung =
        eintrag("09:00", "11:00").ueberschneidet(eintrag("09:00", "11:00"));

    // Then
    assertThat(ueberschneidung).isTrue();
  }

  @Test
  void ueberschneidet_givenAContainedSpan_thenTrue() {
    // When
    final boolean ueberschneidung =
        eintrag("09:00", "12:00").ueberschneidet(eintrag("10:00", "11:00"));

    // Then
    assertThat(ueberschneidung).isTrue();
  }

  @Test
  void ueberschneidet_givenAPartialOverlap_thenTrue() {
    // When
    final boolean ueberschneidung =
        eintrag("09:00", "11:00").ueberschneidet(eintrag("10:45", "12:00"));

    // Then
    assertThat(ueberschneidung).isTrue();
  }

  @Test
  void ueberschneidet_givenABoundaryTouchedFromBelow_thenFalse() {
    // When — 9:00 bis 10:00 und 10:00 bis 11:00 sind zulaessig (A8).
    final boolean ueberschneidung =
        eintrag("09:00", "10:00").ueberschneidet(eintrag("10:00", "11:00"));

    // Then
    assertThat(ueberschneidung).isFalse();
  }

  @Test
  void ueberschneidet_givenABoundaryTouchedFromAbove_thenFalse() {
    // When — dieselbe Lage von der anderen Seite; die Zusage ist symmetrisch.
    final boolean ueberschneidung =
        eintrag("10:00", "11:00").ueberschneidet(eintrag("09:00", "10:00"));

    // Then
    assertThat(ueberschneidung).isFalse();
  }

  @Test
  void ueberschneidet_givenSeparateSpans_thenFalse() {
    // When
    final boolean ueberschneidung =
        eintrag("09:00", "10:00").ueberschneidet(eintrag("13:00", "14:00"));

    // Then
    assertThat(ueberschneidung).isFalse();
  }

  @Test
  void ueberschneidet_givenTheSameSpanOnAnotherDay_thenFalse() {
    // When — dieselbe Uhrzeit an einem anderen Tag ist keine Ueberschneidung.
    final boolean ueberschneidung =
        eintrag("09:00", "11:00").ueberschneidet(eintrag(TAG.plusDays(1), "09:00", "11:00"));

    // Then
    assertThat(ueberschneidung).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"09:10", "09:01", "09:14", "09:00:30", "09:00:00.500"})
  void konstruktor_givenAStartOutsideTheQuarterHourGrid_thenRejected(final String von) {
    // When / Then
    assertThatThrownBy(() -> eintrag(von, "12:00"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Viertelstunde");
  }

  @ParameterizedTest
  @ValueSource(strings = {"10:10", "10:59", "10:00:30", "10:00:00.500"})
  void konstruktor_givenAnEndOutsideTheQuarterHourGrid_thenRejected(final String bis) {
    // When / Then
    assertThatThrownBy(() -> eintrag("09:00", bis))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Viertelstunde");
  }

  @ParameterizedTest
  @CsvSource({"00:00, 00:15", "09:15, 09:30", "09:30, 09:45", "09:45, 10:00", "23:30, 23:45"})
  void konstruktor_givenEveryMinuteOfTheGrid_thenAccepted(final String von, final String bis) {
    // When — 23:45 ist die Obergrenze (E6); jede Viertelstunde kommt durch.
    final Zeiteintrag angenommen = eintrag(von, bis);

    // Then
    assertThat(angenommen.von()).isEqualTo(LocalTime.parse(von));
  }

  @Test
  void konstruktor_givenAnEndBeforeTheStart_thenRejected() {
    // When / Then
    assertThatThrownBy(() -> eintrag("11:00", "09:00"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("nach");
  }

  @Test
  void konstruktor_givenAnEndEqualToTheStart_thenRejected() {
    // When / Then — ein Eintrag ohne Dauer ist kein Eintrag.
    assertThatThrownBy(() -> eintrag("09:00", "09:00"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("nach");
  }
}
