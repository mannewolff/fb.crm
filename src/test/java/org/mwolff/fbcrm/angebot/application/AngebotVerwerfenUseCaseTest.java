package org.mwolff.fbcrm.angebot.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotszustand;

/**
 * Das Verwerfen eines Entwurfs (Kriterien 7, 13).
 *
 * <p>Der Entwurf verschwindet vollstaendig — samt Positionen und ohne Spur in der Historie; weil er
 * nie eine Nummer getragen hat, reisst er keine Luecke (E19). Jeder andere Zustand ist ein
 * festgeschriebener Beleg und wird nicht geloescht: Dann antwortet der Anwendungsfall 409 und
 * ruehrt den Bestand nicht an.
 */
@ExtendWith(MockitoExtension.class)
class AngebotVerwerfenUseCaseTest {

  private static final long ANGEBOT = 11L;

  @Mock private AngebotRepository angebote;

  private AngebotVerwerfenUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase = new AngebotVerwerfenUseCase(angebote);
  }

  @Test
  void verwirf_whenTheAngebotIsADraft_thenDeletesIt() {
    // Given — Kriterium 7.
    when(angebote.findById(ANGEBOT)).thenReturn(Optional.of(Angebotsdoppel.entwurf(ANGEBOT)));

    // When
    useCase.verwirf(ANGEBOT);

    // Then
    verify(angebote).loesche(ANGEBOT);
  }

  @ParameterizedTest
  @EnumSource(
      value = Angebotszustand.class,
      names = {"VERSENDET", "ANGENOMMEN", "ABGELEHNT", "ABGELOEST"})
  void verwirf_whenTheAngebotIsFestgeschrieben_thenRejectsAndWritesNothing(
      final Angebotszustand zustand) {
    // Given — Kriterium 13: ein versendeter Beleg verschwindet nicht.
    when(angebote.findById(ANGEBOT))
        .thenReturn(Optional.of(Angebotsdoppel.festgeschrieben(ANGEBOT, zustand)));

    // When / Then
    assertThatThrownBy(() -> useCase.verwirf(ANGEBOT)).isInstanceOf(AngebotNichtAenderbar.class);
    verify(angebote).findById(ANGEBOT);
    verifyNoMoreInteractions(angebote);
  }

  @Test
  void verwirf_whenTheAngebotIsUnknown_thenRejects() {
    // Given
    when(angebote.findById(ANGEBOT)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.verwirf(ANGEBOT)).isInstanceOf(AngebotNichtGefunden.class);
    verify(angebote).findById(ANGEBOT);
    verifyNoMoreInteractions(angebote);
  }
}
