package org.mwolff.fbcrm.rechnung.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mwolff.fbcrm.rechnung.domain.NachgetrageneRechnungRepository;
import org.mwolff.fbcrm.rechnung.domain.RechnungRepository;

/**
 * Die eine Frage, ob eine Rechnungsnummer vergeben ist, ueber beide Bestaende (#254, Kriterien 4
 * und 5; Plan #259, E3).
 *
 * <p>Vergeben ist eine Nummer, wenn sie eine von fb.crm geschriebene <b>oder</b> eine nachgetragene
 * Rechnung traegt. Die Schreibweise uebergehen die Repositories; der Dienst reicht die Nummer
 * unveraendert weiter.
 *
 * <p>Die Repositories laufen nachsichtig: Ob der zweite Bestand gefragt wird, wenn der erste schon
 * ja sagt, ist keine Zusage des Dienstes.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RechnungsnummernTest {

  private static final String NUMMER = "RE-9";
  private static final long EIGENE = 7L;

  @Mock private RechnungRepository rechnungen;
  @Mock private NachgetrageneRechnungRepository nachgetragene;

  private Rechnungsnummern nummern;

  @BeforeEach
  void baueDenDienst() {
    nummern = new Rechnungsnummern(rechnungen, nachgetragene);
  }

  @ParameterizedTest(name = "in rechnung {0}, in rechnung_nachgetragen {1} → vergeben {2}")
  @CsvSource({"true, false, true", "false, true, true", "true, true, true", "false, false, false"})
  void vergeben_givenBothBestaende_thenTrueIfEitherCarriesTheNummer(
      final boolean inRechnung, final boolean inNachgetragen, final boolean vergeben) {
    // Given
    when(rechnungen.existiertNummer(NUMMER)).thenReturn(inRechnung);
    when(nachgetragene.existiertNummer(NUMMER)).thenReturn(inNachgetragen);

    // When / Then
    assertThat(nummern.vergeben(NUMMER)).isEqualTo(vergeben);
  }

  @ParameterizedTest(name = "in rechnung {0}, in rechnung_nachgetragen {1} → vergeben {2}")
  @CsvSource({"true, false, true", "false, true, true", "true, true, true", "false, false, false"})
  void vergebenVonAnderer_givenBothBestaende_thenLeavesOutTheOwnRow(
      final boolean inRechnung, final boolean beiAnderer, final boolean vergeben) {
    // Given — die eigene Zeile laesst allein der Bestand der nachgetragenen Rechnungen aus.
    when(rechnungen.existiertNummer(NUMMER)).thenReturn(inRechnung);
    when(nachgetragene.existiertNummerAusser(NUMMER, EIGENE)).thenReturn(beiAnderer);

    // When / Then
    assertThat(nummern.vergebenVonAnderer(NUMMER, EIGENE)).isEqualTo(vergeben);
  }

  @ParameterizedTest(name = "eigene Kennung {0} → vergeben {1}")
  @CsvSource({"7, false", "8, true"})
  void vergebenVonAnderer_givenOnlyTheNachgetrageneWithId7CarriesIt_thenOnlyAForeignIdSeesIt(
      final long kennung, final boolean vergeben) {
    // Given — nur die nachgetragene Rechnung 7 traegt die Nummer.
    when(nachgetragene.existiertNummerAusser(NUMMER, EIGENE)).thenReturn(false);
    when(nachgetragene.existiertNummerAusser(NUMMER, 8L)).thenReturn(true);

    // When / Then — mit der eigenen Kennung ist die Nummer frei, mit einer fremden vergeben.
    assertThat(nummern.vergebenVonAnderer(NUMMER, kennung)).isEqualTo(vergeben);
  }
}
