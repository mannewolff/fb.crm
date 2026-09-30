package org.mwolff.fbcrm.rechnung.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.rechnung.domain.RechnungRepository;

/**
 * Welche Positionen eines Angebots in einer Rechnung stehen (#160, Kriterium 28).
 *
 * <p>Die Antwort geht ans Modul {@code angebot} und bindet dort die berechneten Positionen.
 * Gezaehlt werden Entwuerfe wie gestellte Rechnungen: Ein Entwurf, der eine Position schon traegt,
 * verloere seinen Bezug genauso, wenn sie am Angebot verschwaende.
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
}
