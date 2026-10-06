package org.mwolff.fbcrm.rechnung.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.rechnung.domain.NachgetrageneRechnung;
import org.mwolff.fbcrm.rechnung.domain.NachgetrageneRechnungRepository;
import org.mwolff.fbcrm.rechnung.domain.Rechnungszustand;

/**
 * Der Ausgang einer nachgetragenen Rechnung: bezahlt, abgeschrieben, zurueck auf gestellt (#254,
 * Kriterium 8; Plan #259, E4, E24).
 *
 * <p>Die Kantenregel steht in {@link Rechnungszustand#ausgangswechselErlaubt}; hier wird gezeigt,
 * dass der Anwendungsfall sie ueber das Aggregat erreicht, den Zeitpunkt aus der Uhr nimmt und bei
 * einer Abweisung nichts schreibt.
 */
@ExtendWith(MockitoExtension.class)
class NachtragZustandSetzenUseCaseTest {

  private static final long ID = 21L;
  private static final Instant ANGELEGT = Instant.parse("2026-03-02T08:00:00Z");
  private static final Instant JETZT = Instant.parse("2026-10-20T07:30:00Z");

  @Mock private NachgetrageneRechnungRepository nachgetragene;

  @Captor private ArgumentCaptor<NachgetrageneRechnung> gespeicherte;

  private NachtragZustandSetzenUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase = new NachtragZustandSetzenUseCase(nachgetragene, Clock.fixed(JETZT, ZoneOffset.UTC));
    lenient().when(nachgetragene.save(any())).thenAnswer(aufruf -> aufruf.getArgument(0));
  }

  private void rechnungSteht(final Rechnungszustand zustand) {
    when(nachgetragene.findById(ID))
        .thenReturn(
            Optional.of(
                new NachgetrageneRechnung(
                    ID,
                    4L,
                    "RE-1",
                    LocalDate.of(2026, 3, 1),
                    new BigDecimal("1000.00"),
                    new BigDecimal("1190.00"),
                    zustand,
                    null,
                    ANGELEGT,
                    ANGELEGT)));
  }

  @ParameterizedTest
  @CsvSource({
    "GESTELLT, BEZAHLT",
    "GESTELLT, ABGESCHRIEBEN",
    "BEZAHLT, GESTELLT",
    "ABGESCHRIEBEN, GESTELLT"
  })
  void setze_alongAnAllowedEdge_thenWrittenWithTheMomentFromTheClock(
      final Rechnungszustand von, final Rechnungszustand nach) {
    // Given
    rechnungSteht(von);

    // When
    final NachgetrageneRechnung umgestellt = useCase.setze(ID, nach);

    // Then
    verify(nachgetragene).save(gespeicherte.capture());
    assertThat(gespeicherte.getValue())
        .satisfies(
            r -> assertThat(r.id()).isEqualTo(ID),
            r -> assertThat(r.zustand()).isEqualTo(nach),
            r -> assertThat(r.nummer()).isEqualTo("RE-1"),
            r -> assertThat(r.createdAt()).isEqualTo(ANGELEGT),
            r -> assertThat(r.updatedAt()).isEqualTo(JETZT));
    assertThat(umgestellt).isEqualTo(gespeicherte.getValue());
  }

  @ParameterizedTest
  @CsvSource({"BEZAHLT, ABGESCHRIEBEN", "ABGESCHRIEBEN, BEZAHLT"})
  void setze_directlyBetweenTheTwoOutcomes_thenRechnungszustandPasstNicht(
      final Rechnungszustand von, final Rechnungszustand nach) {
    // Given — der Weg fuehrt ueber „gestellt"
    rechnungSteht(von);

    // When / Then
    assertThatThrownBy(() -> useCase.setze(ID, nach))
        .isInstanceOf(RechnungszustandPasstNicht.class);
    verify(nachgetragene, never()).save(any());
  }

  @ParameterizedTest
  @EnumSource(
      value = Rechnungszustand.class,
      names = {"GESTELLT", "BEZAHLT", "ABGESCHRIEBEN"})
  void setze_toTheCurrentZustand_thenRechnungszustandPasstNicht(final Rechnungszustand zustand) {
    // Given
    rechnungSteht(zustand);

    // When / Then
    assertThatThrownBy(() -> useCase.setze(ID, zustand))
        .isInstanceOf(RechnungszustandPasstNicht.class);
    verify(nachgetragene, never()).save(any());
  }

  @Test
  void setze_toEntwurf_thenRechnungszustandPasstNicht() {
    // Given — einen Entwurf gibt es bei der nachgetragenen Rechnung nicht (Kriterium 8)
    rechnungSteht(Rechnungszustand.GESTELLT);

    // When / Then
    assertThatThrownBy(() -> useCase.setze(ID, Rechnungszustand.ENTWURF))
        .isInstanceOf(RechnungszustandPasstNicht.class);
    verify(nachgetragene, never()).save(any());
  }

  @Test
  void setze_withAnUnknownRechnung_thenNachtragNichtGefunden() {
    // Given
    when(nachgetragene.findById(ID)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.setze(ID, Rechnungszustand.BEZAHLT))
        .isInstanceOf(NachtragNichtGefunden.class);
    verify(nachgetragene, never()).save(any());
  }
}
