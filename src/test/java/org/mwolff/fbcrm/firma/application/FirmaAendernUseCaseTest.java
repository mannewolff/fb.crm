package org.mwolff.fbcrm.firma.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import org.mwolff.fbcrm.firma.domain.Anschrift;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;

/**
 * Das Aendern einer Firma (Kriterium 8).
 *
 * <p>Geaendert werden ausschliesslich die Angaben; Kennung, Anlagezeitpunkt und Stilllegungsstand
 * bleiben, wie sie waren — das Aendern ist kein Weg, eine stillgelegte Firma nebenbei zu
 * reaktivieren (Kriterium 14).
 */
@ExtendWith(MockitoExtension.class)
class FirmaAendernUseCaseTest {

  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");
  private static final Instant JETZT = Instant.parse("2026-09-25T09:30:00Z");

  @Mock private FirmaRepository firmen;
  @Captor private ArgumentCaptor<Firma> gespeicherte;

  private FirmaAendernUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase = new FirmaAendernUseCase(firmen, Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  private static Firma bestand(final boolean aktiv) {
    return new Firma(
        7L,
        "Adler AG",
        new Anschrift("Am Wall 1", "28195", "Bremen", "Deutschland"),
        "75/123/45678",
        "DE123456789",
        aktiv,
        ANGELEGT,
        ANGELEGT);
  }

  private static FirmaDaten neueDaten(final String name) {
    return new FirmaDaten(
        name,
        new Anschrift("Moenckebergstrasse 2", "20095", "Hamburg", "Deutschland"),
        "75/999/00000",
        "DE999999999");
  }

  private Firma aendereUndFange(final Firma vorher, final FirmaDaten daten) {
    when(firmen.findById(7L)).thenReturn(Optional.of(vorher));
    when(firmen.save(any(Firma.class))).thenAnswer(aufruf -> aufruf.getArgument(0));
    useCase.aendern(7L, daten);
    verify(firmen).save(gespeicherte.capture());
    return gespeicherte.getValue();
  }

  @Test
  void aendern_thenStoresTheNewValues() {
    // When
    final Firma gespeichert = aendereUndFange(bestand(true), neueDaten("Adler GmbH"));

    // Then
    assertThat(gespeichert)
        .extracting(Firma::name, Firma::anschrift, Firma::steuernummer, Firma::umsatzsteuerId)
        .containsExactly(
            "Adler GmbH",
            new Anschrift("Moenckebergstrasse 2", "20095", "Hamburg", "Deutschland"),
            "75/999/00000",
            "DE999999999");
  }

  @Test
  void aendern_thenKeepsIdAndCreationTime() {
    // When
    final Firma gespeichert = aendereUndFange(bestand(true), neueDaten("Adler GmbH"));

    // Then
    assertThat(gespeichert).extracting(Firma::id, Firma::createdAt).containsExactly(7L, ANGELEGT);
  }

  @Test
  void aendern_thenTakesTheChangeTimeFromTheClock() {
    // When
    final Firma gespeichert = aendereUndFange(bestand(true), neueDaten("Adler GmbH"));

    // Then
    assertThat(gespeichert.updatedAt()).isEqualTo(JETZT);
  }

  @Test
  void aendern_givenARetiredFirma_thenLeavesItRetired() {
    // When — Kriterium 14: aendern ist kein verstecktes Reaktivieren.
    final Firma gespeichert = aendereUndFange(bestand(false), neueDaten("Adler GmbH"));

    // Then
    assertThat(gespeichert.aktiv()).isFalse();
  }

  @Test
  void aendern_givenPaddedValues_thenStoresThemTrimmed() {
    // When — E9.
    final Firma gespeichert = aendereUndFange(bestand(true), neueDaten("  Adler GmbH  "));

    // Then
    assertThat(gespeichert.name()).isEqualTo("Adler GmbH");
  }

  @Test
  void aendern_givenOptionalFieldsOfWhitespaceOnly_thenClearsThem() {
    // When — E9: die Angabe wird geloescht, nicht auf den Leerstring gesetzt.
    final Firma gespeichert =
        aendereUndFange(
            bestand(true),
            new FirmaDaten("Adler GmbH", new Anschrift(" ", " ", " ", " "), " ", " "));

    // Then
    assertThat(gespeichert)
        .extracting(Firma::anschrift, Firma::steuernummer, Firma::umsatzsteuerId)
        .containsExactly(new Anschrift(null, null, null, null), null, null);
  }

  @Test
  void aendern_givenAnUnknownId_thenThrowsFirmaNichtGefunden() {
    // Given
    when(firmen.findById(4711L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.aendern(4711L, neueDaten("Adler GmbH")))
        .isInstanceOf(FirmaNichtGefunden.class);
  }

  @Test
  void aendern_givenAnUnknownId_thenStoresNothing() {
    // Given
    when(firmen.findById(4711L)).thenReturn(Optional.empty());

    // When
    assertThatThrownBy(() -> useCase.aendern(4711L, neueDaten("Adler GmbH")))
        .isInstanceOf(FirmaNichtGefunden.class);

    // Then
    verify(firmen, never()).save(any(Firma.class));
  }

  @Test
  void aendern_givenANameOfWhitespaceOnly_thenIsRefusedBeforeReadingTheFirma() {
    // When / Then — E9: die Normalisierung steht vor dem Zugriff auf den Bestand.
    assertThatThrownBy(() -> useCase.aendern(7L, neueDaten("   ")))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
