package org.mwolff.fbcrm.rechnung.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.common.Einheit;

/**
 * Der Betrag einer Rechnungsposition.
 *
 * <p>Gerechnet und nicht gespeichert, nach derselben Regel wie am Angebot ({@code
 * common.Geldrechnung}): Menge mal Einzelpreis, kaufmaennisch auf den Cent. Eine Rechnung, die die
 * Summe ihres Angebots um einen Cent verfehlt, waere gegenueber dem Kunden nicht erklaerbar.
 */
class RechnungspositionTest {

  private static Rechnungsposition position(final String menge, final String einzelpreis) {
    return new Rechnungsposition(
        7L, "Konzeption", new BigDecimal(menge), Einheit.PERSONENTAG, new BigDecimal(einzelpreis));
  }

  @Test
  void betrag_givenAHalfCentProduct_thenRoundedUpToTheCent() {
    // Given — 2,5 mal 99,99 ergibt 249,975 und liegt damit genau auf der halben Einheit.
    final Rechnungsposition position = position("2.50", "99.99");

    // When
    final BigDecimal betrag = position.betrag();

    // Then
    assertThat(betrag).isEqualByComparingTo("249.98").hasScaleOf(2);
  }

  @Test
  void betrag_givenAWholeQuantity_thenTheExactProductWithTwoDecimals() {
    // Given
    final Rechnungsposition position = position("3.00", "120.00");

    // When
    final BigDecimal betrag = position.betrag();

    // Then
    assertThat(betrag).isEqualByComparingTo("360.00").hasScaleOf(2);
  }
}
