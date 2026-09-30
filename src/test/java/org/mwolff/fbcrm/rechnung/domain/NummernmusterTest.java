package org.mwolff.fbcrm.rechnung.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Die Schreibweise der Rechnungsnummer (Plan #161, E4 und E5).
 *
 * <p>Die Beispiele stehen woertlich genauso in {@code frontend/src/lib/nummernmuster.test.ts}: Die
 * Regel lebt zweimal — hier entscheidet sie beim Speichern, dort speist sie die Vorschau beim
 * Tippen. Die gemeinsame Tabelle ist das Band zwischen den beiden Fassungen.
 */
class NummernmusterTest {

  /** Die ungueltigen Muster, jedes mit dem Grund, aus dem es ungueltig ist. */
  private static Stream<String> ungueltigeMuster() {
    return Stream.of(
        // ohne Nummern-Platzhalter
        "RECHNUNG",
        // zwei Nummern-Platzhalter
        "{NN}-{NN}",
        // zwei Jahres-Platzhalter
        "{JJJJ}-{JJ}-{NNNN}",
        // {JJJ} ist keine der beiden Jahresformen
        "{NNNN}-{JJJ}",
        // unbekannter Platzhalter
        "{X}-{NNNN}",
        // das Leerzeichen steht nicht in der Liste der erlaubten Zeichen
        "{NNNN} 2026",
        // 51 Zeichen — eines zu viel
        "{NNNN}" + "A".repeat(45));
  }

  @ParameterizedTest
  @CsvSource({
    "{NNNN}-{JJJJ}, 3, 2026, 0003-2026",
    "R{JJ}-{NNNN}, 3, 2026, R26-0003",
    "{JJJJ}/{N}, 3, 2026, 2026/3",
    "{NNNN}-{JJJJ}, 10000, 2026, 10000-2026",
    "{NNNN}, 3, 2026, 0003"
  })
  void rechnungsnummer_givenAValidPattern_thenBuildsTheNumber(
      final String muster, final int laufendeNummer, final int jahr, final String erwartet) {
    // When
    final String nummer = new Nummernmuster(muster).rechnungsnummer(laufendeNummer, jahr);

    // Then
    assertThat(nummer).isEqualTo(erwartet);
  }

  @ParameterizedTest
  @ValueSource(strings = {"{NNNN}-{JJJJ}", "R{JJ}-{NNNN}", "{JJJJ}/{N}", "{NNNN}"})
  void istGueltig_givenAValidPattern_thenTrue(final String muster) {
    // When / Then
    assertThat(Nummernmuster.istGueltig(muster)).isTrue();
  }

  @ParameterizedTest
  @MethodSource("ungueltigeMuster")
  void istGueltig_givenAnInvalidPattern_thenFalse(final String muster) {
    // When / Then
    assertThat(Nummernmuster.istGueltig(muster)).isFalse();
  }

  @ParameterizedTest
  @MethodSource("ungueltigeMuster")
  void nummernmuster_givenAnInvalidPattern_thenRejected(final String muster) {
    // When / Then — das Wertobjekt laesst sich mit einem ungueltigen Muster nicht bauen.
    assertThatThrownBy(() -> new Nummernmuster(muster))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(muster);
  }

  @ParameterizedTest
  @ValueSource(strings = {"{NNNN}-{JJJJ}", "R{JJ}-{NNNN}"})
  void text_thenKeepsThePatternAsWritten(final String muster) {
    // When / Then
    assertThat(new Nummernmuster(muster).text()).isEqualTo(muster);
  }

  @ParameterizedTest
  @CsvSource({
    // mit Jahres-Platzhalter, lang wie kurz: der Kreis gehoert dem Jahr
    "{NNNN}-{JJJJ}, 2026, 2026",
    "R{JJ}-{NNNN}, 2026, 2026",
    "{JJJJ}/{N}, 2025, 2025",
    // ohne Jahres-Platzhalter: ein einziger, jahresloser Kreis unter der 0
    "{NNNN}, 2026, 0",
    "R-{N}.A, 2026, 0"
  })
  void zaehlerjahr_thenAnswersWithTheYearOnlyWhenThePatternCarriesOne(
      final String muster, final int jahr, final int erwartet) {
    // When / Then — an dieser einen Stelle entscheidet sich, welchem Zaehler eine Nummer gehoert.
    assertThat(new Nummernmuster(muster).zaehlerjahr(jahr)).isEqualTo(erwartet);
  }
}
