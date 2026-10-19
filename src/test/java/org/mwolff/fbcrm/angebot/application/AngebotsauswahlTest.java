package org.mwolff.fbcrm.angebot.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;

/**
 * Welche Angebote eine {@link Angebotsauswahl} trifft (Issue #335, Plan #332 E6 und E7).
 *
 * <p>Die drei Bauweisen sind eine Partition des Waehlers: {@code imStatus} trifft kein storniertes
 * Angebot, {@code stornierte} jedes stornierte gleich in welchem Status, {@code alle} beide.
 */
class AngebotsauswahlTest {

  private static final Instant JETZT = Instant.parse("2026-10-17T08:00:00Z");

  private static Angebot offen(final Angebotsstatus status) {
    return Angebotsdoppel.angebot(1L, status);
  }

  private static Angebot storniert(final Angebotsstatus status) {
    return offen(status).storniert("vom Kunden abgelehnt", JETZT);
  }

  @ParameterizedTest
  @EnumSource(
      value = Angebotsstatus.class,
      names = {"ANGELEGT", "ABGEGEBEN", "BESTELLT"})
  void alle_thenFitsEveryAngebotStornoOrNot(final Angebotsstatus status) {
    assertThat(Angebotsauswahl.alle().passt(offen(status))).isTrue();
    assertThat(Angebotsauswahl.alle().passt(storniert(status))).isTrue();
  }

  @Test
  void alle_thenLeavesBothOpen() {
    assertThat(Angebotsauswahl.alle()).isEqualTo(new Angebotsauswahl(null, null));
  }

  @Test
  void imStatus_thenFitsAnOpenAngebotInExactlyThisStatus() {
    final Angebotsauswahl auswahl = Angebotsauswahl.imStatus(Angebotsstatus.ABGEGEBEN);

    assertThat(auswahl.passt(offen(Angebotsstatus.ABGEGEBEN))).isTrue();
    assertThat(auswahl.passt(offen(Angebotsstatus.BESTELLT))).isFalse();
  }

  @Test
  void imStatus_givenAStorniertAngebotInThisStatus_thenDoesNotFit() {
    // E7: ein storniertes Angebot erscheint nicht unter seinem eingefrorenen Status.
    assertThat(
            Angebotsauswahl.imStatus(Angebotsstatus.ABGEGEBEN)
                .passt(storniert(Angebotsstatus.ABGEGEBEN)))
        .isFalse();
  }

  @Test
  void imStatus_thenFixesStorniertToFalse() {
    assertThat(Angebotsauswahl.imStatus(Angebotsstatus.BESTELLT))
        .isEqualTo(new Angebotsauswahl(Angebotsstatus.BESTELLT, Boolean.FALSE));
  }

  @ParameterizedTest
  @EnumSource(
      value = Angebotsstatus.class,
      names = {"ANGELEGT", "ABGEGEBEN", "BESTELLT"})
  void stornierte_thenFitsOnlyStorniertAngeboteWhateverTheirStatus(final Angebotsstatus status) {
    assertThat(Angebotsauswahl.stornierte().passt(storniert(status))).isTrue();
    assertThat(Angebotsauswahl.stornierte().passt(offen(status))).isFalse();
  }

  @Test
  void stornierte_thenLeavesTheStatusOpen() {
    assertThat(Angebotsauswahl.stornierte()).isEqualTo(new Angebotsauswahl(null, Boolean.TRUE));
  }
}
