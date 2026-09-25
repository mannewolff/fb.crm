package org.mwolff.fbcrm.firma.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.firma.domain.Anschrift;
import org.mwolff.fbcrm.firma.domain.AnsprechpartnerRepository;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;

/**
 * Die Uebersicht der Firmen (Kriterien 2, 3, 6, 13).
 *
 * <p>Der tragende Nachweis steht in {@code uebersicht_givenAFilter_thenGesamtCountsEveryFirma}:
 * {@code gesamt} ist die Zahl <b>aller</b> Firmen und nicht die der Zeilen — nur so kann die
 * Oberflaeche „noch keine Firma" von „nichts gefunden" unterscheiden (E5).
 */
@ExtendWith(MockitoExtension.class)
class FirmenUebersichtUseCaseTest {

  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");

  @Mock private FirmaRepository firmen;
  @Mock private AnsprechpartnerRepository ansprechpartner;

  private FirmenUebersichtUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase = new FirmenUebersichtUseCase(firmen, ansprechpartner);
  }

  private static Firma firma(
      final long id, final String name, final String ort, final boolean aktiv) {
    return new Firma(
        id,
        name,
        new Anschrift("Am Wall 1", "28195", ort, "Deutschland"),
        null,
        null,
        aktiv,
        ANGELEGT,
        ANGELEGT);
  }

  @Test
  void uebersicht_thenCarriesNameOrtActiveContactsAndRetirementPerRow() {
    // Given
    when(firmen.uebersicht("", true))
        .thenReturn(
            List.of(
                firma(1L, "Adler AG", "Bremen", true), firma(2L, "Baum GmbH", "Hamburg", false)));
    when(ansprechpartner.zaehleAktiveJeFirma(List.of(1L, 2L))).thenReturn(Map.of(1L, 3L, 2L, 0L));
    when(firmen.zaehleAlle()).thenReturn(2L);

    // When
    final FirmenUebersicht uebersicht = useCase.uebersicht("", true);

    // Then
    assertThat(uebersicht.zeilen())
        .containsExactly(
            new FirmaZeile(1L, "Adler AG", "Bremen", 3L, true),
            new FirmaZeile(2L, "Baum GmbH", "Hamburg", 0L, false));
  }

  @Test
  void uebersicht_thenKeepsTheOrderOfTheRepository() {
    // Given — sortiert wird im Bestand (E6); die Uebersicht sortiert nicht nach.
    when(firmen.uebersicht("", false))
        .thenReturn(
            List.of(firma(9L, "Adler AG", "Bremen", true), firma(3L, "beta ag", "Kiel", true)));
    when(ansprechpartner.zaehleAktiveJeFirma(List.of(9L, 3L))).thenReturn(Map.of(9L, 0L, 3L, 0L));
    when(firmen.zaehleAlle()).thenReturn(2L);

    // When
    final FirmenUebersicht uebersicht = useCase.uebersicht("", false);

    // Then
    assertThat(uebersicht.zeilen()).extracting(FirmaZeile::id).containsExactly(9L, 3L);
  }

  @Test
  void uebersicht_givenAFirmaWithoutAnOrt_thenLeavesTheOrtAbsent() {
    // Given
    when(firmen.uebersicht("", false))
        .thenReturn(
            List.of(
                new Firma(
                    1L,
                    "Adler AG",
                    new Anschrift(null, null, null, null),
                    null,
                    null,
                    true,
                    ANGELEGT,
                    ANGELEGT)));
    when(ansprechpartner.zaehleAktiveJeFirma(List.of(1L))).thenReturn(Map.of(1L, 0L));
    when(firmen.zaehleAlle()).thenReturn(1L);

    // When
    final FirmenUebersicht uebersicht = useCase.uebersicht("", false);

    // Then
    assertThat(uebersicht.zeilen()).singleElement().extracting(FirmaZeile::ort).isNull();
  }

  @Test
  void uebersicht_givenAFilter_thenGesamtCountsEveryFirma() {
    // Given — eine Zeile im Ergebnis, sieben Firmen im Bestand.
    when(firmen.uebersicht("adler", false))
        .thenReturn(List.of(firma(1L, "Adler AG", "Bremen", true)));
    when(ansprechpartner.zaehleAktiveJeFirma(List.of(1L))).thenReturn(Map.of(1L, 0L));
    when(firmen.zaehleAlle()).thenReturn(7L);

    // When
    final FirmenUebersicht uebersicht = useCase.uebersicht("adler", false);

    // Then — E5: gesamt ist nicht die Zahl der Zeilen.
    assertThat(uebersicht.gesamt()).isEqualTo(7L);
  }

  @Test
  void uebersicht_givenAnEmptyResult_thenHasNoRows() {
    // Given
    when(firmen.uebersicht("nichts", true)).thenReturn(List.of());
    when(ansprechpartner.zaehleAktiveJeFirma(List.of())).thenReturn(Map.of());
    when(firmen.zaehleAlle()).thenReturn(4L);

    // When
    final FirmenUebersicht uebersicht = useCase.uebersicht("nichts", true);

    // Then
    assertThat(uebersicht.zeilen()).isEmpty();
  }

  @Test
  void uebersicht_givenAnEmptyDatabase_thenGesamtIsZero() {
    // Given
    when(firmen.uebersicht("", false)).thenReturn(List.of());
    when(ansprechpartner.zaehleAktiveJeFirma(List.of())).thenReturn(Map.of());
    when(firmen.zaehleAlle()).thenReturn(0L);

    // When
    final FirmenUebersicht uebersicht = useCase.uebersicht("", false);

    // Then — Kriterium 2: daran erkennt die Oberflaeche „noch keine Firma".
    assertThat(uebersicht.gesamt()).isZero();
  }
}
