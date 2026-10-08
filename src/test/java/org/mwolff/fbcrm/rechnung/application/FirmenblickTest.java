package org.mwolff.fbcrm.rechnung.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.firma.application.FirmaNichtGefunden;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;

/**
 * Der Blick ohne Rechnungen. Seine Wege ueber Angebote und Firmen pruefen {@link
 * RechnungsauskunftTest} und die Offenen Posten mit; hier steht allein, was ihre Aufrufer nicht
 * zeigen koennen: Ohne Rechnung wird nichts gelesen, und der Blick ist trotzdem einer — er kennt
 * nur keine Firma.
 */
@ExtendWith(MockitoExtension.class)
class FirmenblickTest {

  @Mock private AngebotRepository angebote;
  @Mock private FirmaRepository firmen;

  @Test
  void fuer_withoutAnyRechnung_thenAsksNothingAndKnowsNoFirma() {
    // When
    final Firmenblick blick = Firmenblick.fuer(List.of(), List.of(), angebote, firmen);

    // Then
    assertThatThrownBy(() -> blick.nameVon(1L)).isInstanceOf(FirmaNichtGefunden.class);
    verifyNoInteractions(angebote, firmen);
  }
}
