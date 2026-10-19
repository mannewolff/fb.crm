package org.mwolff.fbcrm.arbeitszeit.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Die beiden Saetze der abgewiesenen Buchung, woertlich (Plan #218, E22; Plan #332, E16).
 *
 * <p>Am Kundenangebot nennt der Satz auch das Storno: Ein storniertes Angebot in „bestellt"
 * erfuellt alle uebrigen Bedingungen, und ohne den Zusatz laese der Anwender einen nachweislich
 * falschen Satz. An der internen Arbeit gibt es kein Storno, ihr Satz bleibt, wie er ist.
 */
class PositionNichtBuchbarTest {

  @Test
  void meldungExtern_thenNamesTheCancellation() {
    // Given / When / Then — E16.
    assertThat(PositionNichtBuchbar.MELDUNG_EXTERN)
        .isEqualTo(
            "Auf diese Position kann keine Arbeitszeit gebucht werden: Sie muss nach Aufwand in"
                + " Stunden abgerechnet werden, und ihr Angebot muss bestellt oder erledigt und"
                + " nicht storniert sein.");
  }

  @Test
  void meldungIntern_thenUnchanged() {
    // Given / When / Then — an der internen Arbeit gibt es kein Storno.
    assertThat(PositionNichtBuchbar.MELDUNG_INTERN)
        .isEqualTo(
            "Auf diese Position kann keine Arbeitszeit gebucht werden: Ihr Angebot muss laufen.");
  }
}
