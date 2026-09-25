package org.mwolff.fbcrm.vorgang.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.vorgang.application.VorgaengeDerFirma;
import org.mwolff.fbcrm.vorgang.application.VorgaengeDerFirmaUseCase;
import org.mwolff.fbcrm.vorgang.application.VorgangZeile;
import org.mwolff.fbcrm.vorgang.domain.Phase;

/**
 * Die Uebersetzung zwischen Anwendungsfall und HTTP fuer die Vorgaenge einer Firma (Kriterium 12).
 *
 * <p>Der Weg liegt unter {@code /api/firmen/...}, gehalten wird er aber von diesem Modul (E2): So
 * kommt die Liste in die Detailansicht der Firma, ohne dass {@code firma} etwas vom Vorgang weiss.
 */
@ExtendWith(MockitoExtension.class)
class FirmaVorgaengeControllerTest {

  private static final Instant GESCHEHEN = Instant.parse("2026-09-12T09:00:00Z");

  @Mock private VorgaengeDerFirmaUseCase vorgaenge;

  private FirmaVorgaengeController controller;

  @BeforeEach
  void baueDenController() {
    controller = new FirmaVorgaengeController(vorgaenge);
  }

  private static VorgangZeile zeile(final long id, final long nummer, final boolean abgeschlossen) {
    return new VorgangZeile(
        id, nummer, "Website-Relaunch", "Adler AG", Phase.ANBAHNUNG, abgeschlossen, GESCHEHEN);
  }

  @Test
  void vorgaenge_thenAnswersWithBothListsSeparately() {
    // Given
    when(vorgaenge.vorgaenge(7L))
        .thenReturn(
            new VorgaengeDerFirma(List.of(zeile(4L, 12L, false)), List.of(zeile(5L, 13L, true))));

    // When
    final VorgaengeDerFirmaResponse antwort = controller.vorgaenge(7L);

    // Then
    assertThat(antwort)
        .isEqualTo(
            new VorgaengeDerFirmaResponse(
                List.of(
                    new VorgangZeileResponse(
                        4L,
                        12L,
                        "Website-Relaunch",
                        "Adler AG",
                        Phase.ANBAHNUNG,
                        false,
                        GESCHEHEN)),
                List.of(
                    new VorgangZeileResponse(
                        5L,
                        13L,
                        "Website-Relaunch",
                        "Adler AG",
                        Phase.ANBAHNUNG,
                        true,
                        GESCHEHEN))));
  }

  @Test
  void vorgaenge_givenAFirmaWithoutAnyVorgang_thenAnswersWithTwoEmptyLists() {
    // Given — Kriterium 12.
    when(vorgaenge.vorgaenge(7L)).thenReturn(new VorgaengeDerFirma(List.of(), List.of()));

    // When
    final VorgaengeDerFirmaResponse antwort = controller.vorgaenge(7L);

    // Then
    assertThat(antwort).isEqualTo(new VorgaengeDerFirmaResponse(List.of(), List.of()));
  }
}
