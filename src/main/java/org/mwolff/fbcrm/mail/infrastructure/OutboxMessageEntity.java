package org.mwolff.fbcrm.mail.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** Die Zeile der Tabelle {@code outbox_message} aus {@code V1__baseline.sql}. */
@Entity
@Table(name = "outbox_message")
class OutboxMessageEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private @Nullable Long id;

  @Column(name = "recipient", nullable = false, length = 320)
  private String recipient;

  @Column(name = "subject", nullable = false, length = 255)
  private String subject;

  @Column(name = "body", nullable = false, columnDefinition = "text")
  private String body;

  @Column(name = "attempts", nullable = false)
  private int attempts;

  @Column(name = "next_attempt_at", nullable = false)
  private Instant nextAttemptAt;

  @Column(name = "sent_at")
  private @Nullable Instant sentAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  /*
   * JPA verlangt einen parameterlosen Konstruktor und fuellt die Felder danach selbst; NullAway
   * sieht diesen Weg nicht und meldete sonst nicht initialisierte Felder.
   */
  @SuppressWarnings("NullAway.Init")
  protected OutboxMessageEntity() {
    // Von Hibernate benutzt.
  }

  OutboxMessageEntity(
      final @Nullable Long id,
      final String recipient,
      final String subject,
      final String body,
      final int attempts,
      final Instant nextAttemptAt,
      final @Nullable Instant sentAt,
      final Instant createdAt) {
    this.id = id;
    this.recipient = recipient;
    this.subject = subject;
    this.body = body;
    this.attempts = attempts;
    this.nextAttemptAt = nextAttemptAt;
    this.sentAt = sentAt;
    this.createdAt = createdAt;
  }

  @Nullable Long getId() {
    return id;
  }

  String getRecipient() {
    return recipient;
  }

  String getSubject() {
    return subject;
  }

  String getBody() {
    return body;
  }

  int getAttempts() {
    return attempts;
  }

  Instant getNextAttemptAt() {
    return nextAttemptAt;
  }

  @Nullable Instant getSentAt() {
    return sentAt;
  }

  Instant getCreatedAt() {
    return createdAt;
  }
}
