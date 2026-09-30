package org.mwolff.fbcrm.rechnung.application;

import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.rechnung.domain.Nummernmuster;
import org.mwolff.fbcrm.rechnung.domain.Rechnungseinstellungen;
import org.mwolff.fbcrm.rechnung.domain.RechnungseinstellungenRepository;

/**
 * Das Fortschreiben der Rechnungseinstellungen (fachliche Quelle #159, Kriterium 7).
 *
 * <p>Geschrieben wird immer der ganze Satz — die Maske zeigt alle vier Werte auf einmal. Der
 * Zeitpunkt der Aenderung kommt aus der injizierten Uhr, nie aus {@code Instant.now()}
 * (CLAUDE-java.md §6.2).
 */
@ExtendWith(MockitoExtension.class)
class RechnungseinstellungenPflegenUseCaseTest {

  private static final Instant JETZT = Instant.parse("2026-09-30T09:30:00Z");

  @Mock private RechnungseinstellungenRepository bestand;

  @Test
  void pflege_thenStoresTheSettingsWithTheTimeOfTheInjectedClock() {
    // Given
    final Rechnungseinstellungen eingereicht =
        new Rechnungseinstellungen(
            new Nummernmuster("R{JJ}-{NNNN}"), 4, new BigDecimal("19.50"), 14);

    // When
    new RechnungseinstellungenPflegenUseCase(bestand, Clock.fixed(JETZT, ZoneOffset.UTC))
        .pflege(eingereicht);

    // Then — unveraendert weitergereicht; zu normalisieren gibt es hier nichts, die vier Werte
    // sind Zahlen und ein bereits geprueftes Muster.
    verify(bestand).speichere(eingereicht, JETZT);
  }
}
