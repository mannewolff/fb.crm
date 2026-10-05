package org.mwolff.fbcrm.mail.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.mail.domain.OutboxMessage;

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

  /**
   * Die Zeile zu einem Zustellauftrag — jedes Feld kommt aus dem Fachobjekt (Plan #238, A5).
   *
   * <p>Die Zeile holt sich ihren Stand selbst, statt ihn als Liste von acht Parametern zu bekommen:
   * Der Adapter nennt dann am Aufruf nur noch, <i>was</i> abgebildet wird, und ein neues Feld des
   * Auftrags landet hier und nicht zusaetzlich in jeder Aufrufstelle.
   *
   * @param nachricht der Zustellauftrag, dessen Stand die Zeile tragen soll
   * @return die Zeile; ihre Kennung ist die des Auftrags und damit {@code null}, solange der
   *     Auftrag noch nicht geschrieben wurde
   */
  static OutboxMessageEntity aus(final OutboxMessage nachricht) {
    return new OutboxMessageEntity(nachricht);
  }

  private OutboxMessageEntity(final OutboxMessage nachricht) {
    this.id = nachricht.id();
    this.recipient = nachricht.recipient();
    this.subject = nachricht.subject();
    this.body = nachricht.body();
    this.attempts = nachricht.attempts();
    this.nextAttemptAt = nachricht.nextAttemptAt();
    this.sentAt = nachricht.sentAt();
    this.createdAt = nachricht.createdAt();
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
