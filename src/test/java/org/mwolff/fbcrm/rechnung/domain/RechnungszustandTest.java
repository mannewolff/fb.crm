package org.mwolff.fbcrm.rechnung.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Die vier Zustaende einer Rechnung und die eine Frage, die sie beantworten (Issue #253).
 *
 * <p>Gegenstand ist {@link Rechnungszustand#istGestellt()}: Bezahlt und Abgeschrieben sind
 * weiterhin gestellte Rechnungen — sie behalten Nummer, Dokument und ihre abgerechneten Mengen, und
 * jede Pruefung, die bisher auf {@code == GESTELLT} stand, fragt diese Methode. Ohne sie gaeben
 * bezahlte Rechnungen ihre Positionsmengen frei und verschwaenden aus der Monatsabrechnung.
 *
 * <p>Dazu die Kantenregel {@link Rechnungszustand#ausgangswechselErlaubt}, die die Rechnung und die
 * nachgetragene Rechnung gleichermassen fragen (Plan #259, E4).
 */
class RechnungszustandTest {

  @ParameterizedTest
  @EnumSource(
      value = Rechnungszustand.class,
      names = {"GESTELLT", "BEZAHLT", "ABGESCHRIEBEN"})
  void istGestellt_givenEveryStateButEntwurf_thenTrue(final Rechnungszustand zustand) {
    assertThat(zustand.istGestellt()).isTrue();
  }

  @Test
  void istGestellt_givenEntwurf_thenFalse() {
    assertThat(Rechnungszustand.ENTWURF.istGestellt()).isFalse();
  }

  /**
   * Die Kantenregel aus Issue #253, an einer Stelle fuer beide Aggregate (Plan #259, E4).
   *
   * <p>Alle sechzehn Paare: Genau die vier Richtungen zwischen {@code GESTELLT} und einem Ausgang
   * sind erlaubt; Stillstand, der direkte Weg zwischen den Ausgaengen und jeder Weg von oder zum
   * Entwurf nicht.
   */
  @ParameterizedTest(name = "{0} -> {1}: {2}")
  @CsvSource({
    "GESTELLT, BEZAHLT, true",
    "GESTELLT, ABGESCHRIEBEN, true",
    "BEZAHLT, GESTELLT, true",
    "ABGESCHRIEBEN, GESTELLT, true",
    "GESTELLT, GESTELLT, false",
    "BEZAHLT, BEZAHLT, false",
    "ABGESCHRIEBEN, ABGESCHRIEBEN, false",
    "BEZAHLT, ABGESCHRIEBEN, false",
    "ABGESCHRIEBEN, BEZAHLT, false",
    "ENTWURF, ENTWURF, false",
    "ENTWURF, GESTELLT, false",
    "ENTWURF, BEZAHLT, false",
    "ENTWURF, ABGESCHRIEBEN, false",
    "GESTELLT, ENTWURF, false",
    "BEZAHLT, ENTWURF, false",
    "ABGESCHRIEBEN, ENTWURF, false"
  })
  void ausgangswechselErlaubt_givenEveryPair_thenOnlyTheFourDirectionsViaGestellt(
      final Rechnungszustand von, final Rechnungszustand nach, final boolean erlaubt) {
    assertThat(Rechnungszustand.ausgangswechselErlaubt(von, nach)).isEqualTo(erlaubt);
  }

  @Test
  void values_thenFourStatesInTheOrderOfTheirCourse() {
    // Then — dieselbe Reihenfolge wie `RECHNUNGSZUSTAENDE` im Frontend.
    assertThat(Rechnungszustand.values())
        .containsExactly(
            Rechnungszustand.ENTWURF,
            Rechnungszustand.GESTELLT,
            Rechnungszustand.BEZAHLT,
            Rechnungszustand.ABGESCHRIEBEN);
  }
}
