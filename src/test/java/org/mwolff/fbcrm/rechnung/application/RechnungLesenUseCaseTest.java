package org.mwolff.fbcrm.rechnung.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.application.AngebotNichtGefunden;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.rechnung.domain.Rechnung;
import org.mwolff.fbcrm.rechnung.domain.RechnungRepository;
import org.mwolff.fbcrm.rechnung.domain.Rechnungsposition;

/**
 * Das Lesen eines Rechnungsentwurfs samt der Zeilen seiner Maske (Plan #169, E5, E6).
 *
 * <p>Gegenstand ist der Blick der Maske: Sie zeigt <b>jede</b> Position des Angebots — auch eine,
 * die der Entwurf nicht traegt, dann mit 0 —, und sie rechnet „bereits abgerechnet" ohne diesen
 * Entwurf. Die Ueberschreitung dagegen zaehlt die eingetragene Menge mit: Sie ist der Hinweis an
 * der Position, dass mehr abgerechnet wird als offen ist (#160, Kriterien 4, 7, 8).
 */
@ExtendWith(MockitoExtension.class)
class RechnungLesenUseCaseTest {

  private static final long ENTWURF = 2L;

  @Mock private RechnungRepository rechnungen;
  @Mock private AngebotRepository angebote;

  private RechnungLesenUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase = new RechnungLesenUseCase(rechnungen, angebote);
  }

  @Test
  void lese_withAnUnknownRechnung_thenNotFound() {
    // Given
    when(rechnungen.findById(ENTWURF)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.lese(ENTWURF)).isInstanceOf(RechnungNichtGefunden.class);
  }

  @Test
  void lese_whenTheAngebotIsMissing_thenItIsAContradictionInTheBestand() {
    // Given — Angebote werden nie geloescht; fehlt das Angebot, stimmt der Bestand nicht.
    when(rechnungen.findById(ENTWURF))
        .thenReturn(
            Optional.of(
                Rechnungsdoppel.entwurf(ENTWURF, List.of(Rechnungsdoppel.beratung("40.00")))));
    when(angebote.findById(Rechnungsdoppel.ANGEBOT)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.lese(ENTWURF)).isInstanceOf(AngebotNichtGefunden.class);
  }

  @Test
  void lese_thenEveryPositionOfTheAngebotIsAZeileEvenWhenTheEntwurfDoesNotCarryIt() {
    // Given — der Entwurf traegt nur die Beratung, das Angebot hat zwei Positionen.
    gegebenerEntwurf(List.of(Rechnungsdoppel.beratung("40.00")), List.of());

    // When
    final Rechnungsansicht ansicht = useCase.lese(ENTWURF);

    // Then
    assertThat(ansicht.zeilen())
        .hasSize(2)
        .satisfies(
            zeilen -> assertThat(zeilen.get(0).jetzt()).isEqualByComparingTo("40"),
            zeilen -> assertThat(zeilen.get(1).jetzt()).isEqualByComparingTo("0"),
            zeilen ->
                assertThat(zeilen.get(1).position().id())
                    .isEqualTo(Long.valueOf(Rechnungsdoppel.PAUSCHALE_ID)));
  }

  @Test
  void lese_thenAbgerechnetLeavesThisRechnungOut() {
    // Given — 80 aus einer gestellten Rechnung, 40 im eigenen Entwurf.
    gegebenerEntwurf(
        List.of(Rechnungsdoppel.beratung("40.00")),
        List.of(
            Rechnungsdoppel.gestellt(1L, "R26-0001", List.of(Rechnungsdoppel.beratung("80.00")))));

    // When
    final Rechnungsansicht ansicht = useCase.lese(ENTWURF);

    // Then
    assertThat(ansicht.zeilen().getFirst())
        .satisfies(
            zeile -> assertThat(zeile.angeboten()).isEqualByComparingTo("160"),
            zeile -> assertThat(zeile.abgerechnet()).isEqualByComparingTo("80"),
            zeile -> assertThat(zeile.offen()).isEqualByComparingTo("80"),
            zeile -> assertThat(zeile.ueberschreitung()).isEqualByComparingTo("0"));
  }

  @Test
  void lese_whenThisEntwurfGoesBeyondTheRest_thenTheUeberschreitungCountsItsMenge() {
    // Given — 100 sind gestellt, der Entwurf traegt 80: zusammen 20 mehr als angeboten.
    gegebenerEntwurf(
        List.of(Rechnungsdoppel.beratung("80.00")),
        List.of(
            Rechnungsdoppel.gestellt(1L, "R26-0001", List.of(Rechnungsdoppel.beratung("100.00")))));

    // When
    final Rechnungsansicht ansicht = useCase.lese(ENTWURF);

    // Then
    assertThat(ansicht.zeilen().getFirst().ueberschreitung()).isEqualByComparingTo("20");
  }

  @Test
  void lese_thenTheRechnungItselfComesAlong() {
    // Given
    gegebenerEntwurf(List.of(Rechnungsdoppel.beratung("40.00")), List.of());

    // When
    final Rechnungsansicht ansicht = useCase.lese(ENTWURF);

    // Then
    assertThat(ansicht.rechnung().requireId()).isEqualTo(ENTWURF);
  }

  private void gegebenerEntwurf(
      final List<Rechnungsposition> eigene, final List<Rechnung> weitere) {
    final Rechnung entwurf = Rechnungsdoppel.entwurf(ENTWURF, eigene);
    when(rechnungen.findById(ENTWURF)).thenReturn(Optional.of(entwurf));
    when(angebote.findById(Rechnungsdoppel.ANGEBOT))
        .thenReturn(Optional.of(Rechnungsdoppel.angebot()));
    final List<Rechnung> alle = new ArrayList<>(weitere);
    alle.add(entwurf);
    when(rechnungen.findByAngebot(Rechnungsdoppel.ANGEBOT)).thenReturn(List.copyOf(alle));
  }
}
