package org.mwolff.fbcrm.mail.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.mail.domain.OutboxMessage;
import org.mwolff.fbcrm.mail.domain.OutboxRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Die beiden Abfragen des Postausgangsfachs gegen echte Zeilen (E7).
 *
 * <p>Sie sind der Gegenstand, den ein Mock nicht pruefen kann: Welche Auftraege ein Durchgang
 * ueberhaupt sieht, und welche die Reinigung loescht. Besonders die Reinigung: Sie darf
 * ausschliesslich <b>Zugestelltes</b> entfernen. Ein Auftrag, der nie hinausging, ist der einzige
 * Beleg dafuer, dass der Mailweg dieser Instanz nicht funktioniert — er bleibt liegen.
 */
class JpaOutboxRepositoryIT extends AbstractIntegrationTest {

  private static final Instant JETZT = Instant.parse("2026-09-18T10:00:00Z");
  private static final int MAX_VERSUCHE = 8;

  private final OutboxRepository outbox;
  private final JdbcTemplate jdbc;

  @Autowired
  JpaOutboxRepositoryIT(final OutboxRepository outbox, final JdbcTemplate jdbc) {
    this.outbox = outbox;
    this.jdbc = jdbc;
  }

  @BeforeEach
  void leereDasPostausgangsfach() {
    jdbc.execute("TRUNCATE outbox_message RESTART IDENTITY CASCADE");
  }

  private OutboxMessage auftrag(
      final String empfaenger,
      final int versuche,
      final Instant naechsterVersuch,
      final Instant zugestelltAm) {
    return outbox.save(
        new OutboxMessage(
            null, empfaenger, "Betreff", "Rumpf", versuche, naechsterVersuch, zugestelltAm, JETZT));
  }

  private List<String> faelligeEmpfaenger() {
    return outbox.findDue(JETZT, MAX_VERSUCHE, 50).stream().map(OutboxMessage::recipient).toList();
  }

  @Test
  void findDue_thenReturnsAnOrderThatIsDueNow() {
    // Given
    auftrag("faellig@example.org", 0, JETZT, null);

    // When / Then
    assertThat(faelligeEmpfaenger()).containsExactly("faellig@example.org");
  }

  @Test
  void findDue_thenSkipsAnOrderWhoseNextAttemptIsStillAhead() {
    // Given
    auftrag("spaeter@example.org", 1, JETZT.plusSeconds(60), null);

    // When / Then
    assertThat(faelligeEmpfaenger()).isEmpty();
  }

  @Test
  void findDue_thenSkipsAnOrderThatHasAlreadyBeenDelivered() {
    // Given
    auftrag("zugestellt@example.org", 1, JETZT, JETZT.minusSeconds(60));

    // When / Then
    assertThat(faelligeEmpfaenger()).isEmpty();
  }

  @Test
  void findDue_thenSkipsAnOrderThatHasUsedUpItsAttempts() {
    // Given — nach der achten vergeblichen Zustellung wird der Auftrag nicht mehr aufgegriffen.
    auftrag("aufgegeben@example.org", MAX_VERSUCHE, JETZT, null);

    // When / Then
    assertThat(faelligeEmpfaenger()).isEmpty();
  }

  @Test
  void findDue_thenTakesTheOldestOrderFirst() {
    // Given
    auftrag("spaet@example.org", 0, JETZT.minusSeconds(10), null);
    auftrag("frueh@example.org", 0, JETZT.minusSeconds(60), null);

    // When / Then
    assertThat(faelligeEmpfaenger()).containsExactly("frueh@example.org", "spaet@example.org");
  }

  @Test
  void findDue_thenNeverReturnsMoreThanTheGivenLimit() {
    // Given
    auftrag("eins@example.org", 0, JETZT.minusSeconds(60), null);
    auftrag("zwei@example.org", 0, JETZT.minusSeconds(30), null);

    // When / Then
    assertThat(outbox.findDue(JETZT, MAX_VERSUCHE, 1)).hasSize(1);
  }

  @Test
  void deleteSentBefore_thenRemovesWhatWasDeliveredBeforeTheThreshold() {
    // Given
    auftrag("alt@example.org", 1, JETZT, JETZT.minusSeconds(3600));

    // When
    final int geloescht = outbox.deleteSentBefore(JETZT.minusSeconds(60));

    // Then
    assertThat(geloescht).isEqualTo(1);
  }

  @Test
  void deleteSentBefore_thenKeepsWhatWasDeliveredAfterTheThreshold() {
    // Given
    auftrag("frisch@example.org", 1, JETZT, JETZT.minusSeconds(10));

    // When
    outbox.deleteSentBefore(JETZT.minusSeconds(60));

    // Then
    assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_message", Long.class))
        .isEqualTo(1L);
  }

  @Test
  void deleteSentBefore_thenNeverRemovesAnUndeliveredOrder() {
    // Given — auch ein uralter, nie zugestellter Auftrag bleibt liegen.
    auftrag("nie@example.org", MAX_VERSUCHE, JETZT.minusSeconds(864_000), null);

    // When
    outbox.deleteSentBefore(JETZT);

    // Then
    assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_message", Long.class))
        .isEqualTo(1L);
  }
}
