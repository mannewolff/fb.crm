package org.mwolff.fbcrm.angebot.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Year;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Einheit;

/**
 * Der Betrag einer Position — Kriterium 5.
 *
 * <p>Gegenstand ist die Rundung: Gerechnet wird Menge mal Einzelpreis, und das Ergebnis wird
 * kaufmaennisch auf den Cent gerundet. Das Paar aus dem Kriterium — 2,5 Personentage zu 1.000,01 €
 * — trifft genau die halbe Einheit und unterscheidet damit HALF_UP von HALF_EVEN: 2.500,03 € ist
 * richtig, 2.500,02 € waere es nicht.
 */
class AngebotspositionTest {

  private static Angebotsposition position(final String menge, final String einzelpreis) {
    return new Angebotsposition(
        null,
        "Konzeption",
        Abrechnungsmodus.AUFWAND,
        new BigDecimal(menge),
        Einheit.PERSONENTAG,
        new BigDecimal(einzelpreis),
        Year.of(2026));
  }

  @Test
  void betrag_givenTwoAndAHalfDaysAtAPriceEndingInOneCent_thenRoundsUpToTheCent() {
    // Given — Kriterium 5: 2,5 × 1.000,01 € = 2.500,025 €.
    final Angebotsposition position = position("2.50", "1000.01");

    // When
    final BigDecimal betrag = position.betrag();

    // Then
    assertThat(betrag).isEqualTo(new BigDecimal("2500.03"));
  }

  @Test
  void betrag_thenCarriesTwoDecimalPlaces() {
    // Given
    final Angebotsposition position = position("8.00", "95.00");

    // When
    final BigDecimal betrag = position.betrag();

    // Then
    assertThat(betrag).isEqualTo(new BigDecimal("760.00"));
  }

  @Test
  void betrag_givenAQuantityOfZero_thenZero() {
    // Given
    final Angebotsposition position = position("0.00", "1000.01");

    // When
    final BigDecimal betrag = position.betrag();

    // Then
    assertThat(betrag).isEqualTo(new BigDecimal("0.00"));
  }

  @Test
  void betrag_givenAUnitPriceOfZero_thenZero() {
    // Given — eine mitgelieferte Leistung ohne Preis ist eine gueltige Position.
    final Angebotsposition position = position("2.50", "0.00");

    // When
    final BigDecimal betrag = position.betrag();

    // Then
    assertThat(betrag).isEqualTo(new BigDecimal("0.00"));
  }

  @Test
  void leistungsjahr_thenCarriesTheYearOfTheServiceGivenAtConstruction() {
    // Given — #302, Kriterium 4: das Angebot an IT Bildungshaus vom Oktober 2026 hat Positionen,
    // deren Leistung 2027 erbracht wird.
    final Angebotsposition position =
        new Angebotsposition(
            null,
            "Aufwände Mai 2027",
            Abrechnungsmodus.AUFWAND,
            new BigDecimal("135.00"),
            Einheit.STUNDE,
            new BigDecimal("60.00"),
            Year.of(2027));

    // When
    final Year jahr = position.leistungsjahr();

    // Then
    assertThat(jahr).isEqualTo(Year.of(2027));
  }
}
