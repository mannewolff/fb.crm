package org.mwolff.fbcrm.eigeneangaben.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngaben;
import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngabenRepository;
import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngabenStand;

/**
 * Das Lesen der Selbstauskunft (Kriterium 1).
 *
 * <p>Der Anwendungsfall entscheidet nichts: Es gibt genau einen Satz Angaben, und den liefert der
 * Bestand. Einen Zweig „noch keine Zeile" gibt es nicht — die Migration legt sie an.
 */
@ExtendWith(MockitoExtension.class)
class EigeneAngabenLesenUseCaseTest {

  @Mock private EigeneAngabenRepository bestand;

  @InjectMocks private EigeneAngabenLesenUseCase useCase;

  @Test
  void lese_thenAnswersWithTheStoredValues() {
    // Given
    final EigeneAngaben gespeichert =
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
    when(bestand.lies()).thenReturn(gespeichert);
    when(bestand.stand()).thenReturn(new EigeneAngabenStand(4L, 30L, Instant.EPOCH));

    // When
    final GespeicherteEigeneAngaben gelesen = useCase.lese();

    // Then — samt dem Stand, den das Speichern vorlegt (Plan #415, E2).
    assertThat(gelesen).isEqualTo(new GespeicherteEigeneAngaben(gespeichert, 4L));
  }

  @Test
  void lese_givenTheFreshInstance_thenEveryValueIsAbsent() {
    // Given — nach der Migration steht die eine Zeile mit lauter NULL da.
    final EigeneAngaben leer =
        new EigeneAngaben(
            null, null, new Anschrift(null, null, null, null), null, null, null, null, null, null);
    when(bestand.lies()).thenReturn(leer);
    when(bestand.stand()).thenReturn(new EigeneAngabenStand(0L, null, Instant.EPOCH));

    // When
    final GespeicherteEigeneAngaben gelesen = useCase.lese();

    // Then
    assertThat(gelesen).isEqualTo(new GespeicherteEigeneAngaben(leer, 0L));
  }
}
