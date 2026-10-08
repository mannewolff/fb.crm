package org.mwolff.fbcrm.mail.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.mail.domain.OutboxMessage;
import org.mwolff.fbcrm.mail.domain.OutboxRepository;

/**
 * Das Einstellen eines Zustellauftrags (E7).
 *
 * <p>Der Anwendungsfall schickt nichts — er legt eine Zeile an. Dass er das <b>innerhalb</b> der
 * aufrufenden Transaktion tut, erzwingt {@code Propagation.MANDATORY}; nachgewiesen wird das gegen
 * einen echten Transaktionsmanager in {@code RequestPasswordResetUseCaseIT}.
 */
@ExtendWith(MockitoExtension.class)
class EnqueueMailUseCaseTest {

  private static final Instant JETZT = Instant.parse("2026-09-18T10:00:00Z");

  @Mock private OutboxRepository outbox;

  @Captor private ArgumentCaptor<OutboxMessage> eingestellte;

  private EnqueueMailUseCase anwendungsfall() {
    return new EnqueueMailUseCase(outbox, Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  private OutboxMessage eingestellterAuftrag() {
    verify(outbox).save(eingestellte.capture());
    return eingestellte.getValue();
  }

  @Test
  void enqueue_thenStoresRecipientSubjectAndBody() {
    // When
    anwendungsfall().enqueue("manne@example.org", "Betreff", "Rumpf");

    // Then
    assertThat(eingestellterAuftrag())
        .extracting(OutboxMessage::recipient, OutboxMessage::subject, OutboxMessage::body)
        .containsExactly("manne@example.org", "Betreff", "Rumpf");
  }

  @Test
  void enqueue_thenStartsWithoutAnyAttempt() {
    // When
    anwendungsfall().enqueue("manne@example.org", "Betreff", "Rumpf");

    // Then
    assertThat(eingestellterAuftrag().attempts()).isZero();
  }

  @Test
  void enqueue_thenStampsTheAuftragWithTheInjectedClock() {
    // When
    anwendungsfall().enqueue("manne@example.org", "Betreff", "Rumpf");

    // Then — der naechste Versuch ist sofort faellig, der Auftrag noch nicht zugestellt.
    assertThat(eingestellterAuftrag())
        .extracting(OutboxMessage::createdAt, OutboxMessage::nextAttemptAt, OutboxMessage::sentAt)
        .containsExactly(JETZT, JETZT, null);
  }
}
