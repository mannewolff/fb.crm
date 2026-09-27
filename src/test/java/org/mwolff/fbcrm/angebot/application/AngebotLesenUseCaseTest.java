package org.mwolff.fbcrm.angebot.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotsstand;
import org.mwolff.fbcrm.angebot.domain.Angebotszustand;

/**
 * Das Lesen eines einzelnen Angebots (Kriterien 5, 18).
 *
 * <p>Gegenstand ist der Stand: Er wird nicht gespeichert, sondern beim Lesen aus dem Vergleich der
 * Gueltigkeit mit dem heutigen Tag in der Geschaeftszone abgeleitet (E4, E12). Ein versendetes
 * Angebot, dessen letzter Tag verstrichen ist, liest sich als {@link Angebotsstand#ABGELAUFEN} —
 * ohne dass jemand etwas hat tun muessen.
 */
@ExtendWith(MockitoExtension.class)
class AngebotLesenUseCaseTest {

  private static final long ANGEBOT = 11L;
  private static final Instant INNERHALB = Instant.parse("2026-10-20T08:00:00Z");
  private static final Instant DANACH = Instant.parse("2026-10-21T08:00:00Z");

  @Mock private AngebotRepository angebote;

  private AngebotAnsicht lese(final Instant jetzt) {
    return new AngebotLesenUseCase(angebote, Clock.fixed(jetzt, ZoneOffset.UTC)).lese(ANGEBOT);
  }

  @Test
  void lese_thenAnswersWithTheAngebotAndItsSumme() {
    // Given — Kriterium 5: 2,5 × 1.000,01 € + 1.200,00 € = 3.700,03 €.
    when(angebote.findById(ANGEBOT)).thenReturn(Optional.of(Angebotsdoppel.entwurf(ANGEBOT)));

    // When
    final AngebotAnsicht ansicht = lese(INNERHALB);

    // Then
    assertThat(ansicht.angebot().requireId()).isEqualTo(ANGEBOT);
    assertThat(ansicht.angebot().summe()).isEqualByComparingTo(new BigDecimal("3700.03"));
    assertThat(ansicht.stand()).isEqualTo(Angebotsstand.ENTWURF);
  }

  @Test
  void lese_whenTheValidityHasNotPassed_thenTheStandIsVersendet() {
    // Given — der letzte Tag der Gueltigkeit zaehlt noch mit (Kriterium 18).
    when(angebote.findById(ANGEBOT))
        .thenReturn(
            Optional.of(Angebotsdoppel.festgeschrieben(ANGEBOT, Angebotszustand.VERSENDET)));

    // When / Then
    assertThat(lese(INNERHALB).stand()).isEqualTo(Angebotsstand.VERSENDET);
  }

  @Test
  void lese_whenTheValidityHasPassed_thenTheStandIsAbgelaufen() {
    // Given — E4: „abgelaufen" entsteht beim Lesen und steht in keiner Spalte.
    when(angebote.findById(ANGEBOT))
        .thenReturn(
            Optional.of(Angebotsdoppel.festgeschrieben(ANGEBOT, Angebotszustand.VERSENDET)));

    // When / Then
    assertThat(lese(DANACH).stand()).isEqualTo(Angebotsstand.ABGELAUFEN);
  }

  @Test
  void lese_whenTheAngebotIsUnknown_thenRejects() {
    // Given
    when(angebote.findById(ANGEBOT)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> lese(INNERHALB)).isInstanceOf(AngebotNichtGefunden.class);
  }
}
