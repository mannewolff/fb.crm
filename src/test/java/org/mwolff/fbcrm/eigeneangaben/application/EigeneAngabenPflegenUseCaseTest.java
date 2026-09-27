package org.mwolff.fbcrm.eigeneangaben.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngaben;
import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngabenRepository;

/**
 * Das Pflegen der Selbstauskunft (Kriterium 1).
 *
 * <p>Hier liegt die Normalisierung: Leerraum am Rand faellt weg, und was danach leer ist, wird
 * {@code null} statt Leerstring — dieselbe Regel wie E9 des Firma-Moduls. Der Zeitpunkt der
 * Aenderung kommt aus der injizierten Uhr, nie aus {@code Instant.now()}.
 */
@ExtendWith(MockitoExtension.class)
class EigeneAngabenPflegenUseCaseTest {

  private static final Instant JETZT = Instant.parse("2026-09-27T09:30:00Z");

  @Mock private EigeneAngabenRepository bestand;
  @Captor private ArgumentCaptor<EigeneAngaben> gespeicherte;

  private EigeneAngabenPflegenUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase = new EigeneAngabenPflegenUseCase(bestand, Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  private static EigeneAngaben angaben(final String wert) {
    return new EigeneAngaben(
        wert, new Anschrift(wert, wert, wert, wert), wert, wert, wert, wert, wert, wert);
  }

  private EigeneAngaben pflegeUndFange(final EigeneAngaben eingereicht) {
    useCase.pflege(eingereicht);
    verify(bestand).speichere(gespeicherte.capture(), eq(JETZT));
    return gespeicherte.getValue();
  }

  @Test
  void pflege_thenStoresEveryValue() {
    // Given
    final EigeneAngaben eingereicht =
        new EigeneAngaben(
            "Manfred Wolff",
            new Anschrift("Am Wall 1", "28195", "Bremen", "Deutschland"),
            "manne@example.org",
            "0421 1234",
            "75/123/45678",
            "DE123456789",
            "DE02120300000000202051",
            "Zahlbar innerhalb von 14 Tagen ohne Abzug.");

    // When
    final EigeneAngaben gespeichert = pflegeUndFange(eingereicht);

    // Then
    assertThat(gespeichert).isEqualTo(eingereicht);
  }

  @Test
  void pflege_givenPaddedValues_thenStoresThemTrimmed() {
    // When — E9.
    final EigeneAngaben gespeichert = pflegeUndFange(angaben("  Wert  "));

    // Then
    assertThat(gespeichert).isEqualTo(angaben("Wert"));
  }

  @Test
  void pflege_givenEmptyStrings_thenStoresThemAsAbsent() {
    // When — E9: „nicht angegeben" ist NULL und nicht der Leerstring.
    final EigeneAngaben gespeichert = pflegeUndFange(angaben(""));

    // Then
    assertThat(gespeichert).isEqualTo(angaben(null));
  }

  @Test
  void pflege_givenValuesOfWhitespaceOnly_thenStoresThemAsAbsent() {
    // When — E9.
    final EigeneAngaben gespeichert = pflegeUndFange(angaben("   "));

    // Then
    assertThat(gespeichert).isEqualTo(angaben(null));
  }

  @Test
  void pflege_givenAbsentValues_thenKeepsThemAbsent() {
    // When
    final EigeneAngaben gespeichert = pflegeUndFange(angaben(null));

    // Then
    assertThat(gespeichert).isEqualTo(angaben(null));
  }

  @Test
  void pflege_thenTakesTheChangeTimeFromTheClock() {
    // When
    useCase.pflege(angaben("Wert"));

    // Then — nie Instant.now(): die Uhr kommt von aussen.
    verify(bestand).speichere(any(EigeneAngaben.class), eq(JETZT));
  }
}
