package org.mwolff.fbcrm.rechnung.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.rechnung.domain.Nummernmuster;
import org.mwolff.fbcrm.rechnung.domain.Rechnungseinstellungen;
import org.mwolff.fbcrm.rechnung.domain.RechnungseinstellungenRepository;

/**
 * Das Lesen der Rechnungseinstellungen (fachliche Quelle #159, Kriterium 5).
 *
 * <p>Ohne Sonderzweig fuer die frische Instanz: Die Migration hat die eine Zeile mit ihren
 * Vorbelegungen angelegt, und der Port kennt kein „noch keine Einstellungen".
 */
@ExtendWith(MockitoExtension.class)
class RechnungseinstellungenLesenUseCaseTest {

  @Mock private RechnungseinstellungenRepository bestand;

  @Test
  void lese_thenAnswersWithTheStoredSettings() {
    // Given
    final Rechnungseinstellungen gespeichert =
        new Rechnungseinstellungen(
            new Nummernmuster("R{JJ}-{NNNN}"), 4, new BigDecimal("19.50"), 14);
    when(bestand.lies()).thenReturn(gespeichert);

    // When
    final Rechnungseinstellungen gelesen = new RechnungseinstellungenLesenUseCase(bestand).lese();

    // Then — der Anwendungsfall reicht durch und rechnet nichts um.
    assertThat(gelesen).isEqualTo(gespeichert);
  }
}
