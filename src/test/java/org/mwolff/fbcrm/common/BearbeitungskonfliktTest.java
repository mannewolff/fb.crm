package org.mwolff.fbcrm.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.common.Bearbeitungskonflikt.Art;

class BearbeitungskonfliktTest {

  private static final Instant GEAENDERT = Instant.parse("2026-10-23T12:05:00Z");

  @Test
  void accessors_thenReturnWhatTheUseCaseLaidIn() {
    // Given — E6: der Anwendungsfall legt nur die Kennungen und den Zeitpunkt hinein.
    final Bearbeitungskonflikt konflikt = new Veraltet(7L, 9L, GEAENDERT);

    // When / Then
    assertThat(konflikt.art()).isEqualTo(Art.VERALTET);
    assertThat(konflikt.urheberKonto()).hasValue(7L);
    assertThat(konflikt.handelndesKonto()).isEqualTo(9L);
    assertThat(konflikt.zeitpunkt()).contains(GEAENDERT);
  }

  @Test
  void accessors_givenNoAuthorAndNoTime_thenBothAreEmpty() {
    // Given — eine Zeile aus der Zeit vor V33 traegt weder Urheber noch Zeitpunkt.
    final Bearbeitungskonflikt konflikt = new Veraltet(null, 9L, null);

    // When / Then
    assertThat(konflikt.urheberKonto()).isEmpty();
    assertThat(konflikt.zeitpunkt()).isEmpty();
  }

  @Test
  void art_thenCarriesTheWordTheSurfaceReads() {
    // Given — E16: die Oberflaeche unterscheidet die beiden Lagen an genau diesen Woertern.

    // When / Then
    assertThat(Art.VERALTET.wert()).isEqualTo("veraltet");
    assertThat(Art.GELOESCHT.wert()).isEqualTo("gelöscht");
  }

  @Test
  void pruefe_givenTheCurrentVersion_thenDoesNotThrow() {
    // Given — Kriterium 8: solange der geladene Stand aktuell ist, merkt man nichts.

    // When / Then
    assertThatCode(() -> Bearbeitungskonflikt.pruefe(3L, 3L, () -> new Veraltet(7L, 9L, null)))
        .doesNotThrowAnyException();
  }

  @Test
  void pruefe_givenAnOlderVersion_thenThrowsTheSuppliedConflict() {
    // Given — Kriterium 1: der Erste hat inzwischen gespeichert.
    final Veraltet konflikt = new Veraltet(7L, 9L, GEAENDERT);

    // When / Then
    assertThatThrownBy(() -> Bearbeitungskonflikt.pruefe(2L, 3L, () -> konflikt))
        .isSameAs(konflikt);
  }

  @Test
  void pruefe_givenANewerVersion_thenThrowsToo() {
    // Given — ein vorgelegter Stand, den es nie gab, ist ebenso wenig der gesehene.
    final Veraltet konflikt = new Veraltet(7L, 9L, GEAENDERT);

    // When / Then
    assertThatThrownBy(() -> Bearbeitungskonflikt.pruefe(4L, 3L, () -> konflikt))
        .isSameAs(konflikt);
  }

  /*
   * Ein Konflikt aus einem beliebigen Fachmodul — nachgebildet, damit dieser Test kein Fachmodul
   * importieren muss (ArchitectureTest, modules_thenFreeOfCycles).
   */
  private static final class Veraltet extends Bearbeitungskonflikt {
    Veraltet(
        final @Nullable Long urheberKonto,
        final long handelndesKonto,
        final @Nullable Instant zeitpunkt) {
      super(Art.VERALTET, urheberKonto, handelndesKonto, zeitpunkt);
    }
  }
}
