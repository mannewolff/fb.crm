package org.mwolff.fbcrm.rechnung.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Die Masse, die ein {@link Satzspiegel} aus seinen Spaltenkanten ableitet. */
class SatzspiegelTest {

  private static final Satzspiegel SPIEGEL = new Satzspiegel(71, 136, 371, 436, 503, 120, 100, 780);

  @Test
  void spaltenkanten_givenTheFuenfKanten_thenTheyStehenVonLinksNachRechts() {
    // When
    final var kanten = SPIEGEL.spaltenkanten();

    // Then — die Senkrechten der Tabelle, die letzte ist ihre rechte Kante.
    assertThat(kanten).containsExactly(71, 136, 371, 436, 503);
  }

  @Test
  void positionsbreite_givenTheKantenDerSpaltePosition_thenItIsTheirAbstand() {
    // When
    final int breite = SPIEGEL.positionsbreite();

    // Then — von der Kante vor der Position bis zur Kante vor dem Einzelpreis.
    assertThat(breite).isEqualTo(235);
  }
}
