package org.mwolff.fbcrm.rechnung.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Die vier Zustaende einer Rechnung und die eine Frage, die sie beantworten (Issue #253).
 *
 * <p>Gegenstand ist {@link Rechnungszustand#istGestellt()}: Bezahlt und Abgeschrieben sind
 * weiterhin gestellte Rechnungen — sie behalten Nummer, Dokument und ihre abgerechneten Mengen, und
 * jede Pruefung, die bisher auf {@code == GESTELLT} stand, fragt diese Methode. Ohne sie gaeben
 * bezahlte Rechnungen ihre Positionsmengen frei und verschwaenden aus der Monatsabrechnung.
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
