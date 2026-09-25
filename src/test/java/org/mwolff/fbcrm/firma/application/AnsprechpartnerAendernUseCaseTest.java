package org.mwolff.fbcrm.firma.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.firma.domain.Ansprechpartner;
import org.mwolff.fbcrm.firma.domain.AnsprechpartnerRepository;

/**
 * Das Aendern eines Ansprechpartners (Kriterium 11).
 *
 * <p>Die Firma bleibt, wie sie war: Passt die Kennung im Pfad nicht zu der des gespeicherten
 * Ansprechpartners, ist er fuer diesen Weg schlicht nicht da (Kriterium 12, E7). Ein Umhaengen gibt
 * es damit nicht — auch nicht als Nebenwirkung.
 */
@ExtendWith(MockitoExtension.class)
class AnsprechpartnerAendernUseCaseTest {

  private static final long FIRMA = 7L;
  private static final long ANDERE_FIRMA = 8L;
  private static final long ID = 3L;
  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");
  private static final Instant JETZT = Instant.parse("2026-09-25T09:30:00Z");

  @Mock private AnsprechpartnerRepository ansprechpartner;
  @Captor private ArgumentCaptor<Ansprechpartner> gespeicherte;

  private AnsprechpartnerAendernUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase =
        new AnsprechpartnerAendernUseCase(ansprechpartner, Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  private static Ansprechpartner bestand(final long firmaId, final boolean aktiv) {
    return new Ansprechpartner(
        ID,
        firmaId,
        "Max",
        "Mueller",
        "Einkauf",
        "max@firma.de",
        "0421 1234",
        "0170 1234",
        aktiv,
        ANGELEGT,
        ANGELEGT);
  }

  private static AnsprechpartnerDaten neueDaten(final String nachname) {
    return new AnsprechpartnerDaten(
        "Maxime", nachname, "Vertrieb", "maxime@firma.de", "040 9999", "0171 9999");
  }

  private Ansprechpartner aendereUndFange(
      final Ansprechpartner vorher, final AnsprechpartnerDaten daten) {
    when(ansprechpartner.findById(ID)).thenReturn(Optional.of(vorher));
    when(ansprechpartner.save(any(Ansprechpartner.class)))
        .thenAnswer(aufruf -> aufruf.getArgument(0));
    useCase.aendern(FIRMA, ID, daten);
    verify(ansprechpartner).save(gespeicherte.capture());
    return gespeicherte.getValue();
  }

  @Test
  void aendern_thenStoresTheNewValues() {
    // When
    final Ansprechpartner gespeichert = aendereUndFange(bestand(FIRMA, true), neueDaten("Maier"));

    // Then
    assertThat(gespeichert)
        .extracting(
            Ansprechpartner::vorname,
            Ansprechpartner::nachname,
            Ansprechpartner::rolle,
            Ansprechpartner::email,
            Ansprechpartner::telefonFestnetz,
            Ansprechpartner::telefonMobil)
        .containsExactly("Maxime", "Maier", "Vertrieb", "maxime@firma.de", "040 9999", "0171 9999");
  }

  @Test
  void aendern_thenKeepsIdFirmaAndCreationTime() {
    // When — E7: die Firma ist keine Angabe der Maske.
    final Ansprechpartner gespeichert = aendereUndFange(bestand(FIRMA, true), neueDaten("Maier"));

    // Then
    assertThat(gespeichert)
        .extracting(Ansprechpartner::id, Ansprechpartner::firmaId, Ansprechpartner::createdAt)
        .containsExactly(ID, FIRMA, ANGELEGT);
  }

  @Test
  void aendern_thenTakesTheChangeTimeFromTheClock() {
    // When
    final Ansprechpartner gespeichert = aendereUndFange(bestand(FIRMA, true), neueDaten("Maier"));

    // Then
    assertThat(gespeichert.updatedAt()).isEqualTo(JETZT);
  }

  @Test
  void aendern_givenARetiredContact_thenLeavesItRetired() {
    // When — aendern ist kein verstecktes Reaktivieren.
    final Ansprechpartner gespeichert = aendereUndFange(bestand(FIRMA, false), neueDaten("Maier"));

    // Then
    assertThat(gespeichert.aktiv()).isFalse();
  }

  @Test
  void aendern_givenPaddedValues_thenStoresThemTrimmed() {
    // When — E9.
    final Ansprechpartner gespeichert =
        aendereUndFange(bestand(FIRMA, true), neueDaten("  Maier  "));

    // Then
    assertThat(gespeichert.nachname()).isEqualTo("Maier");
  }

  @Test
  void aendern_givenOptionalFieldsOfWhitespaceOnly_thenClearsThem() {
    // When — E9: die Angabe wird geloescht, nicht auf den Leerstring gesetzt.
    final Ansprechpartner gespeichert =
        aendereUndFange(
            bestand(FIRMA, true), new AnsprechpartnerDaten(" ", "Maier", " ", " ", " ", " "));

    // Then
    assertThat(gespeichert)
        .extracting(
            Ansprechpartner::vorname,
            Ansprechpartner::rolle,
            Ansprechpartner::email,
            Ansprechpartner::telefonFestnetz,
            Ansprechpartner::telefonMobil)
        .containsOnlyNulls();
  }

  @Test
  void aendern_givenAnUnknownId_thenThrowsAnsprechpartnerNichtGefunden() {
    // Given
    when(ansprechpartner.findById(ID)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.aendern(FIRMA, ID, neueDaten("Maier")))
        .isInstanceOf(AnsprechpartnerNichtGefunden.class);
  }

  @Test
  void aendern_givenAnUnknownId_thenStoresNothing() {
    // Given
    when(ansprechpartner.findById(ID)).thenReturn(Optional.empty());

    // When
    assertThatThrownBy(() -> useCase.aendern(FIRMA, ID, neueDaten("Maier")))
        .isInstanceOf(AnsprechpartnerNichtGefunden.class);

    // Then
    verify(ansprechpartner, never()).save(any(Ansprechpartner.class));
  }

  @Test
  void aendern_givenAnIdOfAnotherFirma_thenThrowsAnsprechpartnerNichtGefunden() {
    // Given — Kriterium 12: unter dieser Firma gibt es diesen Ansprechpartner nicht.
    when(ansprechpartner.findById(ID)).thenReturn(Optional.of(bestand(ANDERE_FIRMA, true)));

    // When / Then
    assertThatThrownBy(() -> useCase.aendern(FIRMA, ID, neueDaten("Maier")))
        .isInstanceOf(AnsprechpartnerNichtGefunden.class);
  }

  @Test
  void aendern_givenAnIdOfAnotherFirma_thenStoresNothing() {
    // Given
    when(ansprechpartner.findById(ID)).thenReturn(Optional.of(bestand(ANDERE_FIRMA, true)));

    // When — E7: es gibt keinen Weg, einen Ansprechpartner umzuhaengen.
    assertThatThrownBy(() -> useCase.aendern(FIRMA, ID, neueDaten("Maier")))
        .isInstanceOf(AnsprechpartnerNichtGefunden.class);

    // Then
    verify(ansprechpartner, never()).save(any(Ansprechpartner.class));
  }

  @Test
  void aendern_givenALastNameOfWhitespaceOnly_thenIsRefusedBeforeReadingTheContact() {
    // When / Then — E9: die Normalisierung steht vor dem Zugriff auf den Bestand.
    assertThatThrownBy(() -> useCase.aendern(FIRMA, ID, neueDaten("   ")))
        .isInstanceOf(IllegalArgumentException.class);

    // Then
    verifyNoInteractions(ansprechpartner);
  }
}
