package org.mwolff.fbcrm.firma.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.firma.domain.Anschrift;
import org.mwolff.fbcrm.firma.domain.Ansprechpartner;
import org.mwolff.fbcrm.firma.domain.AnsprechpartnerRepository;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;

/**
 * Die Detailansicht einer Firma samt ihrer Ansprechpartner (Kriterium 10).
 *
 * <p>Aktive und stillgelegte Ansprechpartner kommen gemeinsam — welche davon wo erscheinen,
 * entscheidet die Oberflaeche, nicht die Schnittstelle. Einen eigenen Weg {@code GET
 * …/ansprechpartner} gibt es deshalb nicht.
 */
@ExtendWith(MockitoExtension.class)
class FirmaLesenUseCaseTest {

  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");

  @Mock private FirmaRepository firmen;
  @Mock private AnsprechpartnerRepository ansprechpartner;

  private FirmaLesenUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase = new FirmaLesenUseCase(firmen, ansprechpartner);
  }

  private static Firma adlerAg() {
    return new Firma(
        7L,
        "Adler AG",
        new Anschrift("Am Wall 1", "28195", "Bremen", "Deutschland"),
        "75/123/45678",
        "DE123456789",
        true,
        ANGELEGT,
        ANGELEGT);
  }

  private static Ansprechpartner partner(
      final long id, final String nachname, final boolean aktiv) {
    return new Ansprechpartner(
        id, 7L, "Max", nachname, null, null, null, null, aktiv, ANGELEGT, ANGELEGT);
  }

  @Test
  void lese_thenReturnsTheFirma() {
    // Given
    when(firmen.findById(7L)).thenReturn(Optional.of(adlerAg()));
    when(ansprechpartner.findByFirma(7L)).thenReturn(List.of());

    // When
    final FirmaMitAnsprechpartnern gelesen = useCase.lese(7L);

    // Then
    assertThat(gelesen.firma()).isEqualTo(adlerAg());
  }

  @Test
  void lese_thenReturnsActiveAndRetiredContactsTogether() {
    // Given
    when(firmen.findById(7L)).thenReturn(Optional.of(adlerAg()));
    when(ansprechpartner.findByFirma(7L))
        .thenReturn(List.of(partner(1L, "Adler", true), partner(2L, "Bohnsack", false)));

    // When
    final FirmaMitAnsprechpartnern gelesen = useCase.lese(7L);

    // Then — Kriterium 10: die Trennung macht die Oberflaeche, nicht die Schnittstelle.
    assertThat(gelesen.ansprechpartner())
        .containsExactly(partner(1L, "Adler", true), partner(2L, "Bohnsack", false));
  }

  @Test
  void lese_givenAnUnknownId_thenThrowsFirmaNichtGefunden() {
    // Given
    when(firmen.findById(4711L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.lese(4711L)).isInstanceOf(FirmaNichtGefunden.class);
  }

  @Test
  void lese_givenAnUnknownId_thenNeverAsksForContacts() {
    // Given
    when(firmen.findById(4711L)).thenReturn(Optional.empty());

    // When
    assertThatThrownBy(() -> useCase.lese(4711L)).isInstanceOf(FirmaNichtGefunden.class);

    // Then
    verifyNoInteractions(ansprechpartner);
  }
}
