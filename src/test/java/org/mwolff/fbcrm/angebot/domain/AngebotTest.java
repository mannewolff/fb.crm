package org.mwolff.fbcrm.angebot.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mwolff.fbcrm.angebot.application.StatusGrenzeErreicht;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Einheit;

/**
 * Das Angebot als Fachobjekt: die Rechenregel der Summe, das Aendern in jedem Status und der
 * Statuswechsel um eine Stufe (Issue #127).
 *
 * <p>Die Summe ist die Summe der <b>gerundeten</b> Positionsbetraege und nicht die gerundete Summe
 * der ungerundeten — deshalb stehen unten zwei Positionen, deren Einzelrundungen sich zu einem
 * anderen Wert addieren als die Rundung ihrer Summe.
 */
class AngebotTest {

  private static final LocalDate ANGEBOTSDATUM = LocalDate.of(2026, 9, 20);
  private static final Instant ANGELEGT = Instant.parse("2026-09-20T08:00:00Z");
  private static final Instant JETZT = Instant.parse("2026-09-27T10:30:00Z");

  /** 2,5 × 1.000,01 € = 2.500,025 € und damit gerundet 2.500,03 €. */
  private static final Angebotsposition KONZEPTION =
      new Angebotsposition(
          null,
          "Konzeption",
          Abrechnungsmodus.AUFWAND,
          new BigDecimal("2.50"),
          Einheit.PERSONENTAG,
          new BigDecimal("1000.01"));

  /** 1,5 × 0,01 € = 0,015 € und damit gerundet 0,02 €. */
  private static final Angebotsposition KLEINKRAM =
      new Angebotsposition(
          null,
          "Kleinkram",
          Abrechnungsmodus.AUFWAND,
          new BigDecimal("1.50"),
          Einheit.STUNDE,
          new BigDecimal("0.01"));

  private static final Angebotsposition PAUSCHALE =
      new Angebotsposition(
          null,
          "Schulungstag",
          Abrechnungsmodus.FESTPREIS,
          BigDecimal.ONE,
          Einheit.PAUSCHAL,
          new BigDecimal("1200.00"));

  private static Angebot angebot(final Angebotsstatus status) {
    return angebot(status, List.of(KONZEPTION));
  }

  private static Angebot angebot(
      final Angebotsstatus status, final List<Angebotsposition> positionen) {
    return new Angebot(
        7L,
        3L,
        8L,
        status,
        ANGEBOTSDATUM,
        "Neugestaltung der Website",
        positionen,
        ANGELEGT,
        ANGELEGT);
  }

  @Test
  void summe_thenAddsTheAmountsRoundedPerPosition() {
    // Given — einzeln gerundet 2.500,03 € + 0,02 €; die Rundung der Summe ergaebe 2.500,04 €.
    final Angebot angebot = angebot(Angebotsstatus.ANGELEGT, List.of(KONZEPTION, KLEINKRAM));

    // When
    final BigDecimal summe = angebot.summe();

    // Then
    assertThat(summe).isEqualTo(new BigDecimal("2500.05"));
  }

  @Test
  void summe_givenNoPosition_thenZeroWithTwoDecimals() {
    // Given — ein frisch angelegtes Angebot hat noch keine Position.
    final Angebot angebot = angebot(Angebotsstatus.ANGELEGT, List.of());

    // When / Then
    assertThat(angebot.summe()).isEqualTo(new BigDecimal("0.00"));
  }

  @ParameterizedTest
  @EnumSource(Angebotsstatus.class)
  void geaendert_givenAnyStatus_thenCarriesTheNewValuesAndKeepsTheStatus(
      final Angebotsstatus status) {
    // Given — Kriterium 5: das Angebot bleibt in jedem Status aenderbar.
    final LocalDate neuesDatum = LocalDate.of(2026, 9, 25);

    // When
    final Angebot geaendert =
        angebot(status).geaendert(neuesDatum, 9L, "Betreuung", List.of(PAUSCHALE), JETZT);

    // Then
    assertThat(geaendert)
        .satisfies(
            a -> assertThat(a.status()).isEqualTo(status),
            a -> assertThat(a.angebotDatum()).isEqualTo(neuesDatum),
            a -> assertThat(a.ansprechpartnerId()).isEqualTo(9L),
            a -> assertThat(a.beschreibung()).isEqualTo("Betreuung"),
            a -> assertThat(a.positionen()).containsExactly(PAUSCHALE),
            a -> assertThat(a.updatedAt()).isEqualTo(JETZT),
            a -> assertThat(a.createdAt()).isEqualTo(ANGELEGT),
            a -> assertThat(a.firmaId()).isEqualTo(3L),
            a -> assertThat(a.id()).isEqualTo(7L));
  }

  @Test
  void geaendert_givenNoContactAndNoText_thenKeepsThemAbsent() {
    // When
    final Angebot geaendert =
        angebot(Angebotsstatus.ANGELEGT).geaendert(ANGEBOTSDATUM, null, null, List.of(), JETZT);

    // Then
    assertThat(geaendert)
        .satisfies(
            a -> assertThat(a.ansprechpartnerId()).isNull(),
            a -> assertThat(a.beschreibung()).isNull(),
            a -> assertThat(a.positionen()).isEmpty());
  }

  @Test
  void statusWeiter_givenAPlacedOffer_thenOneStepFurtherAndEverythingElseKept() {
    // When
    final Angebot weiter = angebot(Angebotsstatus.ABGEGEBEN).statusWeiter(JETZT);

    // Then
    assertThat(weiter)
        .satisfies(
            a -> assertThat(a.status()).isEqualTo(Angebotsstatus.BESTELLT),
            a -> assertThat(a.updatedAt()).isEqualTo(JETZT),
            a -> assertThat(a.createdAt()).isEqualTo(ANGELEGT),
            a -> assertThat(a.id()).isEqualTo(7L),
            a -> assertThat(a.firmaId()).isEqualTo(3L),
            a -> assertThat(a.ansprechpartnerId()).isEqualTo(8L),
            a -> assertThat(a.angebotDatum()).isEqualTo(ANGEBOTSDATUM),
            a -> assertThat(a.beschreibung()).isEqualTo("Neugestaltung der Website"),
            a -> assertThat(a.positionen()).containsExactly(KONZEPTION));
  }

  @Test
  void statusWeiter_givenABilledOffer_thenRejected() {
    // Given
    final Angebot angebot = angebot(Angebotsstatus.ABGERECHNET);

    // When / Then
    assertThatThrownBy(() -> angebot.statusWeiter(JETZT)).isInstanceOf(StatusGrenzeErreicht.class);
  }

  @Test
  void statusZurueck_givenADoneOffer_thenOneStepBack() {
    // When
    final Angebot zurueck = angebot(Angebotsstatus.ERLEDIGT).statusZurueck(JETZT);

    // Then
    assertThat(zurueck)
        .satisfies(
            a -> assertThat(a.status()).isEqualTo(Angebotsstatus.BESTELLT),
            a -> assertThat(a.updatedAt()).isEqualTo(JETZT));
  }

  @Test
  void statusZurueck_givenACreatedOffer_thenRejected() {
    // Given
    final Angebot angebot = angebot(Angebotsstatus.ANGELEGT);

    // When / Then
    assertThatThrownBy(() -> angebot.statusZurueck(JETZT)).isInstanceOf(StatusGrenzeErreicht.class);
  }

  @ParameterizedTest
  @EnumSource(value = Angebotsstatus.class, mode = EnumSource.Mode.EXCLUDE, names = "ABGERECHNET")
  void abgerechnet_givenAnyOtherStatus_thenJumpsStraightToAbgerechnet(final Angebotsstatus status) {
    // Given — #160, Kriterium 27: abgerechnet wird aus jedem Status erreicht, nicht Stufe fuer
    // Stufe.

    // When
    final Angebot abgerechnet = angebot(status).abgerechnet(JETZT);

    // Then
    assertThat(abgerechnet)
        .satisfies(
            a -> assertThat(a.status()).isEqualTo(Angebotsstatus.ABGERECHNET),
            a -> assertThat(a.updatedAt()).isEqualTo(JETZT),
            a -> assertThat(a.createdAt()).isEqualTo(ANGELEGT),
            a -> assertThat(a.id()).isEqualTo(7L),
            a -> assertThat(a.firmaId()).isEqualTo(3L),
            a -> assertThat(a.ansprechpartnerId()).isEqualTo(8L),
            a -> assertThat(a.angebotDatum()).isEqualTo(ANGEBOTSDATUM),
            a -> assertThat(a.beschreibung()).isEqualTo("Neugestaltung der Website"),
            a -> assertThat(a.positionen()).containsExactly(KONZEPTION));
  }

  @Test
  void abgerechnet_givenAnAlreadyBilledOffer_thenUnchanged() {
    // Given — es ist schon abgerechnet; der Zug ist ein Nichts und traegt darum auch keinen neuen
    // Zeitstempel ein.
    final Angebot angebot = angebot(Angebotsstatus.ABGERECHNET);

    // When
    final Angebot nochmal = angebot.abgerechnet(JETZT);

    // Then
    assertThat(nochmal).isEqualTo(angebot);
  }

  @Test
  void positionen_thenAreACopyAndNotTheHandedInList() {
    // Given — der Record ist unveraenderlich, auch wenn der Aufrufer seine Liste behaelt.
    final List<Angebotsposition> uebergeben = new ArrayList<>(List.of(KONZEPTION));
    final Angebot angebot = angebot(Angebotsstatus.ANGELEGT, uebergeben);

    // When
    uebergeben.clear();

    // Then
    assertThat(angebot.positionen()).containsExactly(KONZEPTION);
  }
}
