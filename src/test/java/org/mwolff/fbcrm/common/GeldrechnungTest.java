package org.mwolff.fbcrm.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Die eine Geldrechnung, gegen die jeder Beleg seine Betraege rechnet (E5).
 *
 * <p>Der erste Fall ist der eigentliche Gegenstand: Kriterium 5 nennt ein Zahlenpaar, das genau auf
 * der halben Einheit liegt. Wer kaufmaennisch rundet, kommt auf 2.500,03 €; wer {@code HALF_EVEN}
 * nimmt, auf 2.500,02 € — und damit auf einen anderen Beleg als die Spezifikation.
 *
 * <p>Die Skala ist mitgeprueft, weil sie nach aussen sichtbar ist: {@code 0} und {@code 0.00} sind
 * fuer {@link BigDecimal#equals} zwei Werte, und die Schnittstelle zeigt den einen als „0" und den
 * anderen als „0,00".
 */
class GeldrechnungTest {

  @Test
  void betrag_givenValueExactlyOnTheHalfCent_thenRoundsUpAsCommerceDoes() {
    // When — Kriterium 5: 2,5 Personentage zu 1.000,01 € ergeben 2.500,025 €.
    final BigDecimal betrag = Geldrechnung.betrag(new BigDecimal("2.5"), new BigDecimal("1000.01"));

    // Then — HALF_EVEN gaebe hier 2500.02 und waere damit falsch.
    assertThat(betrag).isEqualTo(new BigDecimal("2500.03"));
  }

  @Test
  void betrag_givenQuantityZero_thenZeroWithTwoDecimals() {
    // When / Then
    assertThat(Geldrechnung.betrag(BigDecimal.ZERO, new BigDecimal("1000.01")))
        .isEqualTo(new BigDecimal("0.00"));
  }

  @Test
  void betrag_givenRoundValues_thenStillCarriesScaleTwo() {
    // When — zwei glatte Faktoren, deren Produkt keine Rundung braucht.
    final BigDecimal betrag = Geldrechnung.betrag(new BigDecimal("3"), new BigDecimal("100"));

    // Then — die Skala haengt nicht am Zufall der Eingabe.
    assertThat(betrag).isEqualTo(new BigDecimal("300.00"));
    assertThat(betrag.scale()).isEqualTo(2);
  }

  @Test
  void betrag_givenNegativeUnitPrice_thenReturnsNegativeWithoutComplaint() {
    // When / Then — die Rechnung prueft nicht, sie rechnet: Dass kein negativer Betrag entsteht,
    // haelt die Position mit ihren nicht negativen Faktoren zu, nicht diese Stelle.
    assertThat(Geldrechnung.betrag(new BigDecimal("2"), new BigDecimal("-10.00")))
        .isEqualTo(new BigDecimal("-20.00"));
  }

  @Test
  void summe_givenEmptyStream_thenZeroWithTwoDecimals() {
    // When / Then — die leere Summe steht als „0,00" in der Antwort, nicht als „0".
    assertThat(Geldrechnung.summe(Stream.of())).isEqualTo(new BigDecimal("0.00"));
  }

  @Test
  void summe_givenRoundedAmounts_thenAddsCentByCent() {
    // When — zwei bereits gerundete Betraege.
    final BigDecimal summe =
        Geldrechnung.summe(Stream.of(new BigDecimal("2500.03"), new BigDecimal("1000.01")));

    // Then — genau die Summe der Runden, ohne zweite Rundung darueber.
    assertThat(summe).isEqualTo(new BigDecimal("3500.04"));
  }

  @Test
  void summe_givenAmountsWithLongerScale_thenNormalisesToTwoDecimals() {
    // When — Werte, die mehr Nachkommastellen mitbringen, als ein Cent hat.
    final BigDecimal summe =
        Geldrechnung.summe(Stream.of(new BigDecimal("1.0000"), new BigDecimal("2.0000")));

    // Then
    assertThat(summe).isEqualTo(new BigDecimal("3.00"));
    assertThat(summe.scale()).isEqualTo(2);
  }
}
