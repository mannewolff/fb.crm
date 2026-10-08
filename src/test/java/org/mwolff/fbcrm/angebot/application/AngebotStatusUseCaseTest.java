package org.mwolff.fbcrm.angebot.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;

/**
 * Der Statuswechsel um eine Stufe (Issue #127, Kriterium 4): gespeichert wird der neue Stand, an
 * den Enden nichts.
 */
@ExtendWith(MockitoExtension.class)
class AngebotStatusUseCaseTest {

  private static final long ANGEBOT = 11L;
  private static final Instant JETZT = Instant.parse("2026-09-28T09:30:00Z");

  @Mock private AngebotRepository angebote;

  private AngebotStatusUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase = new AngebotStatusUseCase(angebote, Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  private void angebotIst(final Angebotsstatus status) {
    when(angebote.findById(ANGEBOT))
        .thenReturn(Optional.of(Angebotsdoppel.angebot(ANGEBOT, status)));
  }

  @Test
  void weiter_thenSavesTheNextStatus() {
    // Given
    angebotIst(Angebotsstatus.BESTELLT);
    when(angebote.save(any())).thenAnswer(aufruf -> aufruf.getArgument(0));

    // When
    final Angebot weiter = useCase.weiter(ANGEBOT);

    // Then
    assertThat(weiter.status()).isEqualTo(Angebotsstatus.ERLEDIGT);
    assertThat(weiter.updatedAt()).isEqualTo(JETZT);
  }

  @Test
  void zurueck_thenSavesThePreviousStatus() {
    // Given — Kriterium 4: der Status darf zurueckgesetzt werden.
    angebotIst(Angebotsstatus.ABGERECHNET);
    when(angebote.save(any())).thenAnswer(aufruf -> aufruf.getArgument(0));

    // When
    final Angebot zurueck = useCase.zurueck(ANGEBOT);

    // Then
    assertThat(zurueck.status()).isEqualTo(Angebotsstatus.ERLEDIGT);
    assertThat(zurueck.updatedAt()).isEqualTo(JETZT);
  }

  @Test
  void weiter_givenTheLastStatus_thenRejectsAndWritesNothing() {
    // Given
    angebotIst(Angebotsstatus.ABGERECHNET);

    // When / Then
    assertThatThrownBy(() -> useCase.weiter(ANGEBOT)).isInstanceOf(StatusGrenzeErreicht.class);
    verify(angebote).findById(ANGEBOT);
    verifyNoMoreInteractions(angebote);
  }

  @Test
  void zurueck_givenTheFirstStatus_thenRejectsAndWritesNothing() {
    // Given
    angebotIst(Angebotsstatus.ANGELEGT);

    // When / Then
    assertThatThrownBy(() -> useCase.zurueck(ANGEBOT)).isInstanceOf(StatusGrenzeErreicht.class);
    verify(angebote).findById(ANGEBOT);
    verifyNoMoreInteractions(angebote);
  }

  @Test
  void weiter_whenTheAngebotIsUnknown_thenRejects() {
    // Given
    when(angebote.findById(ANGEBOT)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.weiter(ANGEBOT)).isInstanceOf(AngebotNichtGefunden.class);
  }

  @Test
  void zurueck_whenTheAngebotIsUnknown_thenRejects() {
    // Given
    when(angebote.findById(ANGEBOT)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.zurueck(ANGEBOT)).isInstanceOf(AngebotNichtGefunden.class);
  }
}
