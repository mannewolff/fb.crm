package org.mwolff.fbcrm.firma.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Verhalten des Ansprechpartners: Aendern, Stilllegen und Wiederaktivieren. */
class AnsprechpartnerTest {

  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");
  private static final Instant GEAENDERT = Instant.parse("2026-09-18T12:00:00Z");

  private static Ansprechpartner ansprechpartner(final boolean aktiv) {
    return new Ansprechpartner(
        3L,
        7L,
        "Max",
        "Mustermann",
        "Einkauf",
        "max@firma.de",
        "0421 123456",
        "0170 123456",
        aktiv,
        ANGELEGT,
        ANGELEGT);
  }

  private static Ansprechpartner geaendert(final Ansprechpartner bestand) {
    return bestand.geaendert(
        "Erika",
        "Musterfrau",
        "Vertrieb",
        "erika@firma.de",
        "0421 999999",
        "0170 999999",
        GEAENDERT);
  }

  @Test
  void geaendert_thenCarriesTheNewValues() {
    // Given
    final Ansprechpartner bestand = ansprechpartner(true);

    // When
    final Ansprechpartner neu = geaendert(bestand);

    // Then
    assertThat(neu)
        .satisfies(
            wert -> assertThat(wert.vorname()).isEqualTo("Erika"),
            wert -> assertThat(wert.nachname()).isEqualTo("Musterfrau"),
            wert -> assertThat(wert.rolle()).isEqualTo("Vertrieb"),
            wert -> assertThat(wert.email()).isEqualTo("erika@firma.de"),
            wert -> assertThat(wert.telefonFestnetz()).isEqualTo("0421 999999"),
            wert -> assertThat(wert.telefonMobil()).isEqualTo("0170 999999"));
  }

  @Test
  void geaendert_givenEmptyOptionalValues_thenKeepsThemAbsent() {
    // Given
    final Ansprechpartner bestand = ansprechpartner(true);

    // When
    final Ansprechpartner neu =
        bestand.geaendert(null, "Musterfrau", null, null, null, null, GEAENDERT);

    // Then
    assertThat(neu)
        .satisfies(
            wert -> assertThat(wert.vorname()).isNull(),
            wert -> assertThat(wert.rolle()).isNull(),
            wert -> assertThat(wert.email()).isNull(),
            wert -> assertThat(wert.telefonFestnetz()).isNull(),
            wert -> assertThat(wert.telefonMobil()).isNull());
  }

  @Test
  void geaendert_thenStampsTheChangeTime() {
    // Given
    final Ansprechpartner bestand = ansprechpartner(true);

    // When
    final Ansprechpartner neu = geaendert(bestand);

    // Then
    assertThat(neu.updatedAt()).isEqualTo(GEAENDERT);
  }

  @Test
  void geaendert_thenLeavesIdentityCompanyStateAndCreationUntouched() {
    // Given
    final Ansprechpartner bestand = ansprechpartner(false);

    // When
    final Ansprechpartner neu = geaendert(bestand);

    // Then
    assertThat(neu)
        .satisfies(
            wert -> assertThat(wert.id()).isEqualTo(3L),
            wert -> assertThat(wert.firmaId()).isEqualTo(7L),
            wert -> assertThat(wert.aktiv()).isFalse(),
            wert -> assertThat(wert.createdAt()).isEqualTo(ANGELEGT));
  }

  @Test
  void geaendert_thenLeavesTheOriginalUntouched() {
    // Given
    final Ansprechpartner bestand = ansprechpartner(true);

    // When
    geaendert(bestand);

    // Then
    assertThat(bestand.nachname()).isEqualTo("Mustermann");
  }

  @Test
  void stillgelegt_thenSwitchesTheStateOff() {
    // Given
    final Ansprechpartner bestand = ansprechpartner(true);

    // When
    final Ansprechpartner stillgelegt = bestand.stillgelegt(GEAENDERT);

    // Then
    assertThat(stillgelegt.aktiv()).isFalse();
  }

  @Test
  void stillgelegt_thenStampsTheChangeTime() {
    // Given
    final Ansprechpartner bestand = ansprechpartner(true);

    // When
    final Ansprechpartner stillgelegt = bestand.stillgelegt(GEAENDERT);

    // Then
    assertThat(stillgelegt.updatedAt()).isEqualTo(GEAENDERT);
  }

  @Test
  void stillgelegt_thenLeavesEveryOtherValueUntouched() {
    // Given
    final Ansprechpartner bestand = ansprechpartner(true);

    // When
    final Ansprechpartner stillgelegt = bestand.stillgelegt(GEAENDERT);

    // Then
    assertThat(stillgelegt)
        .satisfies(
            wert -> assertThat(wert.id()).isEqualTo(3L),
            wert -> assertThat(wert.firmaId()).isEqualTo(7L),
            wert -> assertThat(wert.vorname()).isEqualTo("Max"),
            wert -> assertThat(wert.nachname()).isEqualTo("Mustermann"),
            wert -> assertThat(wert.rolle()).isEqualTo("Einkauf"),
            wert -> assertThat(wert.email()).isEqualTo("max@firma.de"),
            wert -> assertThat(wert.telefonFestnetz()).isEqualTo("0421 123456"),
            wert -> assertThat(wert.telefonMobil()).isEqualTo("0170 123456"),
            wert -> assertThat(wert.createdAt()).isEqualTo(ANGELEGT));
  }

  @Test
  void stillgelegt_givenAnAlreadyRetiredAnsprechpartner_thenStaysOff() {
    // Given
    final Ansprechpartner bestand = ansprechpartner(false);

    // When
    final Ansprechpartner stillgelegt = bestand.stillgelegt(GEAENDERT);

    // Then
    assertThat(stillgelegt.aktiv()).isFalse();
  }

  @Test
  void aktiviert_thenSwitchesTheStateOn() {
    // Given
    final Ansprechpartner bestand = ansprechpartner(false);

    // When
    final Ansprechpartner aktiviert = bestand.aktiviert(GEAENDERT);

    // Then
    assertThat(aktiviert.aktiv()).isTrue();
  }

  @Test
  void aktiviert_thenStampsTheChangeTime() {
    // Given
    final Ansprechpartner bestand = ansprechpartner(false);

    // When
    final Ansprechpartner aktiviert = bestand.aktiviert(GEAENDERT);

    // Then
    assertThat(aktiviert.updatedAt()).isEqualTo(GEAENDERT);
  }

  @Test
  void aktiviert_thenLeavesEveryOtherValueUntouched() {
    // Given
    final Ansprechpartner bestand = ansprechpartner(false);

    // When
    final Ansprechpartner aktiviert = bestand.aktiviert(GEAENDERT);

    // Then
    assertThat(aktiviert)
        .satisfies(
            wert -> assertThat(wert.id()).isEqualTo(3L),
            wert -> assertThat(wert.firmaId()).isEqualTo(7L),
            wert -> assertThat(wert.vorname()).isEqualTo("Max"),
            wert -> assertThat(wert.nachname()).isEqualTo("Mustermann"),
            wert -> assertThat(wert.rolle()).isEqualTo("Einkauf"),
            wert -> assertThat(wert.email()).isEqualTo("max@firma.de"),
            wert -> assertThat(wert.telefonFestnetz()).isEqualTo("0421 123456"),
            wert -> assertThat(wert.telefonMobil()).isEqualTo("0170 123456"),
            wert -> assertThat(wert.createdAt()).isEqualTo(ANGELEGT));
  }

  @Test
  void aktiviert_givenAnAlreadyActiveAnsprechpartner_thenStaysOn() {
    // Given
    final Ansprechpartner bestand = ansprechpartner(true);

    // When
    final Ansprechpartner aktiviert = bestand.aktiviert(GEAENDERT);

    // Then
    assertThat(aktiviert.aktiv()).isTrue();
  }
}
