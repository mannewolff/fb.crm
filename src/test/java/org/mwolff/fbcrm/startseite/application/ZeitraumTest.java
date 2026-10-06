package org.mwolff.fbcrm.startseite.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Der gewaehlte Zeitraum der Startseite: ein Monat oder ein Jahr (#273, Kriterien 1 und 2; Plan
 * #274, E1 und E15).
 *
 * <p>Geprueft werden die Grenzen beider Arten und die Wandlung aus dem Adressparameter. Was kein
 * {@code JJJJ-MM} mit gueltigem Monat und kein {@code JJJJ} ist, wird kein Zeitraum — der
 * Anwendungsfall faellt dann auf den laufenden Monat zurueck.
 */
class ZeitraumTest {

  private static final Zeitraum OKTOBER = new Zeitraum.Monat(YearMonth.of(2026, 10));
  private static final Zeitraum JAHR_2026 = new Zeitraum.Jahr(Year.of(2026));

  @Test
  void monat_thenVonIsTheFirstAndBisTheLastDayOfTheMonth() {
    assertThat(OKTOBER.von()).isEqualTo(LocalDate.of(2026, 10, 1));
    assertThat(OKTOBER.bis()).isEqualTo(LocalDate.of(2026, 10, 31));
  }

  @Test
  void jahr_thenVonIsTheFirstOfJanuaryAndBisTheThirtyFirstOfDecember() {
    assertThat(JAHR_2026.von()).isEqualTo(LocalDate.of(2026, 1, 1));
    assertThat(JAHR_2026.bis()).isEqualTo(LocalDate.of(2026, 12, 31));
  }

  @Test
  void monat_thenItContainsOnlyItself() {
    assertThat(OKTOBER.enthaelt(YearMonth.of(2026, 10))).isTrue();
    assertThat(OKTOBER.enthaelt(YearMonth.of(2026, 9))).isFalse();
    assertThat(OKTOBER.enthaelt(YearMonth.of(2025, 10))).isFalse();
  }

  @Test
  void jahr_thenItContainsEveryMonthOfTheYearAndNoOther() {
    assertThat(JAHR_2026.enthaelt(YearMonth.of(2026, 1))).isTrue();
    assertThat(JAHR_2026.enthaelt(YearMonth.of(2026, 12))).isTrue();
    assertThat(JAHR_2026.enthaelt(YearMonth.of(2025, 12))).isFalse();
    assertThat(JAHR_2026.enthaelt(YearMonth.of(2027, 1))).isFalse();
  }

  @Test
  void wert_thenTheMonatReadsJjjjMmAndTheJahrJjjj() {
    assertThat(OKTOBER.wert()).isEqualTo("2026-10");
    assertThat(new Zeitraum.Monat(YearMonth.of(2026, 3)).wert()).isEqualTo("2026-03");
    assertThat(JAHR_2026.wert()).isEqualTo("2026");
  }

  @Test
  void aus_withJjjjMm_thenAMonat() {
    assertThat(Zeitraum.aus("2026-10")).contains(OKTOBER);
    assertThat(Zeitraum.aus("2026-01")).contains(new Zeitraum.Monat(YearMonth.of(2026, 1)));
    assertThat(Zeitraum.aus("2026-12")).contains(new Zeitraum.Monat(YearMonth.of(2026, 12)));
  }

  @Test
  void aus_withJjjj_thenAJahr() {
    assertThat(Zeitraum.aus("2026")).contains(JAHR_2026);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "2026-13",
        "2026-00",
        "",
        "unsinn",
        "26",
        "2026-1",
        "20261",
        "2026-100",
        "2026-1a",
        " 2026",
        "2026-10 ",
        "2026/10",
        "-2026"
      })
  void aus_withAnythingElse_thenEmpty(final String text) {
    assertThat(Zeitraum.aus(text)).isEmpty();
  }

  @Test
  void zeitraum_thenASwitchWithoutDefaultCoversBothArten() {
    assertThat(art(OKTOBER)).isEqualTo("Monat");
    assertThat(art(JAHR_2026)).isEqualTo("Jahr");
  }

  /* Uebersetzt nur, weil Zeitraum versiegelt ist und genau diese beiden Arten erlaubt. */
  private static String art(final Zeitraum zeitraum) {
    return switch (zeitraum) {
      case Zeitraum.Monat monat -> "Monat";
      case Zeitraum.Jahr jahr -> "Jahr";
    };
  }
}
