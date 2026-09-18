package org.mwolff.fbcrm.mail.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.mail.domain.OutboxMessage;
import org.springframework.data.domain.Pageable;

/**
 * Die Uebersetzung zwischen Zustellauftrag und Zeile — in beide Richtungen.
 *
 * <p>Der Adapter steht hier gegen ein gemocktes Spring-Data-Repository, damit die Abbildung selbst
 * geprueft ist und nicht nur ihr Zusammenspiel mit der Datenbank ({@code JpaOutboxRepositoryIT}).
 */
@ExtendWith(MockitoExtension.class)
class JpaOutboxRepositoryTest {

  private static final Instant ANGELEGT = Instant.parse("2026-09-18T10:00:00Z");
  private static final Instant ZUGESTELLT = Instant.parse("2026-09-18T10:00:05Z");

  @Mock private SpringDataOutboxRepository jpa;

  @Captor private ArgumentCaptor<OutboxMessageEntity> gespeicherte;
  @Captor private ArgumentCaptor<Pageable> seite;

  @InjectMocks private JpaOutboxRepository repository;

  private static OutboxMessageEntity zeile(final Long id) {
    return new OutboxMessageEntity(
        id, "manne@example.org", "Betreff", "Rumpf", 2, ANGELEGT, ZUGESTELLT, ANGELEGT);
  }

  private static OutboxMessage auftrag(final Long id) {
    return new OutboxMessage(
        id, "manne@example.org", "Betreff", "Rumpf", 2, ANGELEGT, ZUGESTELLT, ANGELEGT);
  }

  @Test
  void save_thenWritesEveryFieldOfTheMessageIntoTheRow() {
    // Given
    when(jpa.save(any(OutboxMessageEntity.class))).thenReturn(zeile(11L));

    // When
    repository.save(auftrag(null));

    // Then
    verify(jpa).save(gespeicherte.capture());
    assertThat(gespeicherte.getValue())
        .satisfies(
            zeile -> assertThat(zeile.getId()).isNull(),
            zeile -> assertThat(zeile.getRecipient()).isEqualTo("manne@example.org"),
            zeile -> assertThat(zeile.getSubject()).isEqualTo("Betreff"),
            zeile -> assertThat(zeile.getBody()).isEqualTo("Rumpf"),
            zeile -> assertThat(zeile.getAttempts()).isEqualTo(2),
            zeile -> assertThat(zeile.getNextAttemptAt()).isEqualTo(ANGELEGT),
            zeile -> assertThat(zeile.getSentAt()).isEqualTo(ZUGESTELLT),
            zeile -> assertThat(zeile.getCreatedAt()).isEqualTo(ANGELEGT));
  }

  @Test
  void save_thenReturnsTheMessageWithTheGeneratedId() {
    // Given
    when(jpa.save(any(OutboxMessageEntity.class))).thenReturn(zeile(11L));

    // When
    final OutboxMessage gesichert = repository.save(auftrag(null));

    // Then
    assertThat(gesichert).isEqualTo(auftrag(11L));
  }

  @Test
  void findDue_thenTranslatesEveryRow() {
    // Given
    when(jpa.findDue(eq(ANGELEGT), eq(8), any(Pageable.class))).thenReturn(List.of(zeile(11L)));

    // When
    final List<OutboxMessage> faellige = repository.findDue(ANGELEGT, 8, 50);

    // Then
    assertThat(faellige).containsExactly(auftrag(11L));
  }

  @Test
  void findDue_thenAsksForAtMostTheGivenNumberOfRows() {
    // Given — ohne Obergrenze zoege ein Durchgang das gesamte Postausgangsfach in den Speicher.
    when(jpa.findDue(eq(ANGELEGT), eq(8), any(Pageable.class))).thenReturn(List.of());

    // When
    repository.findDue(ANGELEGT, 8, 50);

    // Then
    verify(jpa).findDue(eq(ANGELEGT), eq(8), seite.capture());
    assertThat(seite.getValue().getPageSize()).isEqualTo(50);
  }

  @Test
  void deleteSentBefore_thenPassesTheThresholdThrough() {
    // Given
    when(jpa.deleteSentBefore(ANGELEGT)).thenReturn(3);

    // When
    final int geloescht = repository.deleteSentBefore(ANGELEGT);

    // Then
    assertThat(geloescht).isEqualTo(3);
  }
}
