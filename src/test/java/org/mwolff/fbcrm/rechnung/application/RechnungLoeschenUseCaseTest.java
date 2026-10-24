package org.mwolff.fbcrm.rechnung.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.common.Bearbeitungskonflikt;
import org.mwolff.fbcrm.rechnung.domain.Rechnung;
import org.mwolff.fbcrm.rechnung.domain.RechnungRepository;
import org.mwolff.fbcrm.rechnung.domain.Rechnungszustand;

/**
 * Das Loeschen eines Rechnungsentwurfs (#160, Kriterien 12 und 14).
 *
 * <p>Ein Entwurf, aus dem nichts wird, verschwindet — er hat nie eine Nummer getragen und reisst
 * keine Luecke. Eine gestellte Rechnung dagegen bleibt: Sie zu loeschen risse ein Loch in den
 * Nummernkreis.
 *
 * <p>Verschwinden heisst gekennzeichnet (#411, Kriterium 6; Plan #415, E16, E17): Die Zeile bleibt
 * mit Zeitpunkt und Konto, die Positionen fallen weg. Ein inzwischen geaenderter Entwurf wird nicht
 * geloescht, ein schon geloeschter nicht ein zweites Mal — und in beiden Faellen wird nichts
 * geschrieben.
 */
@ExtendWith(MockitoExtension.class)
class RechnungLoeschenUseCaseTest {

  private static final long RECHNUNG = 2L;
  private static final Instant JETZT = Instant.parse("2026-10-05T09:00:00Z");

  /** Der Stand, auf dem Manfred den Entwurf geoeffnet hat. */
  private static final long GELESEN = 0L;

  /** Das handelnde Konto. */
  private static final long MANFRED = 31L;

  /** Das Konto, das inzwischen geaendert oder geloescht hat. */
  private static final long ANNA = 30L;

  private static final Instant ANNAS_SCHRITT = Instant.parse("2026-10-05T08:42:00Z");

  @Mock private RechnungRepository rechnungen;

  @Captor private ArgumentCaptor<Rechnung> geschrieben;

  private RechnungLoeschenUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase = new RechnungLoeschenUseCase(rechnungen, Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  @Test
  void loesche_withAnUnknownRechnung_thenNotFound() {
    // Given
    when(rechnungen.findById(RECHNUNG)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.loesche(RECHNUNG, GELESEN, MANFRED))
        .isInstanceOf(RechnungNichtGefunden.class);
    verify(rechnungen).findById(RECHNUNG);
    verifyNoMoreInteractions(rechnungen);
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
    assertThatThrownBy(() -> useCase.loesche(RECHNUNG, GELESEN, MANFRED))
        .isInstanceOf(RechnungszustandPasstNicht.class);
    verify(rechnungen).findById(RECHNUNG);
    verifyNoMoreInteractions(rechnungen);
  }

  @Test
  void loesche_anEntwurf_thenItIsMarkedWithoutItsPositionen() {
    // Given
    when(rechnungen.findById(RECHNUNG))
        .thenReturn(
            Optional.of(
                Rechnungsdoppel.entwurf(RECHNUNG, List.of(Rechnungsdoppel.beratung("80.00")))));

    // When
    useCase.loesche(RECHNUNG, GELESEN, MANFRED);

    // Then
    verify(rechnungen).save(geschrieben.capture());
    assertThat(geschrieben.getValue())
        .satisfies(
            rechnung -> assertThat(rechnung.id()).isEqualTo(RECHNUNG),
            rechnung -> assertThat(rechnung.zustand()).isEqualTo(Rechnungszustand.ENTWURF),
            rechnung -> assertThat(rechnung.geloeschtAm()).isEqualTo(JETZT),
            rechnung -> assertThat(rechnung.geloeschtVon()).isEqualTo(MANFRED),
            rechnung -> assertThat(rechnung.positionen()).isEmpty(),
            rechnung -> assertThat(rechnung.version()).isEqualTo(GELESEN));
  }

  @Test
  void loesche_givenAnOutdatedStand_thenRejectsAndRemovesNothing() {
    // Given — Anna hat den Entwurf inzwischen geaendert.
    when(rechnungen.findById(RECHNUNG))
        .thenReturn(
            Optional.of(
                Rechnungsdoppel.aufStand(
                    Rechnungsdoppel.entwurf(RECHNUNG, List.of(Rechnungsdoppel.beratung("80.00"))),
                    1L,
                    ANNA,
                    ANNAS_SCHRITT)));

    // When / Then
    assertThatThrownBy(() -> useCase.loesche(RECHNUNG, GELESEN, MANFRED))
        .asInstanceOf(InstanceOfAssertFactories.type(RechnungGeaendert.class))
        .satisfies(
            konflikt -> assertThat(konflikt.art()).isEqualTo(Bearbeitungskonflikt.Art.VERALTET),
            konflikt -> assertThat(konflikt.urheberKonto()).hasValue(ANNA),
            konflikt -> assertThat(konflikt.handelndesKonto()).isEqualTo(MANFRED),
            konflikt -> assertThat(konflikt.zeitpunkt()).hasValue(ANNAS_SCHRITT));
    verify(rechnungen).findById(RECHNUNG);
    verifyNoMoreInteractions(rechnungen);
  }

  @Test
  void loesche_givenAnAlreadyDeletedEntwurf_thenRejectsAsGeloeschtAndWritesNothing() {
    // Given — Anna hat den Entwurf inzwischen geloescht; der vorgelegte Stand ist sogar der
    // aktuelle.
    when(rechnungen.findById(RECHNUNG))
        .thenReturn(Optional.of(Rechnungsdoppel.geloescht(RECHNUNG, ANNA, ANNAS_SCHRITT)));

    // When / Then
    assertThatThrownBy(() -> useCase.loesche(RECHNUNG, GELESEN, MANFRED))
        .asInstanceOf(InstanceOfAssertFactories.type(RechnungGeloescht.class))
        .satisfies(
            konflikt -> assertThat(konflikt.art()).isEqualTo(Bearbeitungskonflikt.Art.GELOESCHT),
            konflikt -> assertThat(konflikt.urheberKonto()).hasValue(ANNA),
            konflikt -> assertThat(konflikt.handelndesKonto()).isEqualTo(MANFRED),
            konflikt -> assertThat(konflikt.zeitpunkt()).hasValue(ANNAS_SCHRITT));
    verify(rechnungen).findById(RECHNUNG);
    verifyNoMoreInteractions(rechnungen);
  }
}
