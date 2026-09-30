package org.mwolff.fbcrm.rechnung.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.application.AngebotNichtGefunden;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.rechnung.domain.Nummernmuster;
import org.mwolff.fbcrm.rechnung.domain.RechnungRepository;
import org.mwolff.fbcrm.rechnung.domain.Rechnungseinstellungen;
import org.mwolff.fbcrm.rechnung.domain.RechnungseinstellungenRepository;

/**
 * Der Abrechnungsstand eines Angebots (#160, Kriterium 26).
 *
 * <p>Gegenstand sind die beiden Haelften der Antwort: je Position des Angebots angeboten,
 * abgerechnet, offen und Ueberschreitung — Entwuerfe zaehlen dabei mit (Kriterium 6) — und die
 * Rechnungen dieses Angebots mit ihrem Bruttobetrag, neueste zuerst.
 */
@ExtendWith(MockitoExtension.class)
class AbrechnungsstandUseCaseTest {

  @Mock private AngebotRepository angebote;
  @Mock private RechnungRepository rechnungen;
  @Mock private RechnungseinstellungenRepository einstellungen;

  private AbrechnungsstandUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase = new AbrechnungsstandUseCase(angebote, rechnungen, einstellungen);
  }

  private void gegebeneEinstellungen(final String steuersatz) {
    when(einstellungen.lies())
        .thenReturn(
            new Rechnungseinstellungen(
                new Nummernmuster("R{JJ}-{NNNN}"), new BigDecimal(steuersatz), 10));
  }

  @Test
  void zu_withAnUnknownAngebot_thenNotFound() {
    // Given
    when(angebote.findById(Rechnungsdoppel.ANGEBOT)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.zu(Rechnungsdoppel.ANGEBOT))
        .isInstanceOf(AngebotNichtGefunden.class);
    verifyNoInteractions(rechnungen, einstellungen);
  }

  @Test
  void zu_thenEveryPositionOfTheAngebotCarriesItsStand() {
    // Given — 100 gestellt und 80 im Entwurf auf 160 angebotene Stunden.
    when(angebote.findById(Rechnungsdoppel.ANGEBOT))
        .thenReturn(Optional.of(Rechnungsdoppel.angebot()));
    when(rechnungen.findByAngebot(Rechnungsdoppel.ANGEBOT))
        .thenReturn(
            List.of(
                Rechnungsdoppel.gestellt(
                    1L, "R26-0001", List.of(Rechnungsdoppel.beratung("100.00"))),
                Rechnungsdoppel.entwurf(2L, List.of(Rechnungsdoppel.beratung("80.00")))));
    gegebeneEinstellungen("19.00");

    // When
    final Angebotsabrechnung abrechnung = useCase.zu(Rechnungsdoppel.ANGEBOT);

    // Then — die Beratung ist um 20 ueberschritten, die Pauschale ist ganz offen.
    assertThat(abrechnung.stand().positionen())
        .hasSize(2)
        .satisfies(
            staende -> assertThat(staende.getFirst().angeboten()).isEqualByComparingTo("160"),
            staende -> assertThat(staende.getFirst().abgerechnet()).isEqualByComparingTo("180"),
            staende -> assertThat(staende.getFirst().offen()).isEqualByComparingTo("0"),
            staende -> assertThat(staende.getFirst().ueberschreitung()).isEqualByComparingTo("20"),
            staende -> assertThat(staende.get(1).offen()).isEqualByComparingTo("1"));
  }

  @Test
  void zu_thenTheRechnungenComeNewestFirstWithTheirBruttoBetrag() {
    // Given — ein Entwurf ohne eigenen Satz und eine gestellte Rechnung mit ihren 7 Prozent.
    when(angebote.findById(Rechnungsdoppel.ANGEBOT))
        .thenReturn(Optional.of(Rechnungsdoppel.angebot()));
    when(rechnungen.findByAngebot(Rechnungsdoppel.ANGEBOT))
        .thenReturn(
            List.of(
                Rechnungsdoppel.gestellt(
                    1L,
                    Rechnungsdoppel.ANGEBOT,
                    "R26-0001",
                    List.of(Rechnungsdoppel.beratung("100.00")),
                    LocalDate.of(2026, 9, 20)),
                Rechnungsdoppel.entwurf(2L, List.of(Rechnungsdoppel.beratung("80.00")))));
    gegebeneEinstellungen("19.00");

    // When
    final Angebotsabrechnung abrechnung = useCase.zu(Rechnungsdoppel.ANGEBOT);

    // Then — der Entwurf vom 30.09. steht vorn und rechnet mit 19, die gestellte mit ihren 7.
    assertThat(abrechnung.rechnungen())
        .hasSize(2)
        .satisfies(
            zeilen -> assertThat(zeilen.getFirst().rechnung().requireId()).isEqualTo(2L),
            zeilen -> assertThat(zeilen.getFirst().brutto()).isEqualByComparingTo("9520.00"),
            zeilen -> assertThat(zeilen.get(1).rechnung().requireId()).isEqualTo(1L),
            zeilen -> assertThat(zeilen.get(1).brutto()).isEqualByComparingTo("10700.00"));
  }

  @Test
  void zu_withoutARechnung_thenTheListIsEmptyAndEverythingIsOffen() {
    // Given
    when(angebote.findById(Rechnungsdoppel.ANGEBOT))
        .thenReturn(Optional.of(Rechnungsdoppel.angebot()));
    when(rechnungen.findByAngebot(Rechnungsdoppel.ANGEBOT)).thenReturn(List.of());
    gegebeneEinstellungen("19.00");

    // When
    final Angebotsabrechnung abrechnung = useCase.zu(Rechnungsdoppel.ANGEBOT);

    // Then
    assertThat(abrechnung.rechnungen()).isEmpty();
    assertThat(abrechnung.stand().positionen().getFirst().offen()).isEqualByComparingTo("160");
  }
}
