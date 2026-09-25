package org.mwolff.fbcrm.vorgang.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Verhalten des Eintrags: die Zusicherungen beider Arten und das Aendern. */
class EintragTest {

  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");
  private static final Instant GESCHEHEN = Instant.parse("2026-08-30T10:15:00Z");
  private static final Instant SPAETER = Instant.parse("2026-09-05T09:00:00Z");
  private static final String SCHLUESSEL = "vorgang/7/3f1c8c8e-1111-2222-3333-444455556666";

  private static Eintrag kommentar() {
    return Eintrag.kommentar(7L, "Kunde hat angerufen.", GESCHEHEN, Herkunft.VON_HAND, ANGELEGT);
  }

  private static Eintrag anhang() {
    return Eintrag.anhang(
        7L,
        "Das Angebot",
        GESCHEHEN,
        Herkunft.VON_HAND,
        "Angebot.pdf",
        4096L,
        SCHLUESSEL,
        ANGELEGT);
  }

  @Test
  void kommentar_thenCarriesTheGivenValues() {
    // When
    final Eintrag eintrag = kommentar();

    // Then
    assertThat(eintrag)
        .satisfies(
            neu -> assertThat(neu.vorgangId()).isEqualTo(7L),
            neu -> assertThat(neu.art()).isEqualTo(Eintragsart.KOMMENTAR),
            neu -> assertThat(neu.text()).isEqualTo("Kunde hat angerufen."),
            neu -> assertThat(neu.geschehenAm()).isEqualTo(GESCHEHEN),
            neu -> assertThat(neu.herkunft()).isEqualTo(Herkunft.VON_HAND),
            neu -> assertThat(neu.createdAt()).isEqualTo(ANGELEGT));
  }

  @Test
  void kommentar_thenCarriesNoFileData() {
    // When
    final Eintrag eintrag = kommentar();

    // Then
    assertThat(eintrag)
        .satisfies(
            neu -> assertThat(neu.dateiName()).isNull(),
            neu -> assertThat(neu.dateiGroesse()).isNull(),
            neu -> assertThat(neu.objektSchluessel()).isNull());
  }

  @Test
  void kommentar_thenIsNotYetStoredAndNotYetChanged() {
    // When
    final Eintrag eintrag = kommentar();

    // Then
    assertThat(eintrag)
        .satisfies(
            neu -> assertThat(neu.id()).isNull(), neu -> assertThat(neu.geaendertAm()).isNull());
  }

  @Test
  void kommentar_givenABlankText_thenRejected() {
    // When / Then
    assertThatThrownBy(() -> Eintrag.kommentar(7L, "   ", GESCHEHEN, Herkunft.VON_HAND, ANGELEGT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Text");
  }

  @Test
  void anhang_thenCarriesTheFileData() {
    // When
    final Eintrag eintrag = anhang();

    // Then
    assertThat(eintrag)
        .satisfies(
            neu -> assertThat(neu.art()).isEqualTo(Eintragsart.ANHANG),
            neu -> assertThat(neu.text()).isEqualTo("Das Angebot"),
            neu -> assertThat(neu.dateiName()).isEqualTo("Angebot.pdf"),
            neu -> assertThat(neu.dateiGroesse()).isEqualTo(4096L),
            neu -> assertThat(neu.objektSchluessel()).isEqualTo(SCHLUESSEL));
  }

  @Test
  void anhang_givenNoText_thenAccepted() {
    // When
    final Eintrag eintrag =
        Eintrag.anhang(
            7L, null, GESCHEHEN, Herkunft.VON_HAND, "Angebot.pdf", 4096L, SCHLUESSEL, ANGELEGT);

    // Then
    assertThat(eintrag.text()).isNull();
  }

  @Test
  void anhang_givenABlankFileName_thenRejected() {
    // When / Then
    assertThatThrownBy(
            () ->
                Eintrag.anhang(
                    7L, null, GESCHEHEN, Herkunft.VON_HAND, "  ", 4096L, SCHLUESSEL, ANGELEGT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Dateiname");
  }

  @Test
  void anhang_givenABlankObjectKey_thenRejected() {
    // When / Then
    assertThatThrownBy(
            () ->
                Eintrag.anhang(
                    7L, null, GESCHEHEN, Herkunft.VON_HAND, "Angebot.pdf", 4096L, "  ", ANGELEGT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Objektschluessel");
  }

  @Test
  void geaendert_thenCarriesTheNewTextAndTime() {
    // Given
    final Eintrag bestand = kommentar();

    // When
    final Eintrag geaendert = bestand.geaendert("Kunde hat gemailt.", SPAETER, SPAETER);

    // Then
    assertThat(geaendert)
        .satisfies(
            neu -> assertThat(neu.text()).isEqualTo("Kunde hat gemailt."),
            neu -> assertThat(neu.geschehenAm()).isEqualTo(SPAETER));
  }

  @Test
  void geaendert_thenStampsTheChangeTime() {
    // Given
    final Eintrag bestand = kommentar();

    // When
    final Eintrag geaendert = bestand.geaendert("Kunde hat gemailt.", GESCHEHEN, SPAETER);

    // Then
    assertThat(geaendert.geaendertAm()).isEqualTo(SPAETER);
  }

  @Test
  void geaendert_thenLeavesArtOriginAndFileDataUntouched() {
    // Given
    final Eintrag bestand = anhang();

    // When
    final Eintrag geaendert = bestand.geaendert("Neue Beschreibung", SPAETER, SPAETER);

    // Then
    assertThat(geaendert)
        .satisfies(
            neu -> assertThat(neu.art()).isEqualTo(Eintragsart.ANHANG),
            neu -> assertThat(neu.herkunft()).isEqualTo(Herkunft.VON_HAND),
            neu -> assertThat(neu.dateiName()).isEqualTo("Angebot.pdf"),
            neu -> assertThat(neu.dateiGroesse()).isEqualTo(4096L),
            neu -> assertThat(neu.objektSchluessel()).isEqualTo(SCHLUESSEL),
            neu -> assertThat(neu.createdAt()).isEqualTo(ANGELEGT));
  }

  @Test
  void geaendert_thenLeavesTheOriginalUntouched() {
    // Given
    final Eintrag bestand = kommentar();

    // When
    bestand.geaendert("Kunde hat gemailt.", SPAETER, SPAETER);

    // Then
    assertThat(bestand.text()).isEqualTo("Kunde hat angerufen.");
  }

  @Test
  void geaendert_givenAnAnhangWithoutText_thenAccepted() {
    // Given
    final Eintrag bestand = anhang();

    // When
    final Eintrag geaendert = bestand.geaendert(null, SPAETER, SPAETER);

    // Then
    assertThat(geaendert.text()).isNull();
  }

  @Test
  void geaendert_givenAKommentarWithoutText_thenRejected() {
    // Given
    final Eintrag bestand = kommentar();

    // When / Then
    assertThatThrownBy(() -> bestand.geaendert(null, SPAETER, SPAETER))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Text");
  }

  @Test
  void geaendert_givenAKommentarWithABlankText_thenRejected() {
    // Given
    final Eintrag bestand = kommentar();

    // When / Then
    assertThatThrownBy(() -> bestand.geaendert("   ", SPAETER, SPAETER))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Text");
  }
}
