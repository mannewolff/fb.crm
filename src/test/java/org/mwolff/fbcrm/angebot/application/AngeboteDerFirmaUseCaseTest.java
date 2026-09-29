package org.mwolff.fbcrm.angebot.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;

/**
 * Die Angebotsliste einer Firma (Issue #127, Kriterium 7).
 *
 * <p>Gegenstand ist die Reihenfolge: das Angebotsdatum absteigend, bei gleichem Datum die hoehere
 * Kennung zuerst, damit zwei Aufrufe dieselbe Liste liefern. Der Status spielt fuer die Reihenfolge
 * keine Rolle.
 */
@ExtendWith(MockitoExtension.class)
class AngeboteDerFirmaUseCaseTest {

  private static final LocalDate FRUEH = LocalDate.of(2026, 9, 20);
  private static final LocalDate SPAET = LocalDate.of(2026, 9, 25);

  @Mock private AngebotRepository angebote;

  private AngeboteDerFirmaUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase = new AngeboteDerFirmaUseCase(angebote);
  }

  private static Angebot zeile(
      final long id, final Angebotsstatus status, final LocalDate angebotDatum) {
    return Angebotsdoppel.angebot(id, Angebotsdoppel.FIRMA, status, angebotDatum);
  }

  private List<Long> kennungenIn(final List<Angebot> bestand) {
    when(angebote.findByFirma(Angebotsdoppel.FIRMA)).thenReturn(bestand);
    return useCase.angebote(Angebotsdoppel.FIRMA).stream()
        .map(angebot -> Long.valueOf(angebot.requireId()))
        .toList();
  }

  @Test
  void angebote_thenTheNewestDateComesFirstWhateverTheStatus() {
    // Given — das juengere Angebot steht oben, auch wenn das aeltere erst angelegt ist.
    final List<Long> kennungen =
        kennungenIn(
            List.of(
                zeile(1L, Angebotsstatus.ANGELEGT, FRUEH),
                zeile(2L, Angebotsstatus.ABGERECHNET, SPAET)));

    // Then
    assertThat(kennungen).containsExactly(Long.valueOf(2L), Long.valueOf(1L));
  }

  @Test
  void angebote_withEqualDates_thenTheHigherIdComesFirst() {
    // Given — der Tiebreak, damit zwei Aufrufe dieselbe Liste liefern.
    final List<Long> kennungen =
        kennungenIn(
            List.of(
                zeile(1L, Angebotsstatus.ANGELEGT, FRUEH),
                zeile(2L, Angebotsstatus.ANGELEGT, FRUEH)));

    // Then
    assertThat(kennungen).containsExactly(Long.valueOf(2L), Long.valueOf(1L));
  }

  @Test
  void angebote_whenTheFirmaHasNone_thenAnswersWithAnEmptyList() {
    // Given — eine Firma ohne Angebot ist kein Fehler.
    when(angebote.findByFirma(Angebotsdoppel.FIRMA)).thenReturn(List.of());

    // When / Then
    assertThat(useCase.angebote(Angebotsdoppel.FIRMA)).isEmpty();
  }
}
