package org.mwolff.fbcrm.vorgang.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.vorgang.domain.Eintrag;
import org.mwolff.fbcrm.vorgang.domain.Eintragsart;
import org.mwolff.fbcrm.vorgang.domain.Herkunft;

/**
 * Das Vermerken eines Ereignisses in der Historie (Kriterium 19).
 *
 * <p>Der Anwendungsfall hat keinen Controller — er ist der Weg, den ein anderes Modul nimmt, wenn
 * ein Dokument seinen Zustand wechselt. Geprueft wird deshalb, was in der Zeile steht: die Art, der
 * Text, der Zeitpunkt und die Herkunft, die ihn von einem Kommentar unterscheidet.
 *
 * <p>Die Uhr steht fest. Ein Ereignis geschieht in dem Augenblick, in dem es vermerkt wird; ohne
 * bekannte Gegenwart waere das keine pruefbare Aussage.
 */
class EreignisVermerkenUseCaseTest {

  private static final Instant JETZT = Instant.parse("2026-09-12T09:00:00Z");
  private static final long VORGANG_ID = 1L;

  private final List<String> protokoll = new ArrayList<>();
  private final Ports.Eintraege eintraege = new Ports.Eintraege(protokoll);

  private EreignisVermerkenUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase = new EreignisVermerkenUseCase(eintraege, Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  @Test
  void vermerken_thenWritesTheEventWithTextAndMoment() {
    // Given — Kriterium 19.

    // When
    useCase.vermerken(VORGANG_ID, "Angebot versandt.");

    // Then
    assertThat(eintraege.alle())
        .singleElement()
        .extracting(Eintrag::vorgangId, Eintrag::art, Eintrag::text, Eintrag::geschehenAm)
        .containsExactly(
            Long.valueOf(VORGANG_ID), Eintragsart.EREIGNIS, "Angebot versandt.", JETZT);
  }

  @Test
  void vermerken_thenTheOriginIsAutomatic() {
    // Given — daran unterscheidet die Ansicht ein Ereignis von einem eigenen Kommentar.

    // When
    useCase.vermerken(VORGANG_ID, "Angebot versandt.");

    // Then
    assertThat(eintraege.alle())
        .singleElement()
        .extracting(Eintrag::herkunft)
        .isEqualTo(Herkunft.AUTOMATISCH);
  }

  @Test
  void vermerken_thenRecordsTheMomentOfEntryAsWell() {
    // Given — ein Ereignis geschieht, wenn es vermerkt wird; beide Zeitpunkte sind derselbe.

    // When
    useCase.vermerken(VORGANG_ID, "Angebot versandt.");

    // Then
    assertThat(eintraege.alle()).singleElement().extracting(Eintrag::createdAt).isEqualTo(JETZT);
  }

  @Test
  void vermerken_givenABlankText_thenRefusesAndWritesNothing() {
    // Given — die Schranke der Domaene gilt auch fuer den Aufrufer aus einem anderen Modul.

    // When / Then
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> useCase.vermerken(VORGANG_ID, "   "));
    assertThat(protokoll).isEmpty();
  }
}
