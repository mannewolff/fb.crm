package org.mwolff.fbcrm.rechnung.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.rechnung.domain.RechnungRepository;

/**
 * Das Loeschen eines Rechnungsentwurfs (#160, Kriterien 12 und 14).
 *
 * <p>Ein Entwurf, aus dem nichts wird, verschwindet — er hat nie eine Nummer getragen und reisst
 * keine Luecke. Eine gestellte Rechnung dagegen bleibt: Sie zu loeschen risse ein Loch in den
 * Nummernkreis.
 */
@ExtendWith(MockitoExtension.class)
class RechnungLoeschenUseCaseTest {

  private static final long RECHNUNG = 2L;

  @Mock private RechnungRepository rechnungen;

  private RechnungLoeschenUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase = new RechnungLoeschenUseCase(rechnungen);
  }

  @Test
  void loesche_withAnUnknownRechnung_thenNotFound() {
    // Given
    when(rechnungen.findById(RECHNUNG)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.loesche(RECHNUNG)).isInstanceOf(RechnungNichtGefunden.class);
    verify(rechnungen, never()).delete(anyLong());
  }

  @Test
  void loesche_aGestellteRechnung_thenItIsRejected() {
    // Given
    when(rechnungen.findById(RECHNUNG))
        .thenReturn(
            Optional.of(
                Rechnungsdoppel.gestellt(
                    RECHNUNG, "R26-0001", List.of(Rechnungsdoppel.beratung("80.00")))));

    // When / Then
    assertThatThrownBy(() -> useCase.loesche(RECHNUNG))
        .isInstanceOf(RechnungszustandPasstNicht.class);
    verify(rechnungen, never()).delete(anyLong());
  }

  @Test
  void loesche_anEntwurf_thenItIsGone() {
    // Given
    when(rechnungen.findById(RECHNUNG))
        .thenReturn(
            Optional.of(
                Rechnungsdoppel.entwurf(RECHNUNG, List.of(Rechnungsdoppel.beratung("80.00")))));

    // When
    useCase.loesche(RECHNUNG);

    // Then
    verify(rechnungen).delete(RECHNUNG);
  }
}
