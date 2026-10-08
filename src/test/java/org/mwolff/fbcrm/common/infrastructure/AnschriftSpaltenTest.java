package org.mwolff.fbcrm.common.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.common.Anschrift;

/**
 * Der Hin- und Rueckweg zwischen {@link Anschrift} und den vier Anschriftenspalten einer Zeile
 * (Issue #242; Plan #238, A6).
 *
 * <p>Gegenstand ist die Abbildung allein. Sie liegt nur noch an einer Stelle, seit {@code
 * FirmaEntity} und {@code EigeneAngabenEntity} dieselbe eingebettete Klasse benutzen; eine
 * vertauschte Zuweisung wuerde damit beide Tabellen treffen und waere nur hier zu sehen.
 */
class AnschriftSpaltenTest {

  private static final Anschrift AM_WALL =
      new Anschrift("Am Wall 1", "28195", "Bremen", "Deutschland");

  @Test
  void aus_thenEveryFieldOfTheAnschriftArrivesAtItsColumn() {
    // When
    final AnschriftSpalten spalten = AnschriftSpalten.aus(AM_WALL);

    // Then — der Rueckweg nennt jedes Feld einzeln, damit ein Tausch auffaellt.
    assertThat(spalten.alsAnschrift())
        .satisfies(
            gelesen -> assertThat(gelesen.strasse()).isEqualTo("Am Wall 1"),
            gelesen -> assertThat(gelesen.plz()).isEqualTo("28195"),
            gelesen -> assertThat(gelesen.ort()).isEqualTo("Bremen"),
            gelesen -> assertThat(gelesen.land()).isEqualTo("Deutschland"));
  }

  @Test
  void aus_givenAnAnschriftWithoutAnyValue_thenEveryColumnStaysAbsent() {
    // Given — eine Firma wird oft mit nichts als ihrem Namen angelegt (E9).
    final Anschrift leer = new Anschrift(null, null, null, null);

    // When
    final AnschriftSpalten spalten = AnschriftSpalten.aus(leer);

    // Then
    assertThat(spalten.alsAnschrift()).isEqualTo(leer);
  }

  @Test
  void anschriftAus_givenColumns_thenReadsThemBack() {
    // When / Then
    assertThat(AnschriftSpalten.anschriftAus(AnschriftSpalten.aus(AM_WALL))).isEqualTo(AM_WALL);
  }

  /**
   * Die fehlende eingebettete Klasse ist die leere Anschrift und nicht {@code null}.
   *
   * <p>Hibernate laesst das eingebettete Objekt weg, wenn jede seiner Spalten {@code null} ist —
   * genau der Fall der Firma, die nur ihren Namen hat. Ohne diesen Weg flogen beim Lesen einer
   * solchen Zeile die Adapter.
   */
  @Test
  void anschriftAus_givenNoColumnsAtAll_thenTheEmptyAnschrift() {
    // When / Then
    assertThat(AnschriftSpalten.anschriftAus(null))
        .isEqualTo(new Anschrift(null, null, null, null));
  }
}
