package org.mwolff.fbcrm.angebot.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Die Nachbarn jedes Status in seiner Reihe und der Wechsel zwischen den Reihen (Issue #127,
 * Kriterien 3 und 4; Issue #226, Kriterium 1 von #207).
 *
 * <p>Zwei Reihen stehen nebeneinander: die fuenf Status des externen Angebots und die zwei des
 * internen. {@link Angebotsstatus#fuerArt(boolean)} ist die Bruecke dazwischen — total fuer alle
 * sieben Werte und idempotent, damit ein zweites Umstellen auf dieselbe Art nichts mehr verschiebt.
 */
class AngebotsstatusTest {

  @ParameterizedTest
  @CsvSource({
    "ANGELEGT, ABGEGEBEN",
    "ABGEGEBEN, BESTELLT",
    "BESTELLT, ERLEDIGT",
    "ERLEDIGT, ABGERECHNET",
    "LAEUFT, ABGESCHLOSSEN"
  })
  void weiter_thenTheNextStatus(final Angebotsstatus status, final Angebotsstatus naechster) {
    assertThat(status.weiter()).contains(naechster);
  }

  @ParameterizedTest
  @CsvSource({
    "ABGEGEBEN, ANGELEGT",
    "BESTELLT, ABGEGEBEN",
    "ERLEDIGT, BESTELLT",
    "ABGERECHNET, ERLEDIGT",
    "ABGESCHLOSSEN, LAEUFT"
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

  @Test
  void weiter_givenTheLastInternalStatus_thenEmpty() {
    assertThat(Angebotsstatus.ABGESCHLOSSEN.weiter()).isEmpty();
  }

  @Test
  void zurueck_givenTheFirstInternalStatus_thenEmpty() {
    assertThat(Angebotsstatus.LAEUFT.zurueck()).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({
    "LAEUFT, true",
    "ABGESCHLOSSEN, true",
    "ANGELEGT, false",
    "ABGEGEBEN, false",
    "BESTELLT, false",
    "ERLEDIGT, false",
    "ABGERECHNET, false"
  })
  void intern_thenOnlyTheTwoStatusOfTheInternalWork(
      final Angebotsstatus status, final boolean intern) {
    assertThat(status.intern()).isEqualTo(intern);
  }

  @ParameterizedTest
  @CsvSource({
    "ANGELEGT, LAEUFT",
    "ABGEGEBEN, LAEUFT",
    "BESTELLT, LAEUFT",
    "ERLEDIGT, ABGESCHLOSSEN",
    "ABGERECHNET, ABGESCHLOSSEN",
    "LAEUFT, LAEUFT",
    "ABGESCHLOSSEN, ABGESCHLOSSEN"
  })
  void fuerArt_towardsInternal_thenTheMatchingStatusOfTheInternalWork(
      final Angebotsstatus status, final Angebotsstatus erwartet) {
    assertThat(status.fuerArt(true)).isEqualTo(erwartet);
  }

  @ParameterizedTest
  @CsvSource({
    "LAEUFT, BESTELLT",
    "ABGESCHLOSSEN, ERLEDIGT",
    "ANGELEGT, ANGELEGT",
    "ABGEGEBEN, ABGEGEBEN",
    "BESTELLT, BESTELLT",
    "ERLEDIGT, ERLEDIGT",
    "ABGERECHNET, ABGERECHNET"
  })
  void fuerArt_towardsExternal_thenTheMatchingStatusOfTheCustomerOffer(
      final Angebotsstatus status, final Angebotsstatus erwartet) {
    assertThat(status.fuerArt(false)).isEqualTo(erwartet);
  }

  @ParameterizedTest
  @EnumSource(Angebotsstatus.class)
  void fuerArt_appliedTwice_thenChangesNothingMore(final Angebotsstatus status) {
    // Given — idempotent (E4): Wer zweimal auf dieselbe Art umstellt, verschiebt nichts mehr.

    // When / Then
    assertThat(status.fuerArt(true).fuerArt(true)).isEqualTo(status.fuerArt(true));
    assertThat(status.fuerArt(false).fuerArt(false)).isEqualTo(status.fuerArt(false));
  }

  @ParameterizedTest
  @EnumSource(Angebotsstatus.class)
  void fuerArt_thenTheResultCarriesTheRequestedArt(final Angebotsstatus status) {
    // Given — total (E4): Fuer jeden der sieben Werte liefert fuerArt einen Status der Art.

    // When / Then
    assertThat(status.fuerArt(true).intern()).isTrue();
    assertThat(status.fuerArt(false).intern()).isFalse();
  }
}
