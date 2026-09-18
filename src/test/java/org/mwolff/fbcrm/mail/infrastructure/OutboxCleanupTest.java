package org.mwolff.fbcrm.mail.infrastructure;

import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.mail.OutboxProperties;
import org.mwolff.fbcrm.mail.domain.OutboxRepository;

/**
 * Die Reinigung des Postausgangsfachs (E7).
 *
 * <p>Geloescht wird ausschliesslich <b>Zugestelltes</b>, und erst nach der Aufbewahrungsfrist. Ein
 * Auftrag, der nie hinausging, bleibt liegen — er ist der einzige Beleg dafuer, dass der Mailweg
 * dieser Instanz nicht funktioniert. Dass die Abfrage genau diese Trennung trifft, weist {@code
 * JpaOutboxRepositoryIT} gegen echte Zeilen nach; hier steht die Grenze, ab der geloescht wird.
 */
@ExtendWith(MockitoExtension.class)
class OutboxCleanupTest {

  private static final Instant JETZT = Instant.parse("2026-09-18T10:00:00Z");

  @Mock private OutboxRepository outbox;

  private OutboxCleanup reinigung(final int aufbewahrungstage) {
    return new OutboxCleanup(
        outbox,
        new OutboxProperties(true, 5000L, 8, aufbewahrungstage),
        Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  @Test
  void purge_thenDeletesEverythingDeliveredBeforeTheRetentionPeriod() {
    // When
    reinigung(7).purge();

    // Then
    verify(outbox).deleteSentBefore(Instant.parse("2026-09-11T10:00:00Z"));
  }

  @Test
  void purge_givenAShorterRetention_thenMovesTheThreshold() {
    // When — die Frist ist ein Schalter, kein fester Wert.
    reinigung(1).purge();

    // Then
    verify(outbox).deleteSentBefore(Instant.parse("2026-09-17T10:00:00Z"));
  }
}
