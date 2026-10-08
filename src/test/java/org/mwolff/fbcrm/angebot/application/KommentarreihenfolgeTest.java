package org.mwolff.fbcrm.angebot.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.angebot.domain.Angebotskommentar;

/**
 * Die eine Reihenfolge jeder Kommentarliste: neuester zuerst (Issue #140, Kriterium 4).
 *
 * <p>Gegenstand sind beide Stufen des Vergleichs — der Zeitpunkt absteigend und, bei Gleichstand,
 * die hoehere Kennung. Die zweite Stufe ist es, die zwei Aufrufe dieselbe Liste liefern laesst.
 */
class KommentarreihenfolgeTest {

  private static final Instant FRUEH = Instant.parse("2026-09-30T08:00:00Z");
  private static final Instant SPAET = Instant.parse("2026-09-30T09:00:00Z");

  private static Angebotskommentar kommentar(final long id, final Instant createdAt) {
    return new Angebotskommentar(Long.valueOf(id), 11L, "Text " + id, createdAt, createdAt);
  }

  @Test
  void neuesteZuerst_thenOrdersByCreatedAtDescending() {
    // Given
    final List<Angebotskommentar> kommentare =
        List.of(kommentar(1L, FRUEH), kommentar(2L, SPAET)).stream()
            .sorted(Kommentarreihenfolge.NEUESTE_ZUERST)
            .toList();

    // When / Then
    assertThat(kommentare).extracting(Angebotskommentar::id).containsExactly(2L, 1L);
  }

  @Test
  void neuesteZuerst_withTheSameCreatedAt_thenTheHigherIdComesFirst() {
    // Given — ohne die zweite Stufe waere die Reihenfolge zweier gleichzeitiger Kommentare offen.
    final List<Angebotskommentar> kommentare =
        List.of(kommentar(7L, FRUEH), kommentar(9L, FRUEH), kommentar(8L, FRUEH)).stream()
            .sorted(Kommentarreihenfolge.NEUESTE_ZUERST)
            .toList();

    // When / Then
    assertThat(kommentare).extracting(Angebotskommentar::id).containsExactly(9L, 8L, 7L);
  }
}
