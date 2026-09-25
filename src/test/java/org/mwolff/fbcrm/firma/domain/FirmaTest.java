package org.mwolff.fbcrm.firma.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Verhalten der Firma: Aendern, Stilllegen und Wiederaktivieren. */
class FirmaTest {

  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");
  private static final Instant GEAENDERT = Instant.parse("2026-09-18T12:00:00Z");

  private static final Anschrift BREMEN =
      new Anschrift("Am Wall 1", "28195", "Bremen", "Deutschland");
  private static final Anschrift HAMBURG =
      new Anschrift("Moenckebergstrasse 2", "20095", "Hamburg", "Deutschland");

  private static Firma firma(final boolean aktiv) {
    return new Firma(
        7L, "Adler AG", BREMEN, "75/123/45678", "DE123456789", aktiv, ANGELEGT, ANGELEGT);
  }

  @Test
  void geaendert_thenCarriesTheNewValues() {
    // Given
    final Firma bestand = firma(true);

    // When
    final Firma geaendert =
        bestand.geaendert("Adler GmbH", HAMBURG, "75/999/00000", "DE999999999", GEAENDERT);

    // Then
    assertThat(geaendert)
        .satisfies(
            neu -> assertThat(neu.name()).isEqualTo("Adler GmbH"),
            neu -> assertThat(neu.anschrift()).isEqualTo(HAMBURG),
            neu -> assertThat(neu.steuernummer()).isEqualTo("75/999/00000"),
            neu -> assertThat(neu.umsatzsteuerId()).isEqualTo("DE999999999"));
  }

  @Test
  void geaendert_givenEmptyOptionalValues_thenKeepsThemAbsent() {
    // Given
    final Firma bestand = firma(true);

    // When
    final Firma geaendert =
        bestand.geaendert(
            "Adler GmbH", new Anschrift(null, null, null, null), null, null, GEAENDERT);

    // Then
    assertThat(geaendert)
        .satisfies(
            neu -> assertThat(neu.anschrift().ort()).isNull(),
            neu -> assertThat(neu.steuernummer()).isNull(),
            neu -> assertThat(neu.umsatzsteuerId()).isNull());
  }

  @Test
  void geaendert_thenStampsTheChangeTime() {
    // Given
    final Firma bestand = firma(true);

    // When
    final Firma geaendert =
        bestand.geaendert("Adler GmbH", HAMBURG, "75/999/00000", "DE999999999", GEAENDERT);

    // Then
    assertThat(geaendert.updatedAt()).isEqualTo(GEAENDERT);
  }

  @Test
  void geaendert_thenLeavesIdentityStateAndCreationUntouched() {
    // Given
    final Firma bestand = firma(false);

    // When
    final Firma geaendert =
        bestand.geaendert("Adler GmbH", HAMBURG, "75/999/00000", "DE999999999", GEAENDERT);

    // Then
    assertThat(geaendert)
        .satisfies(
            neu -> assertThat(neu.id()).isEqualTo(7L),
            neu -> assertThat(neu.aktiv()).isFalse(),
            neu -> assertThat(neu.createdAt()).isEqualTo(ANGELEGT));
  }

  @Test
  void geaendert_thenLeavesTheOriginalUntouched() {
    // Given
    final Firma bestand = firma(true);

    // When
    bestand.geaendert("Adler GmbH", HAMBURG, "75/999/00000", "DE999999999", GEAENDERT);

    // Then
    assertThat(bestand.name()).isEqualTo("Adler AG");
  }

  @Test
  void stillgelegt_thenSwitchesTheStateOff() {
    // Given
    final Firma bestand = firma(true);

    // When
    final Firma stillgelegt = bestand.stillgelegt(GEAENDERT);

    // Then
    assertThat(stillgelegt.aktiv()).isFalse();
  }

  @Test
  void stillgelegt_thenStampsTheChangeTime() {
    // Given
    final Firma bestand = firma(true);

    // When
    final Firma stillgelegt = bestand.stillgelegt(GEAENDERT);

    // Then
    assertThat(stillgelegt.updatedAt()).isEqualTo(GEAENDERT);
  }

  @Test
  void stillgelegt_thenLeavesEveryOtherValueUntouched() {
    // Given
    final Firma bestand = firma(true);

    // When
    final Firma stillgelegt = bestand.stillgelegt(GEAENDERT);

    // Then
    assertThat(stillgelegt)
        .satisfies(
            neu -> assertThat(neu.id()).isEqualTo(7L),
            neu -> assertThat(neu.name()).isEqualTo("Adler AG"),
            neu -> assertThat(neu.anschrift()).isEqualTo(BREMEN),
            neu -> assertThat(neu.steuernummer()).isEqualTo("75/123/45678"),
            neu -> assertThat(neu.umsatzsteuerId()).isEqualTo("DE123456789"),
            neu -> assertThat(neu.createdAt()).isEqualTo(ANGELEGT));
  }

  @Test
  void stillgelegt_givenAnAlreadyRetiredFirma_thenStaysOff() {
    // Given
    final Firma bestand = firma(false);

    // When
    final Firma stillgelegt = bestand.stillgelegt(GEAENDERT);

    // Then
    assertThat(stillgelegt.aktiv()).isFalse();
  }

  @Test
  void aktiviert_thenSwitchesTheStateOn() {
    // Given
    final Firma bestand = firma(false);

    // When
    final Firma aktiviert = bestand.aktiviert(GEAENDERT);

    // Then
    assertThat(aktiviert.aktiv()).isTrue();
  }

  @Test
  void aktiviert_thenStampsTheChangeTime() {
    // Given
    final Firma bestand = firma(false);

    // When
    final Firma aktiviert = bestand.aktiviert(GEAENDERT);

    // Then
    assertThat(aktiviert.updatedAt()).isEqualTo(GEAENDERT);
  }

  @Test
  void aktiviert_thenLeavesEveryOtherValueUntouched() {
    // Given
    final Firma bestand = firma(false);

    // When
    final Firma aktiviert = bestand.aktiviert(GEAENDERT);

    // Then
    assertThat(aktiviert)
        .satisfies(
            neu -> assertThat(neu.id()).isEqualTo(7L),
            neu -> assertThat(neu.name()).isEqualTo("Adler AG"),
            neu -> assertThat(neu.anschrift()).isEqualTo(BREMEN),
            neu -> assertThat(neu.steuernummer()).isEqualTo("75/123/45678"),
            neu -> assertThat(neu.umsatzsteuerId()).isEqualTo("DE123456789"),
            neu -> assertThat(neu.createdAt()).isEqualTo(ANGELEGT));
  }

  @Test
  void aktiviert_givenAnAlreadyActiveFirma_thenStaysOn() {
    // Given
    final Firma bestand = firma(true);

    // When
    final Firma aktiviert = bestand.aktiviert(GEAENDERT);

    // Then
    assertThat(aktiviert.aktiv()).isTrue();
  }
}
