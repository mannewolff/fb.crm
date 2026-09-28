package org.mwolff.fbcrm.auftrag.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Einheit;

/**
 * Der Betrag einer Auftragsposition — Kriterium 5.
 *
 * <p>Gerechnet wird mit derselben Regel wie am Angebot (E5): Menge mal Einzelpreis, kaufmaennisch
 * auf den Cent. Das Paar aus dem Kriterium — 2,5 Personentage zu 1.000,01 € — trifft genau die
 * halbe Einheit und unterscheidet damit HALF_UP von HALF_EVEN: 2.500,03 € ist richtig, 2.500,02 €
 * waere es nicht. Traefe der Auftrag die Summe seines Angebots um einen Cent nicht, waere das
 * gegenueber dem Kunden nicht erklaerbar.
 */
class AuftragspositionTest {

  private static Auftragsposition nachAufwand(final String menge, final String einzelpreis) {
    return new Auftragsposition(
        "Konzeption",
        Abrechnungsmodus.AUFWAND,
        new BigDecimal(menge),
        Einheit.PERSONENTAG,
        new BigDecimal(einzelpreis),
        new BigDecimal("8.00"));
  }

  @Test
  void betrag_givenTwoAndAHalfDaysAtAPriceEndingInOneCent_thenRoundsUpToTheCent() {
    // Given — Kriterium 5: 2,5 × 1.000,01 € = 2.500,025 €.
    final Auftragsposition position = nachAufwand("2.50", "1000.01");

    // When
    final BigDecimal betrag = position.betrag();

    // Then
    assertThat(betrag).isEqualTo(new BigDecimal("2500.03"));
  }

  @Test
  void betrag_thenCarriesTwoDecimalPlaces() {
    // Given
    final Auftragsposition position = nachAufwand("8.00", "95.00");

    // When
    final BigDecimal betrag = position.betrag();

    // Then
    assertThat(betrag).isEqualTo(new BigDecimal("760.00"));
  }

  @Test
  void betrag_givenAQuantityOfZero_thenZero() {
    // Given
    final Auftragsposition position = nachAufwand("0.00", "1000.01");

    // When
    final BigDecimal betrag = position.betrag();

    // Then
    assertThat(betrag).isEqualTo(new BigDecimal("0.00"));
  }

  @Test
  void stundenJePersonentag_givenAFixedPricePosition_thenMayBeEmpty() {
    // Given — E10: der Faktor haengt am Abrechnungsmodus und fehlt beim Festpreis.
    final Auftragsposition position =
        new Auftragsposition(
            "Schulungstag",
            Abrechnungsmodus.FESTPREIS,
            BigDecimal.ONE,
            Einheit.PAUSCHAL,
            new BigDecimal("1200.00"),
            null);

    // When / Then
    assertThat(position.stundenJePersonentag()).isNull();
    assertThat(position.betrag()).isEqualTo(new BigDecimal("1200.00"));
  }

  @Test
  void stundenJePersonentag_givenAHalfHour_thenKeepsTheTwoDecimalPlaces() {
    // Given — E10: 7,5 Stunden je Personentag ist verbreitet und waere als ganze Zahl nicht
    // abbildbar.
    final Auftragsposition position =
        new Auftragsposition(
            "Betreuung",
            Abrechnungsmodus.AUFWAND,
            new BigDecimal("8.00"),
            Einheit.STUNDE,
            new BigDecimal("95.00"),
            new BigDecimal("7.50"));

    // When / Then
    assertThat(position.stundenJePersonentag()).isEqualTo(new BigDecimal("7.50"));
  }
}
