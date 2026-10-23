package org.mwolff.fbcrm.rechnung.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
 * Der Ausgang einer gestellten Rechnung: bezahlt, abgeschrieben, zurueck auf gestellt (Issue #253).
 *
 * <p>Der Anwendungsfall entscheidet nichts ueber die Zulaessigkeit — das tut {@code
 * Rechnung#mitZustand}. Gegenstand ist darum, <b>dass</b> er liest, umstellt und schreibt, dass der
 * Zeitpunkt aus der Uhr kommt, dass eine unbekannte Rechnung 404 ergibt und dass eine Abweisung der
 * Domaene nichts schreibt.
 *
 * <p>Dazu der Schutz vor dem alten Stand (#411, Kriterien 4 und 5; Plan #415, E1, E14): Auch
 * „bezahlt" und „abgeschrieben" weisen auf einem Stand, den es nicht mehr gibt, vor der Kantenregel
 * ab, und ein gelungener Wechsel traegt das handelnde Konto als Urheber.
 */
@ExtendWith(MockitoExtension.class)
class RechnungZustandSetzenUseCaseTest {

  private static final long RECHNUNG = 2L;
  private static final Instant JETZT = Instant.parse("2026-10-20T07:30:00Z");

  /** Der Stand, auf dem Manfred die Rechnung geoeffnet hat — der der frischen Rechnung. */
  private static final long GELESEN = 0L;

  /** Das handelnde Konto. */
  private static final long MANFRED = 31L;

  /** Das Konto, das inzwischen geaendert hat. */
  private static final long ANNA = 30L;

  private static final Instant ANNAS_WECHSEL = Instant.parse("2026-10-20T07:12:00Z");

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
    assertThatThrownBy(() -> useCase.setze(RECHNUNG, Rechnungszustand.BEZAHLT, GELESEN, MANFRED))
        .isInstanceOf(RechnungNichtGefunden.class);
    verify(rechnungen, never()).save(any());
  }

  @Test
  void setze_aGestellteRechnungToBezahlt_thenWrittenWithTheMomentFromTheClock() {
    // Given
    when(rechnungen.findById(RECHNUNG)).thenReturn(Optional.of(gestellt()));
    when(rechnungen.save(any())).thenAnswer(aufruf -> aufruf.getArgument(0));

    // When
    final Rechnung bezahlt = useCase.setze(RECHNUNG, Rechnungszustand.BEZAHLT, GELESEN, MANFRED);

    // Then
    verify(rechnungen).save(gespeicherte.capture());
    assertThat(gespeicherte.getValue())
        .satisfies(
            r -> assertThat(r.zustand()).isEqualTo(Rechnungszustand.BEZAHLT),
            r -> assertThat(r.nummer()).isEqualTo("R26-0001"),
            r -> assertThat(r.updatedAt()).isEqualTo(JETZT),
            r -> assertThat(r.geaendertVon()).isEqualTo(MANFRED));
    assertThat(bezahlt.zustand()).isEqualTo(Rechnungszustand.BEZAHLT);
  }

  @Test
  void setze_aBezahlteRechnungBackToGestellt_thenWritten() {
    // Given
    when(rechnungen.findById(RECHNUNG))
        .thenReturn(Optional.of(gestellt().mitZustand(Rechnungszustand.BEZAHLT, JETZT)));
    when(rechnungen.save(any())).thenAnswer(aufruf -> aufruf.getArgument(0));

    // When
    final Rechnung zurueck = useCase.setze(RECHNUNG, Rechnungszustand.GESTELLT, GELESEN, MANFRED);

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
    assertThatThrownBy(() -> useCase.setze(RECHNUNG, Rechnungszustand.BEZAHLT, GELESEN, MANFRED))
        .isInstanceOf(RechnungszustandPasstNicht.class);
    verify(rechnungen, never()).save(any());
  }

  @Test
  void setze_givenTheCurrentStand_thenKeepsTheStandForTheRepository() {
    // Given — #411, Kriterium 8: auf dem aktuellen Stand merkt man vom Schutz nichts; erhoeht wird
    // der Stand erst beim Schreiben im Bestand.
    when(rechnungen.findById(RECHNUNG))
        .thenReturn(Optional.of(Rechnungsdoppel.aufStand(gestellt(), 3L, ANNA, ANNAS_WECHSEL)));
    when(rechnungen.save(any())).thenAnswer(aufruf -> aufruf.getArgument(0));

    // When
    final Rechnung abgeschrieben =
        useCase.setze(RECHNUNG, Rechnungszustand.ABGESCHRIEBEN, 3L, MANFRED);

    // Then
    assertThat(abgeschrieben.version()).isEqualTo(3L);
    assertThat(abgeschrieben.geaendertVon()).isEqualTo(MANFRED);
  }

  @Test
  void setze_givenAnOutdatedStand_thenRejectsWithTheConflictAndWritesNothing() {
    // Given — #411, Kriterium 5: Anna hat die Rechnung inzwischen auf bezahlt gesetzt.
    when(rechnungen.findById(RECHNUNG))
        .thenReturn(
            Optional.of(
                Rechnungsdoppel.aufStand(
                    gestellt().mitZustand(Rechnungszustand.BEZAHLT, ANNAS_WECHSEL),
                    1L,
                    ANNA,
                    ANNAS_WECHSEL)));

    // When / Then — der Konflikt kommt vor der Kantenregel: BEZAHLT nach BEZAHLT waere keine Kante.
    assertThatThrownBy(() -> useCase.setze(RECHNUNG, Rechnungszustand.BEZAHLT, GELESEN, MANFRED))
        .asInstanceOf(InstanceOfAssertFactories.type(RechnungGeaendert.class))
        .satisfies(
            konflikt -> assertThat(konflikt.art()).isEqualTo(Bearbeitungskonflikt.Art.VERALTET),
            konflikt -> assertThat(konflikt.urheberKonto()).hasValue(ANNA),
            konflikt -> assertThat(konflikt.handelndesKonto()).isEqualTo(MANFRED),
            konflikt -> assertThat(konflikt.zeitpunkt()).hasValue(ANNAS_WECHSEL));
    verify(rechnungen).findById(RECHNUNG);
    verifyNoMoreInteractions(rechnungen);
  }
}
