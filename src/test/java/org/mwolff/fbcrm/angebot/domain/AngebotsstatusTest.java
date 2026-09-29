package org.mwolff.fbcrm.angebot.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Die Nachbarn jedes Status in der Reihe (Issue #127, Kriterien 3 und 4). */
class AngebotsstatusTest {

  @ParameterizedTest
  @CsvSource({
    "ANGELEGT, ABGEGEBEN",
    "ABGEGEBEN, BESTELLT",
    "BESTELLT, ERLEDIGT",
    "ERLEDIGT, ABGERECHNET"
  })
  void weiter_thenTheNextStatus(final Angebotsstatus status, final Angebotsstatus naechster) {
    assertThat(status.weiter()).contains(naechster);
  }

  @ParameterizedTest
  @CsvSource({
    "ABGEGEBEN, ANGELEGT",
    "BESTELLT, ABGEGEBEN",
    "ERLEDIGT, BESTELLT",
    "ABGERECHNET, ERLEDIGT"
  })
  void zurueck_thenThePreviousStatus(final Angebotsstatus status, final Angebotsstatus voriger) {
    assertThat(status.zurueck()).contains(voriger);
  }

  @Test
  void weiter_givenTheLastStatus_thenEmpty() {
    assertThat(Angebotsstatus.ABGERECHNET.weiter()).isEmpty();
  }

  @Test
  void zurueck_givenTheFirstStatus_thenEmpty() {
    assertThat(Angebotsstatus.ANGELEGT.zurueck()).isEmpty();
  }
}
