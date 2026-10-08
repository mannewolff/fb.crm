package org.mwolff.fbcrm.angebot.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.angebot.domain.Angebotsanlage;

/**
 * Die eine Reihenfolge jeder Anlagenliste: neueste zuerst (Issue #148, Kriterium 4; Plan #150,
 * E10).
 *
 * <p>Gegenstand sind beide Stufen des Vergleichs — der Zeitpunkt absteigend und, bei Gleichstand,
 * die hoehere Kennung. Die zweite Stufe ist es, die zwei Aufrufe dieselbe Liste liefern laesst:
 * Zwei Anlagen desselben Hochladens tragen denselben Zeitpunkt aus derselben Uhr.
 */
class AnlagenreihenfolgeTest {

  private static final Instant FRUEH = Instant.parse("2026-09-30T08:00:00Z");
  private static final Instant SPAET = Instant.parse("2026-09-30T09:00:00Z");

  private static Angebotsanlage anlage(final long id, final Instant createdAt) {
    return new Angebotsanlage(
        Long.valueOf(id),
        11L,
        "datei-" + id + ".pdf",
        17L,
        null,
        "angebot/11/anlage/" + id,
        createdAt);
  }

  @Test
  void neuesteZuerst_thenOrdersByCreatedAtDescending() {
    // Given
    final List<Angebotsanlage> anlagen =
        List.of(anlage(1L, FRUEH), anlage(2L, SPAET)).stream()
            .sorted(Anlagenreihenfolge.NEUESTE_ZUERST)
            .toList();

    // When / Then
    assertThat(anlagen).extracting(Angebotsanlage::id).containsExactly(2L, 1L);
  }

  @Test
  void neuesteZuerst_withTheSameCreatedAt_thenTheHigherIdComesFirst() {
    // Given — ohne die zweite Stufe waere die Reihenfolge zweier gleichzeitiger Anlagen offen.
    final List<Angebotsanlage> anlagen =
        List.of(anlage(7L, FRUEH), anlage(9L, FRUEH), anlage(8L, FRUEH)).stream()
            .sorted(Anlagenreihenfolge.NEUESTE_ZUERST)
            .toList();

    // When / Then
    assertThat(anlagen).extracting(Angebotsanlage::id).containsExactly(9L, 8L, 7L);
  }
}
