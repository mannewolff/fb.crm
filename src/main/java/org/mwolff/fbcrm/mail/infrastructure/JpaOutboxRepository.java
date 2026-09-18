package org.mwolff.fbcrm.mail.infrastructure;

import java.time.Instant;
import java.util.List;
import org.mwolff.fbcrm.mail.domain.OutboxMessage;
import org.mwolff.fbcrm.mail.domain.OutboxRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

/** Setzt den Port {@link OutboxRepository} auf JPA um und uebersetzt in beide Richtungen. */
@Repository
class JpaOutboxRepository implements OutboxRepository {

  private final SpringDataOutboxRepository jpa;

  JpaOutboxRepository(final SpringDataOutboxRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public OutboxMessage save(final OutboxMessage nachricht) {
    return toDomain(jpa.save(toEntity(nachricht)));
  }

  @Override
  public List<OutboxMessage> findDue(final Instant jetzt, final int maxAttempts, final int limit) {
    return jpa.findDue(jetzt, maxAttempts, PageRequest.ofSize(limit)).stream()
        .map(JpaOutboxRepository::toDomain)
        .toList();
  }

  @Override
  public int deleteSentBefore(final Instant grenze) {
    return jpa.deleteSentBefore(grenze);
  }

  private static OutboxMessage toDomain(final OutboxMessageEntity zeile) {
    return new OutboxMessage(
        zeile.getId(),
        zeile.getRecipient(),
        zeile.getSubject(),
        zeile.getBody(),
        zeile.getAttempts(),
        zeile.getNextAttemptAt(),
        zeile.getSentAt(),
        zeile.getCreatedAt());
  }

  private static OutboxMessageEntity toEntity(final OutboxMessage nachricht) {
    return new OutboxMessageEntity(
        nachricht.id(),
        nachricht.recipient(),
        nachricht.subject(),
        nachricht.body(),
        nachricht.attempts(),
        nachricht.nextAttemptAt(),
        nachricht.sentAt(),
        nachricht.createdAt());
  }
}
