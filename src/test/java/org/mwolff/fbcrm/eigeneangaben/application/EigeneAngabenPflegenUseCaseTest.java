package org.mwolff.fbcrm.eigeneangaben.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
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
import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngabenStand;

/**
 * Das Pflegen der Selbstauskunft (Kriterium 1).
 *
 * <p>Hier liegt die Normalisierung: Leerraum am Rand faellt weg, und was danach leer ist, wird
 * {@code null} statt Leerstring — dieselbe Regel wie E9 des Firma-Moduls. Der Zeitpunkt der
 * Aenderung kommt aus der injizierten Uhr, nie aus {@code Instant.now()}.
 *
 * <p>Davor steht der Vergleich des vorgelegten Stands (#411, Kriterium 4; Plan #415, E1, E14): Auf
 * einem alten Stand wird abgewiesen, und nichts wird geschrieben.
 */
@ExtendWith(MockitoExtension.class)
class EigeneAngabenPflegenUseCaseTest {

  private static final Instant JETZT = Instant.parse("2026-09-27T09:30:00Z");

  /** Der Stand, den die Maske vorlegt und den die Zeile traegt. */
  private static final long STAND = 3L;

  /** Das handelnde Konto. */
  private static final long KONTO = 31L;

  /** Das Konto, das zuletzt geaendert hat. */
  private static final long ANNA = 30L;

  private static final Instant ANNAS_AENDERUNG = Instant.parse("2026-09-26T08:42:00Z");

  @Mock private EigeneAngabenRepository bestand;
  @Captor private ArgumentCaptor<EigeneAngaben> gespeicherte;

  private EigeneAngabenPflegenUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase = new EigeneAngabenPflegenUseCase(bestand, Clock.fixed(JETZT, ZoneOffset.UTC));
    // Nachsichtig: Die abweisenden Faelle kommen nie zum Schreiben.
    lenient()
        .when(bestand.stand())
        .thenReturn(new EigeneAngabenStand(STAND, ANNA, ANNAS_AENDERUNG));
    lenient()
        .when(bestand.speichere(any(), any(), anyLong(), anyLong()))
        .thenReturn(new EigeneAngabenStand(STAND + 1, KONTO, JETZT));
  }

  private static EigeneAngaben angaben(final String wert) {
    return new EigeneAngaben(
        wert, wert, new Anschrift(wert, wert, wert, wert), wert, wert, wert, wert, wert, wert);
  }

  private EigeneAngaben pflegeUndFange(final EigeneAngaben eingereicht) {
    useCase.pflege(eingereicht, STAND, KONTO);
    verify(bestand).speichere(gespeicherte.capture(), eq(JETZT), eq(STAND), eq(KONTO));
    return gespeicherte.getValue();
  }

  @Test
  void pflege_thenStoresEveryValue() {
    // Given
    final EigeneAngaben eingereicht =
        new EigeneAngaben(
            "Manfred Wolff",
            "Freiberuflicher Softwareentwickler",
            new Anschrift("Am Wall 1", "28195", "Bremen", "DE"),
            "manne@example.org",
            "0421 1234",
            "https://mwolff.org",
            "75/123/45678",
            "DE123456789",
            "DE02120300000000202051");

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
    useCase.pflege(angaben("Wert"), STAND, KONTO);

    // Then — nie Instant.now(): die Uhr kommt von aussen.
    verify(bestand).speichere(any(EigeneAngaben.class), eq(JETZT), anyLong(), anyLong());
  }

  @Test
  void pflege_thenAnswersWithTheNormalisedValuesAndTheRaisedVersion() {
    // When
    final GespeicherteEigeneAngaben neu = useCase.pflege(angaben("  Wert  "), STAND, KONTO);

    // Then — die Maske legt die erhoehte Version beim naechsten Speichern vor (Plan #415, E2).
    assertThat(neu).isEqualTo(new GespeicherteEigeneAngaben(angaben("Wert"), STAND + 1));
  }

  @Test
  void pflege_givenAnOutdatedStand_thenRefusesAndWritesNothing() {
    // When — #411, Kriterium 4: Anna hat die Angaben inzwischen gespeichert.
    final EigeneAngabenGeaendert konflikt =
        catchThrowableOfType(
            EigeneAngabenGeaendert.class, () -> useCase.pflege(angaben("Wert"), STAND - 1, KONTO));

    // Then — Urheber, Handelnder und Zeitpunkt fuer die Meldung.
    assertThat(konflikt).isNotNull();
    assertThat(konflikt.urheberKonto()).hasValue(ANNA);
    assertThat(konflikt.handelndesKonto()).isEqualTo(KONTO);
    assertThat(konflikt.zeitpunkt()).hasValue(ANNAS_AENDERUNG);
    verify(bestand, never()).speichere(any(), any(), anyLong(), anyLong());
  }

  @Test
  void pflege_givenAStandThatNeverExisted_thenRefusesAsWell() {
    // When / Then — abgewiesen wird jede Abweichung, nicht nur ein aelterer Stand.
    assertThatThrownBy(() -> useCase.pflege(angaben("Wert"), STAND + 1, KONTO))
        .isInstanceOf(EigeneAngabenGeaendert.class);
    verify(bestand, never()).speichere(any(), any(), anyLong(), anyLong());
  }
}
