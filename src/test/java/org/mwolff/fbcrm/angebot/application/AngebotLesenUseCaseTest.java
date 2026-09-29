package org.mwolff.fbcrm.angebot.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;

/** Das Lesen eines einzelnen Angebots (Issue #127): der gespeicherte Stand, samt Summe. */
@ExtendWith(MockitoExtension.class)
class AngebotLesenUseCaseTest {

  private static final long ANGEBOT = 11L;

  @Mock private AngebotRepository angebote;

  @Test
  void lese_thenAnswersWithTheAngebotItsStatusAndItsSumme() {
    // Given — 2,5 × 1.000,01 € + 1.200,00 € = 3.700,03 €.
    when(angebote.findById(ANGEBOT))
        .thenReturn(Optional.of(Angebotsdoppel.angebot(ANGEBOT, Angebotsstatus.BESTELLT)));

    // When
    final Angebot angebot = new AngebotLesenUseCase(angebote).lese(ANGEBOT);

    // Then
    assertThat(angebot.requireId()).isEqualTo(ANGEBOT);
    assertThat(angebot.status()).isEqualTo(Angebotsstatus.BESTELLT);
    assertThat(angebot.summe()).isEqualByComparingTo(new BigDecimal("3700.03"));
  }

  @Test
  void lese_whenTheAngebotIsUnknown_thenRejects() {
    // Given
    when(angebote.findById(ANGEBOT)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> new AngebotLesenUseCase(angebote).lese(ANGEBOT))
        .isInstanceOf(AngebotNichtGefunden.class);
  }
}
