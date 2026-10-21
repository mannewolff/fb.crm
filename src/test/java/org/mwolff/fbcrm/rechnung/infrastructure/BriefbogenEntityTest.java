package org.mwolff.fbcrm.rechnung.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.rechnung.domain.Briefbogen;
import org.mwolff.fbcrm.rechnung.domain.Briefpapierblatt;
import org.mwolff.fbcrm.rechnung.domain.Randmasse;

/**
 * Hin und zurueck zwischen {@link BriefbogenEntity} und {@link Briefbogen} (Plan #363, E15).
 *
 * <p>Jeder Wert ist ein anderer, damit eine vertauschte Zuweisung auffiele. Einmal mit Blatt und
 * einmal ohne — ohne hinterlegtes Briefpapier stehen alle vier Spalten des Blattes leer.
 */
class BriefbogenEntityTest {

  private static final Instant GEAENDERT = Instant.parse("2026-10-19T12:00:00Z");

  private static final Randmasse RAENDER = new Randmasse(251, 202, 983, 204, 205, 206);

  @Test
  void aus_thenCarriesTheFixedKeyAndTheTime() {
    // When
    final BriefbogenEntity zeile =
        BriefbogenEntity.aus(new Briefbogen(null, true, false, RAENDER), GEAENDERT);

    // Then
    assertThat(zeile.getId()).isEqualTo(BriefbogenEntity.ZEILE);
    assertThat(zeile.getUpdatedAt()).isEqualTo(GEAENDERT);
  }

  @Test
  void alsBriefbogen_givenABlatt_thenTheRoundTripKeepsEveryValue() {
    // Given
    final Briefbogen briefbogen =
        new Briefbogen(
            new Briefpapierblatt("briefpapier/abc.pdf", "kopf.pdf", 123_456L, 2),
            false,
            true,
            RAENDER);

    // When / Then
    assertThat(BriefbogenEntity.aus(briefbogen, GEAENDERT).alsBriefbogen()).isEqualTo(briefbogen);
  }

  @Test
  void alsBriefbogen_givenNoBlatt_thenTheRoundTripKeepsSwitchesAndMargins() {
    // Given
    final Briefbogen briefbogen = new Briefbogen(null, true, false, RAENDER);

    // When / Then
    assertThat(BriefbogenEntity.aus(briefbogen, GEAENDERT).alsBriefbogen()).isEqualTo(briefbogen);
  }
}
