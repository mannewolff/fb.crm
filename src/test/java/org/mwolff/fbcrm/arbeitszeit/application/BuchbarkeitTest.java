package org.mwolff.fbcrm.arbeitszeit.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Einheit;

/**
 * Die beiden Begriffe der Buchbarkeit (Plan #194, A6).
 *
 * <p><b>Buchbar</b> ist eine Eigenschaft der Position allein: nach Aufwand in Stunden. <b>Buchung
 * zulaessig</b> heisst zusaetzlich, dass das Angebot bestellt oder erledigt ist. Dass beides
 * auseinanderfaellt, ist der Kern der Regel: Eine Position bleibt buchbar, nachdem das Stellen der
 * Rechnung ihr Angebot auf „abgerechnet" gesetzt hat — sonst verschwaenden „angefallen" und die
 * Ueberschreitung aus der Angebotsansicht (Issue #193, Kriterien 8 und 11).
 */
class BuchbarkeitTest {

  private static Angebotsposition position(
      final Abrechnungsmodus abrechnungsmodus, final Einheit einheit) {
    return new Angebotsposition(
        Long.valueOf(7L),
        "Konzeption",
        abrechnungsmodus,
        new BigDecimal("20.00"),
        einheit,
        new BigDecimal("120.00"));
  }

  @Test
  void buchbar_givenAufwandInHours_thenTrue() {
    // Given / When / Then — Issue #193, Antworten 3 und 5.
    assertThat(Buchbarkeit.buchbar(position(Abrechnungsmodus.AUFWAND, Einheit.STUNDE))).isTrue();
  }

  @ParameterizedTest
  @EnumSource(Einheit.class)
  void buchbar_givenFestpreis_thenFalseForEveryEinheit(final Einheit einheit) {
    // Given / When / Then — der Festpreis ist unabhaengig vom Aufwand und traegt keine Stunden.
    assertThat(Buchbarkeit.buchbar(position(Abrechnungsmodus.FESTPREIS, einheit))).isFalse();
  }

  @ParameterizedTest
  @EnumSource(
      value = Einheit.class,
      names = {"PERSONENTAG", "PAUSCHAL"})
  void buchbar_givenAufwandInAnotherEinheit_thenFalse(final Einheit einheit) {
    // Given / When / Then — gebucht werden Stunden, nicht Personentage oder Pauschalen.
    assertThat(Buchbarkeit.buchbar(position(Abrechnungsmodus.AUFWAND, einheit))).isFalse();
  }

  @ParameterizedTest
  @EnumSource(
      value = Angebotsstatus.class,
      names = {"BESTELLT", "ERLEDIGT"})
  void buchungZulaessig_givenABookablePositionInAnOrderedAngebot_thenTrue(
      final Angebotsstatus status) {
    // Given / When / Then — Issue #193, Antwort 2.
    assertThat(Buchbarkeit.buchungZulaessig(Zeitdoppel.angebot(status), Zeitdoppel.KONZEPTION))
        .isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = Angebotsstatus.class,
      names = {"ANGELEGT", "ABGEGEBEN", "ABGERECHNET"})
  void buchungZulaessig_givenAnotherStatus_thenFalse(final Angebotsstatus status) {
    // Given / When / Then — vor der Zusage gibt es nichts zu buchen, danach ist Schluss.
    assertThat(Buchbarkeit.buchungZulaessig(Zeitdoppel.angebot(status), Zeitdoppel.KONZEPTION))
        .isFalse();
  }

  @Test
  void buchungZulaessig_givenANonBookablePositionInAnOrderedAngebot_thenFalse() {
    // Given / When / Then — der Status allein genuegt nicht; die Position muss buchbar sein.
    assertThat(Buchbarkeit.buchungZulaessig(Zeitdoppel.angebot(), Zeitdoppel.SCHULUNG)).isFalse();
  }

  @Test
  void buchbar_givenAPositionOfAnAlreadyBilledAngebot_thenStillTrue() {
    // Given — A6: buchbar haengt nicht am Status, sonst verschwaende „angefallen" nach dem
    // Stellen der letzten Rechnung.
    // When / Then
    assertThat(
            Buchbarkeit.buchbar(
                Zeitdoppel.angebot(Angebotsstatus.ABGERECHNET).positionen().getFirst()))
        .isTrue();
  }
}
