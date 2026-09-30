package org.mwolff.fbcrm.rechnung.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.application.AngebotNichtGefunden;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Einheit;
import org.mwolff.fbcrm.firma.application.FirmaNichtGefunden;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
import org.mwolff.fbcrm.rechnung.domain.Nummernmuster;
import org.mwolff.fbcrm.rechnung.domain.Rechnung;
import org.mwolff.fbcrm.rechnung.domain.RechnungRepository;
import org.mwolff.fbcrm.rechnung.domain.Rechnungseinstellungen;
import org.mwolff.fbcrm.rechnung.domain.RechnungseinstellungenRepository;
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
  @Mock private FirmaRepository firmen;
  @Mock private RechnungseinstellungenRepository einstellungen;

  private RechnungLesenUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase = new RechnungLesenUseCase(rechnungen, angebote, firmen, einstellungen);
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

  @Test
  void lese_thenTheFirmaOfTheAngebotComesAlong() {
    // Given
    gegebenerEntwurf(List.of(Rechnungsdoppel.beratung("40.00")), List.of());

    // When
    final Rechnungsansicht ansicht = useCase.lese(ENTWURF);

    // Then
    assertThat(ansicht.firmaId()).isEqualTo(Rechnungsdoppel.FIRMA);
    assertThat(ansicht.firmaName()).isEqualTo(Rechnungsdoppel.FIRMENNAME);
  }

  @Test
  void lese_whenTheFirmaIsMissing_thenItIsAContradictionInTheBestand() {
    // Given — Firmen werden nie geloescht; fehlt die Firma, stimmt der Bestand nicht.
    when(rechnungen.findById(ENTWURF))
        .thenReturn(
            Optional.of(
                Rechnungsdoppel.entwurf(ENTWURF, List.of(Rechnungsdoppel.beratung("40.00")))));
    when(angebote.findById(Rechnungsdoppel.ANGEBOT))
        .thenReturn(Optional.of(Rechnungsdoppel.angebot()));
    when(firmen.findById(Rechnungsdoppel.FIRMA)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.lese(ENTWURF)).isInstanceOf(FirmaNichtGefunden.class);
  }

  @Test
  void lese_forAnEntwurf_thenTheSteuersatzComesFromTheEinstellungen() {
    // Given — ein Entwurf traegt keinen eigenen Satz (Kriterium 14).
    gegebenerEntwurf(List.of(Rechnungsdoppel.beratung("40.00")), List.of());

    // When
    final Rechnungsansicht ansicht = useCase.lese(ENTWURF);

    // Then
    assertThat(ansicht.steuersatz()).isEqualByComparingTo("19.00");
  }

  @Test
  void lese_forAGestellteRechnung_thenItKeepsItsOwnSteuersatz() {
    // Given — die gestellte Rechnung wurde mit 7 Prozent festgeschrieben.
    gegebeneRechnung(
        Rechnungsdoppel.gestellt(ENTWURF, "R26-0001", List.of(Rechnungsdoppel.beratung("40.00"))),
        List.of(),
        Rechnungsdoppel.angebot());

    // When
    final Rechnungsansicht ansicht = useCase.lese(ENTWURF);

    // Then
    assertThat(ansicht.steuersatz()).isEqualByComparingTo("7.00");
  }

  @Test
  void lese_whenTheAngebotWasRepriced_thenTheZeileShowsThePriceOfTheEntwurf() {
    // Given — der Entwurf haelt 100,00 fest, das Angebot steht inzwischen auf 120,00 (Frage 15).
    gegebeneRechnung(
        Rechnungsdoppel.entwurf(
            ENTWURF, List.of(Rechnungsdoppel.beratung("40.00", new BigDecimal("100.00")))),
        List.of(),
        Rechnungsdoppel.angebot(
            Angebotsstatus.BESTELLT,
            List.of(
                new Angebotsposition(
                    Long.valueOf(Rechnungsdoppel.BERATUNG_ID),
                    "Beratung, neu benannt",
                    Abrechnungsmodus.AUFWAND,
                    new BigDecimal("160.00"),
                    Einheit.STUNDE,
                    new BigDecimal("120.00")))));

    // When
    final Rechnungsansicht ansicht = useCase.lese(ENTWURF);

    // Then
    assertThat(ansicht.zeilen().getFirst().einzelpreis()).isEqualByComparingTo("100.00");
    assertThat(ansicht.zeilen().getFirst().bezeichnung()).isEqualTo("Beratung");
  }

  @Test
  void lese_whenTheEntwurfDoesNotCarryThePosition_thenTextAndPriceComeFromTheAngebot() {
    // Given — die Pauschale steht nur im Angebot.
    gegebenerEntwurf(List.of(Rechnungsdoppel.beratung("40.00")), List.of());

    // When
    final Rechnungsansicht ansicht = useCase.lese(ENTWURF);

    // Then
    assertThat(ansicht.zeilen().get(1).bezeichnung()).isEqualTo("Schulungstag");
    assertThat(ansicht.zeilen().get(1).einzelpreis()).isEqualByComparingTo("1200.00");
  }

  private void gegebenerEntwurf(
      final List<Rechnungsposition> eigene, final List<Rechnung> weitere) {
    gegebeneRechnung(Rechnungsdoppel.entwurf(ENTWURF, eigene), weitere, Rechnungsdoppel.angebot());
  }

  /*
   * Firma und Einstellungen gehoeren zu jeder Antwort: Die Maske zeigt den Namen der Firma, und der
   * geltende Steuersatz kommt beim Entwurf aus den Einstellungen.
   */
  private void gegebeneRechnung(
      final Rechnung rechnung, final List<Rechnung> weitere, final Angebot angebot) {
    when(rechnungen.findById(ENTWURF)).thenReturn(Optional.of(rechnung));
    when(angebote.findById(Rechnungsdoppel.ANGEBOT)).thenReturn(Optional.of(angebot));
    when(firmen.findById(Rechnungsdoppel.FIRMA))
        .thenReturn(
            Optional.of(Rechnungsdoppel.firma(Rechnungsdoppel.FIRMA, Rechnungsdoppel.FIRMENNAME)));
    when(einstellungen.lies())
        .thenReturn(
            new Rechnungseinstellungen(
                new Nummernmuster("R{JJ}-{NNNN}"), new BigDecimal("19.00"), 10));
    final List<Rechnung> alle = new ArrayList<>(weitere);
    alle.add(rechnung);
    when(rechnungen.findByAngebot(Rechnungsdoppel.ANGEBOT)).thenReturn(List.copyOf(alle));
  }
}
