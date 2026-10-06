package org.mwolff.fbcrm.rechnung.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Die Summe mehrerer Monatsabrechnungen — die Jahressumme (Plan #274, E5).
 *
 * <p>Sie ist die Summe der Monatssummen und keine zweite Rechnung aus den einzelnen Rechnungen:
 * Jeder Betrag ist schon je Rechnung auf den Cent gerundet, und die Addition gerundeter Werte ist
 * in jeder Gruppierung dieselbe Zahl. Ueber keinen Monat summiert, ergibt sie die Betraege mit
 * Skala 2 und die Anzahl 0 — so, wie {@code Geldrechnung.summe} es zusagt.
 */
class MonatsabrechnungTest {

  @Test
  void summe_withoutAnyMonat_thenZeroWithScaleTwoAndAnzahlZero() {
    // When
    final Monatsabrechnung summe = Monatsabrechnung.summe(Stream.empty());

    // Then — equals und nicht isEqualByComparingTo: Die Skala ist Teil der Zusage.
    assertThat(summe)
        .isEqualTo(new Monatsabrechnung(new BigDecimal("0.00"), new BigDecimal("0.00"), 0));
  }

  @Test
  void summe_withOneMonat_thenThatMonat() {
    // Given
    final Monatsabrechnung september =
        new Monatsabrechnung(new BigDecimal("1000.00"), new BigDecimal("1190.00"), 2);

    // When / Then
    assertThat(Monatsabrechnung.summe(Stream.of(september))).isEqualTo(september);
  }

  @Test
  void summe_withSeveralMonate_thenNettoBruttoAndAnzahlAreAddedUp() {
    // Given — drei Monate, einer davon mit einem Cent-Betrag.
    final Stream<Monatsabrechnung> monate =
        Stream.of(
            new Monatsabrechnung(new BigDecimal("1000.00"), new BigDecimal("1190.00"), 2),
            new Monatsabrechnung(new BigDecimal("0.50"), new BigDecimal("0.54"), 1),
            new Monatsabrechnung(new BigDecimal("2000.00"), new BigDecimal("2140.00"), 3));

    // When
    final Monatsabrechnung summe = Monatsabrechnung.summe(monate);

    // Then
    assertThat(summe)
        .isEqualTo(new Monatsabrechnung(new BigDecimal("3000.50"), new BigDecimal("3330.54"), 6));
  }
}
