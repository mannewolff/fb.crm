package org.mwolff.fbcrm.rechnung.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.application.Rechnungsbindung;
import org.mwolff.fbcrm.rechnung.domain.RechnungRepository;

/**
 * Welche Positionen eines Angebots in einer Rechnung stehen (#160, Kriterium 28).
 *
 * <p>Die Antwort geht ans Modul {@code angebot} und bindet dort die berechneten Positionen.
 * Gezaehlt werden Entwuerfe wie gestellte Rechnungen: Ein Entwurf, der eine Position schon traegt,
 * verloere seinen Bezug genauso, wenn sie am Angebot verschwaende.
 *
 * <p>Dieselbe Klasse beantwortet den zweiten Port {@link Rechnungsbindung} (Issue #227, E5): ob aus
 * dem Angebot <b>ueberhaupt</b> eine Rechnung entstanden ist. Das ist nicht dieselbe Frage — eine
 * Rechnung ohne Positionen bindet keine einzige Kennung und ist trotzdem eine Rechnung.
 */
@ExtendWith(MockitoExtension.class)
class RechnungsPositionsverwendungTest {

  @Mock private RechnungRepository rechnungen;

  @Test
  void verwendeteKennungen_thenNamesThePositionenOfEveryRechnungOfTheAngebot() {
    // Given — ein Entwurf ueber die Beratung und eine gestellte Rechnung ueber die Pauschale.
    when(rechnungen.findByAngebot(Rechnungsdoppel.ANGEBOT))
        .thenReturn(
            List.of(
                Rechnungsdoppel.entwurf(1L, List.of(Rechnungsdoppel.beratung("80.00"))),
                Rechnungsdoppel.gestellt(
                    2L, "2026-0001", List.of(Rechnungsdoppel.pauschale("1")))));

    // When
    final var verwendet =
        new RechnungsPositionsverwendung(rechnungen).verwendeteKennungen(Rechnungsdoppel.ANGEBOT);

    // Then
    assertThat(verwendet)
        .containsExactlyInAnyOrder(
            Long.valueOf(Rechnungsdoppel.BERATUNG_ID), Long.valueOf(Rechnungsdoppel.PAUSCHALE_ID));
  }

  @Test
  void verwendeteKennungen_givenNoRechnung_thenEmpty() {
    // Given — ein Angebot, aus dem noch nichts abgerechnet wurde.
    when(rechnungen.findByAngebot(Rechnungsdoppel.ANGEBOT)).thenReturn(List.of());

    // When / Then
    assertThat(
            new RechnungsPositionsverwendung(rechnungen)
                .verwendeteKennungen(Rechnungsdoppel.ANGEBOT))
        .isEmpty();
  }

  @Test
  void rechnungVorhanden_givenAGestellteRechnung_thenTrue() {
    // Given — Kriterium 8 von #207: Aus dem Angebot ist eine Rechnung entstanden.
    when(rechnungen.findByAngebot(Rechnungsdoppel.ANGEBOT))
        .thenReturn(
            List.of(
                Rechnungsdoppel.gestellt(
                    2L, "2026-0001", List.of(Rechnungsdoppel.pauschale("1")))));

    // When / Then
    assertThat(
            new RechnungsPositionsverwendung(rechnungen).rechnungVorhanden(Rechnungsdoppel.ANGEBOT))
        .isTrue();
  }

  @Test
  void rechnungVorhanden_givenOnlyAnEntwurf_thenTrue() {
    // Given — der Entwurf zaehlt mit: Ein Angebot ohne Preise waere ein widerspruechlicher Satz.
    when(rechnungen.findByAngebot(Rechnungsdoppel.ANGEBOT))
        .thenReturn(
            List.of(Rechnungsdoppel.entwurf(1L, List.of(Rechnungsdoppel.beratung("80.00")))));

    // When / Then
    assertThat(
            new RechnungsPositionsverwendung(rechnungen).rechnungVorhanden(Rechnungsdoppel.ANGEBOT))
        .isTrue();
  }

  @Test
  void rechnungVorhanden_givenNoRechnung_thenFalse() {
    // Given — ein Angebot, aus dem noch nichts entstanden ist.
    when(rechnungen.findByAngebot(Rechnungsdoppel.ANGEBOT)).thenReturn(List.of());

    // When / Then
    assertThat(
            new RechnungsPositionsverwendung(rechnungen).rechnungVorhanden(Rechnungsdoppel.ANGEBOT))
        .isFalse();
  }

  @Test
  void rechnungVorhanden_givenARechnungWithoutPositionen_thenTrueWhileNoKennungIsBound() {
    // Given — der Grund fuer den zweiten Port (E5): Ein leerer Kennungssatz ist nicht dasselbe wie
    // „keine Rechnung". Die Rechnung traegt keine Position, weil jede Menge 0 war (Kriterium 5).
    final Rechnungsbindung bindung = new RechnungsPositionsverwendung(rechnungen);
    when(rechnungen.findByAngebot(Rechnungsdoppel.ANGEBOT))
        .thenReturn(List.of(Rechnungsdoppel.entwurf(1L, List.of())));

    // When / Then
    assertThat(bindung.rechnungVorhanden(Rechnungsdoppel.ANGEBOT)).isTrue();
    assertThat(
            new RechnungsPositionsverwendung(rechnungen)
                .verwendeteKennungen(Rechnungsdoppel.ANGEBOT))
        .isEmpty();
  }
}
