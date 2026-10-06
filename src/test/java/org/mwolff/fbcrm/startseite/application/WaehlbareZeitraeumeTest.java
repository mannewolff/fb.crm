package org.mwolff.fbcrm.startseite.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Year;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Welche Jahre und Monate auf der Startseite zur Wahl stehen (#273, Kriterien 1 und 2; Plan #274,
 * E8 und E9).
 *
 * <p>Der laufende Monat ist in allen Faellen Oktober 2026; das letzte Jahr ist damit 2025, und 2024
 * ist ein aelteres. Jede Grenze der beiden Kriterien hat einen eigenen Fall: das letzte Jahr mit
 * und ohne Rechnung, ein Monat mit blosser Arbeitszeit, der laufende Monat ohne jeden Vorgang und
 * ein Monat mit Rechnung nach dem laufenden.
 */
class WaehlbareZeitraeumeTest {

  private static final YearMonth LAUFEND = YearMonth.of(2026, 10);

  @Test
  void herleiten_withNothing_thenOnlyTheLaufendeJahrAndTheLaufendeMonat() {
    final WaehlbareZeitraeume waehlbar =
        WaehlbareZeitraeume.herleiten(LAUFEND, List.of(), List.of());

    assertThat(waehlbar.jahre()).containsExactly(Year.of(2026));
    assertThat(waehlbar.monate()).containsExactly(LAUFEND);
  }

  @Test
  void herleiten_withARechnungInTheLetzteJahr_thenTheLetzteJahrFollowsTheLaufende() {
    final WaehlbareZeitraeume waehlbar =
        WaehlbareZeitraeume.herleiten(LAUFEND, List.of(YearMonth.of(2025, 3)), List.of());

    assertThat(waehlbar.jahre()).containsExactly(Year.of(2026), Year.of(2025));
  }

  @Test
  void herleiten_withOnlyArbeitszeitInTheLetzteJahr_thenTheLetzteJahrIsNotWaehlbar() {
    // E8: Das Jahr haengt allein an Rechnungen, der Monat daneben auch an Arbeitszeit (#273,
    // Antwort 1). Der Monat steht zur Wahl, sein Jahr nicht.
    final WaehlbareZeitraeume waehlbar =
        WaehlbareZeitraeume.herleiten(LAUFEND, List.of(), List.of(YearMonth.of(2025, 3)));

    assertThat(waehlbar.jahre()).containsExactly(Year.of(2026));
    assertThat(waehlbar.monate()).contains(YearMonth.of(2025, 3));
  }

  @Test
  void herleiten_withRechnungenInOlderYears_thenTheyAreNeverWaehlbar() {
    final WaehlbareZeitraeume waehlbar =
        WaehlbareZeitraeume.herleiten(
            LAUFEND,
            List.of(YearMonth.of(2024, 12), YearMonth.of(2023, 6)),
            List.of(YearMonth.of(2024, 11)));

    assertThat(waehlbar.jahre()).containsExactly(Year.of(2026));
    assertThat(waehlbar.monate()).containsExactly(LAUFEND);
  }

  @Test
  void herleiten_withARechnungInAMonat_thenTheMonatIsWaehlbar() {
    final WaehlbareZeitraeume waehlbar =
        WaehlbareZeitraeume.herleiten(
            LAUFEND, List.of(YearMonth.of(2026, 4), YearMonth.of(2025, 1)), List.of());

    assertThat(waehlbar.monate())
        .containsExactly(LAUFEND, YearMonth.of(2026, 4), YearMonth.of(2025, 1));
  }

  @Test
  void herleiten_withOnlyArbeitszeitInAMonat_thenTheMonatIsWaehlbar() {
    final WaehlbareZeitraeume waehlbar =
        WaehlbareZeitraeume.herleiten(
            LAUFEND, List.of(), List.of(YearMonth.of(2026, 8), YearMonth.of(2025, 12)));

    assertThat(waehlbar.monate())
        .containsExactly(LAUFEND, YearMonth.of(2026, 8), YearMonth.of(2025, 12));
  }

  @Test
  void herleiten_withAMonatWithoutRechnungAndArbeitszeit_thenItIsNotWaehlbar() {
    final WaehlbareZeitraeume waehlbar =
        WaehlbareZeitraeume.herleiten(
            LAUFEND, List.of(YearMonth.of(2026, 7)), List.of(YearMonth.of(2026, 5)));

    assertThat(waehlbar.monate()).doesNotContain(YearMonth.of(2026, 6));
  }

  @Test
  void herleiten_withoutAnyVorgangInTheLaufendeMonat_thenItIsWaehlbarAnyway() {
    final WaehlbareZeitraeume waehlbar =
        WaehlbareZeitraeume.herleiten(
            LAUFEND, List.of(YearMonth.of(2026, 2)), List.of(YearMonth.of(2026, 3)));

    assertThat(waehlbar.monate())
        .containsExactly(LAUFEND, YearMonth.of(2026, 3), YearMonth.of(2026, 2));
  }

  @Test
  void herleiten_withARechnungAfterTheLaufendeMonat_thenThatMonatIsWaehlbarAndComesFirst() {
    // E9: Kriterium 2 nennt keine obere Grenze; jede Zeile der Jahresliste ist ein waehlbarer
    // Monat.
    final WaehlbareZeitraeume waehlbar =
        WaehlbareZeitraeume.herleiten(LAUFEND, List.of(YearMonth.of(2026, 12)), List.of());

    assertThat(waehlbar.monate()).containsExactly(YearMonth.of(2026, 12), LAUFEND);
  }

  @Test
  void herleiten_withRechnungAndArbeitszeitInTheSameMonat_thenItAppearsOnce() {
    final WaehlbareZeitraeume waehlbar =
        WaehlbareZeitraeume.herleiten(
            LAUFEND,
            List.of(YearMonth.of(2026, 9), LAUFEND, YearMonth.of(2026, 9)),
            List.of(YearMonth.of(2026, 9), LAUFEND));

    assertThat(waehlbar.monate()).containsExactly(LAUFEND, YearMonth.of(2026, 9));
  }

  @Test
  void herleiten_inJanuary_thenDecemberOfTheLetzteJahrIsWaehlbar() {
    final WaehlbareZeitraeume waehlbar =
        WaehlbareZeitraeume.herleiten(
            YearMonth.of(2027, 1), List.of(YearMonth.of(2026, 12)), List.of());

    assertThat(waehlbar.jahre()).containsExactly(Year.of(2027), Year.of(2026));
    assertThat(waehlbar.monate()).containsExactly(YearMonth.of(2027, 1), YearMonth.of(2026, 12));
  }

  @Test
  void enthaelt_thenWaehlbareMonateAndJahreAreContainedAndOthersNot() {
    final WaehlbareZeitraeume waehlbar =
        WaehlbareZeitraeume.herleiten(LAUFEND, List.of(YearMonth.of(2025, 3)), List.of());

    assertThat(waehlbar.enthaelt(new Zeitraum.Monat(LAUFEND))).isTrue();
    assertThat(waehlbar.enthaelt(new Zeitraum.Monat(YearMonth.of(2025, 3)))).isTrue();
    assertThat(waehlbar.enthaelt(new Zeitraum.Monat(YearMonth.of(2025, 4)))).isFalse();
    assertThat(waehlbar.enthaelt(new Zeitraum.Jahr(Year.of(2026)))).isTrue();
    assertThat(waehlbar.enthaelt(new Zeitraum.Jahr(Year.of(2025)))).isTrue();
    assertThat(waehlbar.enthaelt(new Zeitraum.Jahr(Year.of(2024)))).isFalse();
  }

  @Test
  void konstruktor_thenTheListsAreCopies() {
    final List<Year> jahre = new ArrayList<>(List.of(Year.of(2026)));
    final List<YearMonth> monate = new ArrayList<>(List.of(LAUFEND));

    final WaehlbareZeitraeume waehlbar = new WaehlbareZeitraeume(jahre, monate);
    jahre.add(Year.of(2025));
    monate.add(YearMonth.of(2026, 9));

    assertThat(waehlbar.jahre()).containsExactly(Year.of(2026));
    assertThat(waehlbar.monate()).containsExactly(LAUFEND);
  }
}
