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
 *
 * <p><b>Das Offene summiert mit</b> (Issue #284): Es ist ein Teil derselben Abrechnung und keine
 * zweite Karte daneben, und darum laeuft es durch dieselbe Addition wie Netto und Anzahl.
 */
class MonatsabrechnungTest {

  /** Nichts offen — der Wert, den eine bezahlte oder abgeschriebene Rechnung beitraegt. */
  private static final BigDecimal NICHTS = new BigDecimal("0.00");

  @Test
  void summe_withoutAnyMonat_thenZeroWithScaleTwoAndAnzahlZero() {
    // When
    final Monatsabrechnung summe = Monatsabrechnung.summe(Stream.empty());

    // Then — equals und nicht isEqualByComparingTo: Die Skala ist Teil der Zusage.
    assertThat(summe).isEqualTo(new Monatsabrechnung(NICHTS, NICHTS, 0, NICHTS, 0));
  }

  @Test
  void summe_withOneMonat_thenThatMonat() {
    // Given
    final Monatsabrechnung september =
        new Monatsabrechnung(
            new BigDecimal("1000.00"), new BigDecimal("1190.00"), 2, new BigDecimal("400.00"), 1);

    // When / Then
    assertThat(Monatsabrechnung.summe(Stream.of(september))).isEqualTo(september);
  }

  @Test
  void summe_withSeveralMonate_thenNettoBruttoAndAnzahlAreAddedUp() {
    // Given — drei Monate, einer davon mit einem Cent-Betrag.
    final Stream<Monatsabrechnung> monate =
        Stream.of(
            new Monatsabrechnung(
                new BigDecimal("1000.00"), new BigDecimal("1190.00"), 2, NICHTS, 0),
            new Monatsabrechnung(new BigDecimal("0.50"), new BigDecimal("0.54"), 1, NICHTS, 0),
            new Monatsabrechnung(
                new BigDecimal("2000.00"), new BigDecimal("2140.00"), 3, NICHTS, 0));

    // When
    final Monatsabrechnung summe = Monatsabrechnung.summe(monate);

    // Then
    assertThat(summe)
        .isEqualTo(
            new Monatsabrechnung(
                new BigDecimal("3000.50"), new BigDecimal("3330.54"), 6, NICHTS, 0));
  }

  @Test
  void summe_withOffenesInSeveralMonate_thenTheOffeneBetraegeAndCountsAreAddedUpToo() {
    // Given — drei Monate, in zweien davon steht etwas offen (Issue #284).
    final Stream<Monatsabrechnung> monate =
        Stream.of(
            new Monatsabrechnung(
                new BigDecimal("1000.00"), new BigDecimal("1190.00"), 2, new BigDecimal("0.50"), 1),
            new Monatsabrechnung(
                new BigDecimal("2000.00"), new BigDecimal("2380.00"), 1, NICHTS, 0),
            new Monatsabrechnung(
                new BigDecimal("360.00"),
                new BigDecimal("428.40"),
                1,
                new BigDecimal("360.00"),
                1));

    // When
    final Monatsabrechnung summe = Monatsabrechnung.summe(monate);

    // Then — 0,50 + 360,00 offen aus zwei Rechnungen; Netto und Anzahl bleiben die aller vier.
    assertThat(summe)
        .isEqualTo(
            new Monatsabrechnung(
                new BigDecimal("3360.00"),
                new BigDecimal("3998.40"),
                4,
                new BigDecimal("360.50"),
                2));
  }

  @Test
  void summe_withOffenesInOnlyOneMonat_thenTheOthersContributeNothingToIt() {
    // Given — ein Monat mit Offenem, einer ohne; nur so faellt ein vergessener Summand auf.
    final Stream<Monatsabrechnung> monate =
        Stream.of(
            new Monatsabrechnung(
                new BigDecimal("500.00"), new BigDecimal("595.00"), 1, new BigDecimal("500.00"), 1),
            new Monatsabrechnung(new BigDecimal("500.00"), new BigDecimal("595.00"), 1, NICHTS, 0));

    // When
    final Monatsabrechnung summe = Monatsabrechnung.summe(monate);

    // Then
    assertThat(summe.offenNetto()).isEqualByComparingTo("500.00");
    assertThat(summe.offenAnzahl()).isEqualTo(1);
  }
}
