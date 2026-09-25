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
 * Stilllegen und Wiederaktivieren einer Firma (Kriterium 13).
 *
 * <p>Beides in einer Klasse, weil es dieselbe Umschaltung ist — und weil ein Weg, der nur in eine
 * Richtung fuehrt, kein Stilllegen waere, sondern ein Loeschen mit anderem Namen (E3).
 */
@ExtendWith(MockitoExtension.class)
class FirmaStilllegenUseCaseTest {

  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");
  private static final Instant JETZT = Instant.parse("2026-09-25T09:30:00Z");

  @Mock private FirmaRepository firmen;
  @Captor private ArgumentCaptor<Firma> gespeicherte;

  private FirmaStilllegenUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase = new FirmaStilllegenUseCase(firmen, Clock.fixed(JETZT, ZoneOffset.UTC));
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

  private Firma gefangeneFirma() {
    verify(firmen).save(gespeicherte.capture());
    return gespeicherte.getValue();
  }

  private void gibDenBestandAus(final Firma vorher) {
    when(firmen.findById(7L)).thenReturn(Optional.of(vorher));
    when(firmen.save(any(Firma.class))).thenAnswer(aufruf -> aufruf.getArgument(0));
  }

  @Test
  void stilllegen_thenStoresTheFirmaAsRetired() {
    // Given
    gibDenBestandAus(bestand(true));

    // When
    useCase.stilllegen(7L);

    // Then
    assertThat(gefangeneFirma().aktiv()).isFalse();
  }

  @Test
  void stilllegen_thenTakesTheChangeTimeFromTheClock() {
    // Given
    gibDenBestandAus(bestand(true));

    // When
    useCase.stilllegen(7L);

    // Then
    assertThat(gefangeneFirma().updatedAt()).isEqualTo(JETZT);
  }

  @Test
  void stilllegen_thenKeepsEveryOtherValue() {
    // Given — Kriterium 13: stillgelegt heisst nicht geloescht, die Angaben bleiben.
    gibDenBestandAus(bestand(true));

    // When
    useCase.stilllegen(7L);

    // Then
    assertThat(gefangeneFirma())
        .extracting(Firma::id, Firma::name, Firma::createdAt)
        .containsExactly(7L, "Adler AG", ANGELEGT);
  }

  @Test
  void aktivieren_thenStoresTheFirmaAsActive() {
    // Given
    gibDenBestandAus(bestand(false));

    // When
    useCase.aktivieren(7L);

    // Then
    assertThat(gefangeneFirma().aktiv()).isTrue();
  }

  @Test
  void aktivieren_thenTakesTheChangeTimeFromTheClock() {
    // Given
    gibDenBestandAus(bestand(false));

    // When
    useCase.aktivieren(7L);

    // Then
    assertThat(gefangeneFirma().updatedAt()).isEqualTo(JETZT);
  }

  @Test
  void stilllegen_givenAnUnknownId_thenThrowsFirmaNichtGefunden() {
    // Given
    when(firmen.findById(4711L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.stilllegen(4711L)).isInstanceOf(FirmaNichtGefunden.class);
  }

  @Test
  void stilllegen_givenAnUnknownId_thenStoresNothing() {
    // Given
    when(firmen.findById(4711L)).thenReturn(Optional.empty());

    // When
    assertThatThrownBy(() -> useCase.stilllegen(4711L)).isInstanceOf(FirmaNichtGefunden.class);

    // Then
    verify(firmen, never()).save(any(Firma.class));
  }

  @Test
  void aktivieren_givenAnUnknownId_thenThrowsFirmaNichtGefunden() {
    // Given
    when(firmen.findById(4711L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.aktivieren(4711L)).isInstanceOf(FirmaNichtGefunden.class);
  }

  @Test
  void aktivieren_givenAnUnknownId_thenStoresNothing() {
    // Given
    when(firmen.findById(4711L)).thenReturn(Optional.empty());

    // When
    assertThatThrownBy(() -> useCase.aktivieren(4711L)).isInstanceOf(FirmaNichtGefunden.class);

    // Then
    verify(firmen, never()).save(any(Firma.class));
  }
}
