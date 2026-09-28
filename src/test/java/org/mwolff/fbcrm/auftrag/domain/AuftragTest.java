package org.mwolff.fbcrm.auftrag.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Einheit;

/**
 * Der Auftrag als Fachobjekt — Kriterien 3, 5 und 7.
 *
 * <p>Zwei Gegenstaende: die Summe, die Cent fuer Cent aus den <b>gerundeten</b> Positionsbetraegen
 * entsteht (E11 — gespeichert wird sie nicht), und der eine unveraenderliche Uebergang {@link
 * Auftrag#gepflegt} fuer genau die vier Angaben, die Kriterium 7 freigibt. Was er nicht anfasst,
 * ist genauso wichtig: Nummer, Angebot, Vorgang und Positionen stehen ab der Anlage fest (F3).
 */
class AuftragTest {

  private static final LocalDate AUFTRAGSDATUM = LocalDate.of(2026, 9, 20);
  private static final Instant ANGELEGT = Instant.parse("2026-09-20T08:00:00Z");
  private static final Instant GEAENDERT = Instant.parse("2026-09-27T10:30:00Z");
  private static final String NUMMER = "AU-2026-001";

  private static final Auftragsposition KONZEPTION =
      new Auftragsposition(
          "Konzeption",
          Abrechnungsmodus.AUFWAND,
          new BigDecimal("2.50"),
          Einheit.PERSONENTAG,
          new BigDecimal("1000.01"),
          new BigDecimal("8.00"));

  private static final Auftragsposition SCHULUNG =
      new Auftragsposition(
          "Schulungstag",
          Abrechnungsmodus.FESTPREIS,
          BigDecimal.ONE,
          Einheit.PAUSCHAL,
          new BigDecimal("1200.00"),
          null);

  private static Auftrag auftrag(final List<Auftragsposition> positionen) {
    return new Auftrag(
        7L,
        3L,
        11L,
        NUMMER,
        Auftragsstatus.OFFEN,
        AUFTRAGSDATUM,
        "BST-4711",
        LocalDate.of(2026, 10, 1),
        LocalDate.of(2026, 12, 31),
        positionen,
        ANGELEGT,
        ANGELEGT);
  }

  @Test
  void summe_thenAddsTheRoundedPositionAmounts() {
    // Given — 2.500,03 € + 1.200,00 €; gerundet wird je Position, dann addiert (Kriterium 5).
    final Auftrag auftrag = auftrag(List.of(KONZEPTION, SCHULUNG));

    // When
    final BigDecimal summe = auftrag.summe();

    // Then
    assertThat(summe).isEqualTo(new BigDecimal("3700.03"));
  }

  @Test
  void summe_givenTwoPositionsRoundingUpEach_thenCountsBothCents() {
    // Given — die Summe der gerundeten Betraege, nicht die Rundung der Summe: 2 × 2.500,025 €
    // ergibt 5.000,06 € und nicht 5.000,05 €.
    final Auftrag auftrag = auftrag(List.of(KONZEPTION, KONZEPTION));

    // When
    final BigDecimal summe = auftrag.summe();

    // Then
    assertThat(summe).isEqualTo(new BigDecimal("5000.06"));
  }

  @Test
  void summe_givenAnOrderWithoutPositions_thenZeroWithTwoDecimalPlaces() {
    // Given
    final Auftrag auftrag = auftrag(List.of());

    // When
    final BigDecimal summe = auftrag.summe();

    // Then
    assertThat(summe).isEqualTo(new BigDecimal("0.00"));
  }

  @Test
  void positionen_givenACallerChangingItsListAfterwards_thenTheOrderKeepsItsOwnCopy() {
    // Given
    final List<Auftragsposition> veraenderlich = new ArrayList<>(List.of(KONZEPTION));
    final Auftrag auftrag = auftrag(veraenderlich);

    // When
    veraenderlich.add(SCHULUNG);

    // Then
    assertThat(auftrag.positionen()).containsExactly(KONZEPTION);
  }

  @Test
  void gepflegt_thenReturnsANewInstanceWithTheFourChangedDetails() {
    // Given — Kriterium 7: Auftragsdatum, Kundenbestellnummer, Leistungszeitraum und Status.
    final Auftrag auftrag = auftrag(List.of(KONZEPTION));

    // When
    final Auftrag gepflegt =
        auftrag.gepflegt(
            LocalDate.of(2026, 9, 22),
            "BST-0815",
            LocalDate.of(2026, 11, 1),
            LocalDate.of(2027, 1, 31),
            Auftragsstatus.IN_ARBEIT,
            GEAENDERT);

    // Then
    assertThat(gepflegt)
        .isNotSameAs(auftrag)
        .satisfies(
            neu -> assertThat(neu.auftragDatum()).isEqualTo(LocalDate.of(2026, 9, 22)),
            neu -> assertThat(neu.kundenbestellnummer()).isEqualTo("BST-0815"),
            neu -> assertThat(neu.leistungAb()).isEqualTo(LocalDate.of(2026, 11, 1)),
            neu -> assertThat(neu.leistungBis()).isEqualTo(LocalDate.of(2027, 1, 31)),
            neu -> assertThat(neu.status()).isEqualTo(Auftragsstatus.IN_ARBEIT),
            neu -> assertThat(neu.updatedAt()).isEqualTo(GEAENDERT));
  }

  @Test
  void gepflegt_thenLeavesTheOrderItWasCalledOnUntouched() {
    // Given — unveraenderlich: jeder Uebergang liefert ein neues Objekt.
    final Auftrag auftrag = auftrag(List.of(KONZEPTION));

    // When
    auftrag.gepflegt(
        LocalDate.of(2026, 9, 22), null, null, null, Auftragsstatus.ABGESCHLOSSEN, GEAENDERT);

    // Then
    assertThat(auftrag)
        .satisfies(
            alt -> assertThat(alt.auftragDatum()).isEqualTo(AUFTRAGSDATUM),
            alt -> assertThat(alt.status()).isEqualTo(Auftragsstatus.OFFEN),
            alt -> assertThat(alt.updatedAt()).isEqualTo(ANGELEGT));
  }

  @Test
  void gepflegt_thenKeepsNumberOfferVorgangPositionsAndTheMomentOfCreation() {
    // Given — F3: die Positionen stehen ab der Anlage fest, und die Nummer wird nicht nachgerueckt.
    final Auftrag auftrag = auftrag(List.of(KONZEPTION, SCHULUNG));

    // When
    final Auftrag gepflegt =
        auftrag.gepflegt(AUFTRAGSDATUM, null, null, null, Auftragsstatus.ABGESCHLOSSEN, GEAENDERT);

    // Then
    assertThat(gepflegt)
        .satisfies(
            neu -> assertThat(neu.id()).isEqualTo(7L),
            neu -> assertThat(neu.vorgangId()).isEqualTo(3L),
            neu -> assertThat(neu.angebotId()).isEqualTo(11L),
            neu -> assertThat(neu.nummer()).isEqualTo(NUMMER),
            neu -> assertThat(neu.positionen()).containsExactly(KONZEPTION, SCHULUNG),
            neu -> assertThat(neu.createdAt()).isEqualTo(ANGELEGT));
  }

  @Test
  void gepflegt_givenAnEmptiedServicePeriodAndOrderNumber_thenClearsThem() {
    // Given — Kriterium 3: der Leistungszeitraum ist freiwillig und darf wieder verschwinden.
    final Auftrag auftrag = auftrag(List.of(KONZEPTION));

    // When
    final Auftrag gepflegt =
        auftrag.gepflegt(AUFTRAGSDATUM, null, null, null, Auftragsstatus.OFFEN, GEAENDERT);

    // Then
    assertThat(gepflegt)
        .satisfies(
            neu -> assertThat(neu.kundenbestellnummer()).isNull(),
            neu -> assertThat(neu.leistungAb()).isNull(),
            neu -> assertThat(neu.leistungBis()).isNull());
  }

  @Test
  void requireId_givenAnUnsavedOrder_thenFails() {
    // Given
    final Auftrag ohneId =
        new Auftrag(
            null,
            3L,
            11L,
            NUMMER,
            Auftragsstatus.OFFEN,
            AUFTRAGSDATUM,
            null,
            null,
            null,
            List.of(),
            ANGELEGT,
            ANGELEGT);

    // When / Then
    assertThat(ohneId.id()).isNull();
    assertThat(auftrag(List.of()).requireId()).isEqualTo(7L);
  }
}
