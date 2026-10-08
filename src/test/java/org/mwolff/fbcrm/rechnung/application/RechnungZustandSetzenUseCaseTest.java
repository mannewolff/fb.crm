package org.mwolff.fbcrm.rechnung.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.rechnung.domain.Rechnung;
import org.mwolff.fbcrm.rechnung.domain.RechnungRepository;
import org.mwolff.fbcrm.rechnung.domain.Rechnungszustand;

/**
 * Der Ausgang einer gestellten Rechnung: bezahlt, abgeschrieben, zurueck auf gestellt (Issue #253).
 *
 * <p>Der Anwendungsfall entscheidet nichts ueber die Zulaessigkeit — das tut {@code
 * Rechnung#mitZustand}. Gegenstand ist darum, <b>dass</b> er liest, umstellt und schreibt, dass der
 * Zeitpunkt aus der Uhr kommt, dass eine unbekannte Rechnung 404 ergibt und dass eine Abweisung der
 * Domaene nichts schreibt.
 */
@ExtendWith(MockitoExtension.class)
class RechnungZustandSetzenUseCaseTest {

  private static final long RECHNUNG = 2L;
  private static final Instant JETZT = Instant.parse("2026-10-20T07:30:00Z");

  @Mock private RechnungRepository rechnungen;

  @Captor private ArgumentCaptor<Rechnung> gespeicherte;

  private RechnungZustandSetzenUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase = new RechnungZustandSetzenUseCase(rechnungen, Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  private static Rechnung gestellt() {
    return Rechnungsdoppel.gestellt(
        RECHNUNG, "R26-0001", List.of(Rechnungsdoppel.beratung("80.00")));
  }

  @Test
  void setze_withAnUnknownRechnung_thenNotFound() {
    // Given
    when(rechnungen.findById(RECHNUNG)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.setze(RECHNUNG, Rechnungszustand.BEZAHLT))
        .isInstanceOf(RechnungNichtGefunden.class);
    verify(rechnungen, never()).save(any());
  }

  @Test
  void setze_aGestellteRechnungToBezahlt_thenWrittenWithTheMomentFromTheClock() {
    // Given
    when(rechnungen.findById(RECHNUNG)).thenReturn(Optional.of(gestellt()));
    when(rechnungen.save(any())).thenAnswer(aufruf -> aufruf.getArgument(0));

    // When
    final Rechnung bezahlt = useCase.setze(RECHNUNG, Rechnungszustand.BEZAHLT);

    // Then
    verify(rechnungen).save(gespeicherte.capture());
    assertThat(gespeicherte.getValue())
        .satisfies(
            r -> assertThat(r.zustand()).isEqualTo(Rechnungszustand.BEZAHLT),
            r -> assertThat(r.nummer()).isEqualTo("R26-0001"),
            r -> assertThat(r.updatedAt()).isEqualTo(JETZT));
    assertThat(bezahlt.zustand()).isEqualTo(Rechnungszustand.BEZAHLT);
  }

  @Test
  void setze_aBezahlteRechnungBackToGestellt_thenWritten() {
    // Given
    when(rechnungen.findById(RECHNUNG))
        .thenReturn(Optional.of(gestellt().mitZustand(Rechnungszustand.BEZAHLT, JETZT)));
    when(rechnungen.save(any())).thenAnswer(aufruf -> aufruf.getArgument(0));

    // When
    final Rechnung zurueck = useCase.setze(RECHNUNG, Rechnungszustand.GESTELLT);

    // Then
    assertThat(zurueck.zustand()).isEqualTo(Rechnungszustand.GESTELLT);
  }

  @Test
  void setze_anEntwurf_thenRejectedByTheDomainAndNothingIsWritten() {
    // Given
    when(rechnungen.findById(RECHNUNG))
        .thenReturn(
            Optional.of(
                Rechnungsdoppel.entwurf(RECHNUNG, List.of(Rechnungsdoppel.beratung("80.00")))));

    // When / Then
    assertThatThrownBy(() -> useCase.setze(RECHNUNG, Rechnungszustand.BEZAHLT))
        .isInstanceOf(RechnungszustandPasstNicht.class);
    verify(rechnungen, never()).save(any());
  }
}
