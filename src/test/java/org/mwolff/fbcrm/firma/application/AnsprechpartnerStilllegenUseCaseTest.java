package org.mwolff.fbcrm.firma.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.firma.domain.Ansprechpartner;
import org.mwolff.fbcrm.firma.domain.AnsprechpartnerRepository;

/**
 * Stilllegen und Wiederaktivieren eines Ansprechpartners (Kriterium 15).
 *
 * <p>Beide Richtungen in einer Klasse, weil es dieselbe Umschaltung ist — wie bei der Firma. Auch
 * hier gilt Kriterium 12: Eine Kennung, die zu einer anderen Firma gehoert, ist unter dieser Firma
 * nicht da.
 */
@ExtendWith(MockitoExtension.class)
class AnsprechpartnerStilllegenUseCaseTest {

  private static final long FIRMA = 7L;
  private static final long ANDERE_FIRMA = 8L;
  private static final long ID = 3L;
  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");
  private static final Instant JETZT = Instant.parse("2026-09-25T09:30:00Z");

  @Mock private AnsprechpartnerRepository ansprechpartner;
  @Captor private ArgumentCaptor<Ansprechpartner> gespeicherte;

  private AnsprechpartnerStilllegenUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase =
        new AnsprechpartnerStilllegenUseCase(ansprechpartner, Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  private static Ansprechpartner bestand(final long firmaId, final boolean aktiv) {
    return new Ansprechpartner(
        ID, firmaId, "Max", "Mueller", null, null, null, null, aktiv, ANGELEGT, ANGELEGT);
  }

  private Ansprechpartner schalteUndFange(final Ansprechpartner vorher, final boolean stilllegen) {
    when(ansprechpartner.findById(ID)).thenReturn(Optional.of(vorher));
    when(ansprechpartner.save(any(Ansprechpartner.class)))
        .thenAnswer(aufruf -> aufruf.getArgument(0));
    if (stilllegen) {
      useCase.stilllegen(FIRMA, ID);
    } else {
      useCase.aktivieren(FIRMA, ID);
    }
    verify(ansprechpartner).save(gespeicherte.capture());
    return gespeicherte.getValue();
  }

  @Test
  void stilllegen_thenStoresTheContactAsRetired() {
    // When — Kriterium 15.
    final Ansprechpartner gespeichert = schalteUndFange(bestand(FIRMA, true), true);

    // Then
    assertThat(gespeichert.aktiv()).isFalse();
  }

  @Test
  void stilllegen_thenTakesTheChangeTimeFromTheClock() {
    // When
    final Ansprechpartner gespeichert = schalteUndFange(bestand(FIRMA, true), true);

    // Then
    assertThat(gespeichert.updatedAt()).isEqualTo(JETZT);
  }

  @Test
  void aktivieren_thenStoresTheContactAsActive() {
    // When — Kriterium 15: der Weg zurueck.
    final Ansprechpartner gespeichert = schalteUndFange(bestand(FIRMA, false), false);

    // Then
    assertThat(gespeichert.aktiv()).isTrue();
  }

  @Test
  void aktivieren_thenTakesTheChangeTimeFromTheClock() {
    // When
    final Ansprechpartner gespeichert = schalteUndFange(bestand(FIRMA, false), false);

    // Then
    assertThat(gespeichert.updatedAt()).isEqualTo(JETZT);
  }

  @Test
  void stilllegen_givenAnUnknownId_thenThrowsAnsprechpartnerNichtGefunden() {
    // Given
    when(ansprechpartner.findById(ID)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.stilllegen(FIRMA, ID))
        .isInstanceOf(AnsprechpartnerNichtGefunden.class);
  }

  @Test
  void aktivieren_givenAnUnknownId_thenThrowsAnsprechpartnerNichtGefunden() {
    // Given
    when(ansprechpartner.findById(ID)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.aktivieren(FIRMA, ID))
        .isInstanceOf(AnsprechpartnerNichtGefunden.class);
  }

  @Test
  void stilllegen_givenAnIdOfAnotherFirma_thenThrowsAnsprechpartnerNichtGefunden() {
    // Given — Kriterium 12.
    when(ansprechpartner.findById(ID)).thenReturn(Optional.of(bestand(ANDERE_FIRMA, true)));

    // When / Then
    assertThatThrownBy(() -> useCase.stilllegen(FIRMA, ID))
        .isInstanceOf(AnsprechpartnerNichtGefunden.class);
  }

  @Test
  void aktivieren_givenAnIdOfAnotherFirma_thenThrowsAnsprechpartnerNichtGefunden() {
    // Given
    when(ansprechpartner.findById(ID)).thenReturn(Optional.of(bestand(ANDERE_FIRMA, false)));

    // When / Then
    assertThatThrownBy(() -> useCase.aktivieren(FIRMA, ID))
        .isInstanceOf(AnsprechpartnerNichtGefunden.class);
  }

  @Test
  void stilllegen_givenAnIdOfAnotherFirma_thenStoresNothing() {
    // Given
    when(ansprechpartner.findById(ID)).thenReturn(Optional.of(bestand(ANDERE_FIRMA, true)));

    // When
    assertThatThrownBy(() -> useCase.stilllegen(FIRMA, ID))
        .isInstanceOf(AnsprechpartnerNichtGefunden.class);

    // Then
    verify(ansprechpartner, never()).save(any(Ansprechpartner.class));
  }
}
