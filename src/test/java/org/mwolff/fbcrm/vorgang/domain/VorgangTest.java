package org.mwolff.fbcrm.vorgang.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Verhalten des Vorgangs: abgeleitete Phase, Aendern, Abschliessen und Wiedereroeffnen. */
class VorgangTest {

  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");
  private static final Instant GEAENDERT = Instant.parse("2026-09-18T12:00:00Z");

  private static Vorgang vorgang(final boolean abgeschlossen) {
    return new Vorgang(7L, 12L, "Website-Relaunch", 3L, 5L, abgeschlossen, ANGELEGT, ANGELEGT);
  }

  @Test
  void phase_givenAVorgangWithoutDocuments_thenAnbahnung() {
    // Given
    final Vorgang bestand = vorgang(false);

    // When
    final Phase phase = bestand.phase();

    // Then
    assertThat(phase).isEqualTo(Phase.ANBAHNUNG);
  }

  @Test
  void phase_givenAClosedVorgang_thenStillAnbahnung() {
    // Given
    final Vorgang bestand = vorgang(true);

    // When
    final Phase phase = bestand.phase();

    // Then
    assertThat(phase).isEqualTo(Phase.ANBAHNUNG);
  }

  @Test
  void geaendert_thenCarriesTheNewValues() {
    // Given
    final Vorgang bestand = vorgang(false);

    // When
    final Vorgang geaendert = bestand.geaendert("Neuer Titel", 4L, 9L, GEAENDERT);

    // Then
    assertThat(geaendert)
        .satisfies(
            neu -> assertThat(neu.titel()).isEqualTo("Neuer Titel"),
            neu -> assertThat(neu.firmaId()).isEqualTo(4L),
            neu -> assertThat(neu.ansprechpartnerId()).isEqualTo(9L));
  }

  @Test
  void geaendert_givenNoAnsprechpartner_thenKeepsItAbsent() {
    // Given
    final Vorgang bestand = vorgang(false);

    // When
    final Vorgang geaendert = bestand.geaendert("Neuer Titel", 4L, null, GEAENDERT);

    // Then
    assertThat(geaendert.ansprechpartnerId()).isNull();
  }

  @Test
  void geaendert_thenStampsTheChangeTime() {
    // Given
    final Vorgang bestand = vorgang(false);

    // When
    final Vorgang geaendert = bestand.geaendert("Neuer Titel", 4L, 9L, GEAENDERT);

    // Then
    assertThat(geaendert.updatedAt()).isEqualTo(GEAENDERT);
  }

  @Test
  void geaendert_thenLeavesIdentityNumberStateAndCreationUntouched() {
    // Given
    final Vorgang bestand = vorgang(true);

    // When
    final Vorgang geaendert = bestand.geaendert("Neuer Titel", 4L, 9L, GEAENDERT);

    // Then
    assertThat(geaendert)
        .satisfies(
            neu -> assertThat(neu.id()).isEqualTo(7L),
            neu -> assertThat(neu.nummer()).isEqualTo(12L),
            neu -> assertThat(neu.abgeschlossen()).isTrue(),
            neu -> assertThat(neu.createdAt()).isEqualTo(ANGELEGT));
  }

  @Test
  void geaendert_thenLeavesTheOriginalUntouched() {
    // Given
    final Vorgang bestand = vorgang(false);

    // When
    bestand.geaendert("Neuer Titel", 4L, 9L, GEAENDERT);

    // Then
    assertThat(bestand.titel()).isEqualTo("Website-Relaunch");
  }

  @Test
  void abgeschlossen_thenSwitchesTheStateOn() {
    // Given
    final Vorgang bestand = vorgang(false);

    // When
    final Vorgang abgeschlossen = bestand.abgeschlossen(GEAENDERT);

    // Then
    assertThat(abgeschlossen.abgeschlossen()).isTrue();
  }

  @Test
  void abgeschlossen_thenStampsTheChangeTime() {
    // Given
    final Vorgang bestand = vorgang(false);

    // When
    final Vorgang abgeschlossen = bestand.abgeschlossen(GEAENDERT);

    // Then
    assertThat(abgeschlossen.updatedAt()).isEqualTo(GEAENDERT);
  }

  @Test
  void abgeschlossen_thenLeavesEveryOtherValueUntouched() {
    // Given
    final Vorgang bestand = vorgang(false);

    // When
    final Vorgang abgeschlossen = bestand.abgeschlossen(GEAENDERT);

    // Then
    assertThat(abgeschlossen)
        .satisfies(
            neu -> assertThat(neu.id()).isEqualTo(7L),
            neu -> assertThat(neu.nummer()).isEqualTo(12L),
            neu -> assertThat(neu.titel()).isEqualTo("Website-Relaunch"),
            neu -> assertThat(neu.firmaId()).isEqualTo(3L),
            neu -> assertThat(neu.ansprechpartnerId()).isEqualTo(5L),
            neu -> assertThat(neu.createdAt()).isEqualTo(ANGELEGT));
  }

  @Test
  void abgeschlossen_thenLeavesTheOriginalUntouched() {
    // Given
    final Vorgang bestand = vorgang(false);

    // When
    bestand.abgeschlossen(GEAENDERT);

    // Then
    assertThat(bestand.abgeschlossen()).isFalse();
  }

  @Test
  void abgeschlossen_givenAnAlreadyClosedVorgang_thenStaysClosed() {
    // Given
    final Vorgang bestand = vorgang(true);

    // When
    final Vorgang abgeschlossen = bestand.abgeschlossen(GEAENDERT);

    // Then
    assertThat(abgeschlossen.abgeschlossen()).isTrue();
  }

  @Test
  void wiederEroeffnet_thenSwitchesTheStateOff() {
    // Given
    final Vorgang bestand = vorgang(true);

    // When
    final Vorgang wieder = bestand.wiederEroeffnet(GEAENDERT);

    // Then
    assertThat(wieder.abgeschlossen()).isFalse();
  }

  @Test
  void wiederEroeffnet_thenStampsTheChangeTime() {
    // Given
    final Vorgang bestand = vorgang(true);

    // When
    final Vorgang wieder = bestand.wiederEroeffnet(GEAENDERT);

    // Then
    assertThat(wieder.updatedAt()).isEqualTo(GEAENDERT);
  }

  @Test
  void wiederEroeffnet_thenLeavesEveryOtherValueUntouched() {
    // Given
    final Vorgang bestand = vorgang(true);

    // When
    final Vorgang wieder = bestand.wiederEroeffnet(GEAENDERT);

    // Then
    assertThat(wieder)
        .satisfies(
            neu -> assertThat(neu.id()).isEqualTo(7L),
            neu -> assertThat(neu.nummer()).isEqualTo(12L),
            neu -> assertThat(neu.titel()).isEqualTo("Website-Relaunch"),
            neu -> assertThat(neu.firmaId()).isEqualTo(3L),
            neu -> assertThat(neu.ansprechpartnerId()).isEqualTo(5L),
            neu -> assertThat(neu.createdAt()).isEqualTo(ANGELEGT));
  }

  @Test
  void wiederEroeffnet_thenLeavesTheOriginalUntouched() {
    // Given
    final Vorgang bestand = vorgang(true);

    // When
    bestand.wiederEroeffnet(GEAENDERT);

    // Then
    assertThat(bestand.abgeschlossen()).isTrue();
  }

  @Test
  void wiederEroeffnet_givenAnOpenVorgang_thenStaysOpen() {
    // Given
    final Vorgang bestand = vorgang(false);

    // When
    final Vorgang wieder = bestand.wiederEroeffnet(GEAENDERT);

    // Then
    assertThat(wieder.abgeschlossen()).isFalse();
  }
}
