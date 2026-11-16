package org.mwolff.fbcrm.common.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Die Verwaltungswerte kommen ueber die Konstruktoren von {@link GefuehrteZeile} und {@link
 * LoeschbareZeile} an ihren Gettern an (Issue #489).
 *
 * <p>Gegenstand ist die Abbildung allein: Seit sieben Entities dieselben Felder erben, wuerde eine
 * vertauschte Zuweisung jede von ihnen treffen. Jeder Wert ist darum ein anderer.
 */
class LoeschbareZeileTest {

  private static final Instant ANGELEGT = Instant.parse("2026-11-01T08:00:00Z");
  private static final Instant GEAENDERT = Instant.parse("2026-11-02T09:00:00Z");
  private static final Instant GELOESCHT = Instant.parse("2026-11-03T10:00:00Z");

  /** Eine gefuehrte Zeile ohne eigene Felder. */
  private static final class Gefuehrt extends GefuehrteZeile {
    Gefuehrt() {
      super(ANGELEGT, GEAENDERT, 4L, 7L);
    }
  }

  /** Eine loeschbare Zeile ohne eigene Felder. */
  private static final class Loeschbar extends LoeschbareZeile {
    Loeschbar(final Instant geloeschtAm, final Long geloeschtVon) {
      super(ANGELEGT, GEAENDERT, 5L, 8L, geloeschtAm, geloeschtVon);
    }
  }

  @Test
  void gefuehrteZeile_thenCarriesEveryVerwaltungswert() {
    // When
    final GefuehrteZeile zeile = new Gefuehrt();

    // Then
    assertThat(zeile)
        .satisfies(
            gelesen -> assertThat(gelesen.getCreatedAt()).isEqualTo(ANGELEGT),
            gelesen -> assertThat(gelesen.getUpdatedAt()).isEqualTo(GEAENDERT),
            gelesen -> assertThat(gelesen.getVersion()).isEqualTo(4L),
            gelesen -> assertThat(gelesen.getGeaendertVon()).isEqualTo(7L));
  }

  @Test
  void loeschbareZeile_thenCarriesEveryVerwaltungswertAndTheKennzeichen() {
    // When
    final LoeschbareZeile zeile = new Loeschbar(GELOESCHT, 9L);

    // Then
    assertThat(zeile)
        .satisfies(
            gelesen -> assertThat(gelesen.getCreatedAt()).isEqualTo(ANGELEGT),
            gelesen -> assertThat(gelesen.getUpdatedAt()).isEqualTo(GEAENDERT),
            gelesen -> assertThat(gelesen.getVersion()).isEqualTo(5L),
            gelesen -> assertThat(gelesen.getGeaendertVon()).isEqualTo(8L),
            gelesen -> assertThat(gelesen.getGeloeschtAm()).isEqualTo(GELOESCHT),
            gelesen -> assertThat(gelesen.getGeloeschtVon()).isEqualTo(9L));
  }
}
