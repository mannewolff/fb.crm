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
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;
import org.mwolff.fbcrm.common.Bearbeitungskonflikt;

/**
 * Der Statuswechsel um eine Stufe (Issue #127, Kriterium 4): gespeichert wird der neue Stand, an
 * den Enden nichts.
 *
 * <p>Dazu der Schutz vor dem alten Stand (#411, Kriterien 4 und 5; Plan #415, E9, E14): Ein Wechsel
 * auf einem Stand, den es nicht mehr gibt, weist vor jeder fachlichen Pruefung ab, und ein
 * gelungener traegt das handelnde Konto als Urheber.
 */
@ExtendWith(MockitoExtension.class)
class AngebotStatusUseCaseTest {

  private static final long ANGEBOT = 11L;
  private static final Instant JETZT = Instant.parse("2026-09-28T09:30:00Z");

  /** Der Stand, auf dem Ben das Angebot geoeffnet hat — der des frischen Angebots. */
  private static final long GELESEN = 0L;

  /** Das handelnde Konto. */
  private static final long BEN = 31L;

  /** Das Konto, das inzwischen geaendert hat. */
  private static final long ANNA = 30L;

  private static final Instant ANNAS_WECHSEL = Instant.parse("2026-09-28T08:42:00Z");

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
    final Angebot weiter = useCase.weiter(ANGEBOT, GELESEN, BEN);

    // Then
    assertThat(weiter.status()).isEqualTo(Angebotsstatus.ERLEDIGT);
    assertThat(weiter.updatedAt()).isEqualTo(JETZT);
    assertThat(weiter.geaendertVon()).isEqualTo(BEN);
  }

  @Test
  void zurueck_thenSavesThePreviousStatus() {
    // Given — Kriterium 4: der Status darf zurueckgesetzt werden.
    angebotIst(Angebotsstatus.ABGERECHNET);
    when(angebote.save(any())).thenAnswer(aufruf -> aufruf.getArgument(0));

    // When
    final Angebot zurueck = useCase.zurueck(ANGEBOT, GELESEN, BEN);

    // Then
    assertThat(zurueck.status()).isEqualTo(Angebotsstatus.ERLEDIGT);
    assertThat(zurueck.updatedAt()).isEqualTo(JETZT);
    assertThat(zurueck.geaendertVon()).isEqualTo(BEN);
  }

  @Test
  void weiter_givenTheCurrentStand_thenKeepsTheStandForTheRepository() {
    // Given — #411, Kriterium 8: auf dem aktuellen Stand merkt man vom Schutz nichts; erhoeht wird
    // der Stand erst beim Schreiben im Bestand.
    when(angebote.findById(ANGEBOT))
        .thenReturn(
            Optional.of(
                Angebotsdoppel.aufStand(
                    Angebotsdoppel.angebot(ANGEBOT, Angebotsstatus.ANGELEGT),
                    2L,
                    ANNA,
                    ANNAS_WECHSEL)));
    when(angebote.save(any())).thenAnswer(aufruf -> aufruf.getArgument(0));

    // When
    final Angebot weiter = useCase.weiter(ANGEBOT, 2L, BEN);

    // Then
    assertThat(weiter.version()).isEqualTo(2L);
    assertThat(weiter.geaendertVon()).isEqualTo(BEN);
  }

  @Test
  void weiter_givenAnOutdatedStand_thenRejectsWithTheConflictAndWritesNothing() {
    // Given — #411, Kriterium 5: Anna hat das Angebot inzwischen weitergeschaltet.
    angebotAufAnnasStand(Angebotsdoppel.angebot(ANGEBOT, Angebotsstatus.ABGEGEBEN));

    // When / Then — abgewiesen mit Urheber, Abgewiesenem und Zeitpunkt.
    assertThatThrownBy(() -> useCase.weiter(ANGEBOT, GELESEN, BEN))
        .asInstanceOf(InstanceOfAssertFactories.type(AngebotGeaendert.class))
        .satisfies(
            konflikt -> assertThat(konflikt.art()).isEqualTo(Bearbeitungskonflikt.Art.VERALTET),
            konflikt -> assertThat(konflikt.urheberKonto()).hasValue(ANNA),
            konflikt -> assertThat(konflikt.handelndesKonto()).isEqualTo(BEN),
            konflikt -> assertThat(konflikt.zeitpunkt()).hasValue(ANNAS_WECHSEL));
    verify(angebote).findById(ANGEBOT);
    verifyNoMoreInteractions(angebote);
  }

  @Test
  void zurueck_givenAnOutdatedStand_thenRejectsWithTheConflictAndWritesNothing() {
    // Given — dieselbe Lage in der anderen Richtung.
    angebotAufAnnasStand(Angebotsdoppel.angebot(ANGEBOT, Angebotsstatus.BESTELLT));

    // When / Then
    assertThatThrownBy(() -> useCase.zurueck(ANGEBOT, GELESEN, BEN))
        .isInstanceOf(AngebotGeaendert.class);
    verify(angebote).findById(ANGEBOT);
    verifyNoMoreInteractions(angebote);
  }

  @Test
  void weiter_givenAnOutdatedStandAtTheEnd_thenTheStandComesFirst() {
    // Given — Anna hat bis „abgerechnet" geschaltet; Ben erfaehrt zuerst, dass sie geaendert hat,
    // und nicht das Ende der Reihe, das sie herbeigefuehrt hat.
    angebotAufAnnasStand(Angebotsdoppel.angebot(ANGEBOT, Angebotsstatus.ABGERECHNET));

    // When / Then
    assertThatThrownBy(() -> useCase.weiter(ANGEBOT, GELESEN, BEN))
        .isInstanceOf(AngebotGeaendert.class);
  }

  @Test
  void zurueck_givenAnOutdatedStandOfAStornoedAngebot_thenTheStandComesFirst() {
    // Given — Anna hat storniert; auch das Storno steht hinter dem Stand.
    angebotAufAnnasStand(
        Angebotsdoppel.angebot(ANGEBOT, Angebotsstatus.BESTELLT).storniert(null, ANNAS_WECHSEL));

    // When / Then
    assertThatThrownBy(() -> useCase.zurueck(ANGEBOT, GELESEN, BEN))
        .isInstanceOf(AngebotGeaendert.class);
  }

  private void angebotAufAnnasStand(final Angebot angebot) {
    when(angebote.findById(ANGEBOT))
        .thenReturn(Optional.of(Angebotsdoppel.aufStand(angebot, 1L, ANNA, ANNAS_WECHSEL)));
  }

  @Test
  void weiter_givenTheLastStatus_thenRejectsAndWritesNothing() {
    // Given
    angebotIst(Angebotsstatus.ABGERECHNET);

    // When / Then
    assertThatThrownBy(() -> useCase.weiter(ANGEBOT, GELESEN, BEN))
        .isInstanceOf(StatusGrenzeErreicht.class);
    verify(angebote).findById(ANGEBOT);
    verifyNoMoreInteractions(angebote);
  }

  @Test
  void zurueck_givenTheFirstStatus_thenRejectsAndWritesNothing() {
    // Given
    angebotIst(Angebotsstatus.ANGELEGT);

    // When / Then
    assertThatThrownBy(() -> useCase.zurueck(ANGEBOT, GELESEN, BEN))
        .isInstanceOf(StatusGrenzeErreicht.class);
    verify(angebote).findById(ANGEBOT);
    verifyNoMoreInteractions(angebote);
  }

  @Test
  void weiter_givenAStornoedAngebot_thenRejectsAndWritesNothing() {
    // Given — Plan #332, E2: die Statuskette steht still, solange das Angebot storniert ist.
    when(angebote.findById(ANGEBOT))
        .thenReturn(
            Optional.of(
                Angebotsdoppel.angebot(ANGEBOT, Angebotsstatus.ABGEGEBEN).storniert(null, JETZT)));

    // When / Then
    assertThatThrownBy(() -> useCase.weiter(ANGEBOT, GELESEN, BEN))
        .isInstanceOf(AngebotStorniert.class);
    verify(angebote).findById(ANGEBOT);
    verifyNoMoreInteractions(angebote);
  }

  @Test
  void zurueck_givenAStornoedAngebot_thenRejectsAndWritesNothing() {
    // Given — E2, die andere Richtung.
    when(angebote.findById(ANGEBOT))
        .thenReturn(
            Optional.of(
                Angebotsdoppel.angebot(ANGEBOT, Angebotsstatus.BESTELLT).storniert(null, JETZT)));

    // When / Then
    assertThatThrownBy(() -> useCase.zurueck(ANGEBOT, GELESEN, BEN))
        .isInstanceOf(AngebotStorniert.class);
    verify(angebote).findById(ANGEBOT);
    verifyNoMoreInteractions(angebote);
  }

  @Test
  void weiter_whenTheAngebotIsUnknown_thenRejects() {
    // Given
    when(angebote.findById(ANGEBOT)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.weiter(ANGEBOT, GELESEN, BEN))
        .isInstanceOf(AngebotNichtGefunden.class);
  }

  @Test
  void zurueck_whenTheAngebotIsUnknown_thenRejects() {
    // Given
    when(angebote.findById(ANGEBOT)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.zurueck(ANGEBOT, GELESEN, BEN))
        .isInstanceOf(AngebotNichtGefunden.class);
  }
}
