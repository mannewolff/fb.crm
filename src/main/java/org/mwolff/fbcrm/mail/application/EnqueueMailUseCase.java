package org.mwolff.fbcrm.mail.application;

import java.time.Clock;
import org.mwolff.fbcrm.mail.domain.OutboxMessage;
import org.mwolff.fbcrm.mail.domain.OutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stellt einen Zustellauftrag ins Postausgangsfach (E7).
 *
 * <p><b>{@code Propagation.MANDATORY} ist der ganze Punkt.</b> Der Auftrag gehoert in die
 * Transaktion des Aufrufers — wird die zurueckgerollt, verschwindet er mit ihr. Ohne diesen Riegel
 * waere „in derselben Transaktion" eine Zusage, die jeder spaetere Aufrufer versehentlich brechen
 * koennte, ohne dass etwas anschlaegt: Der Auftrag entstuende in einer eigenen Transaktion, und die
 * Mail ginge zu einem Reset-Token hinaus, das es nie gab. Mit ihm scheitert ein Aufruf ausserhalb
 * einer Transaktion sofort und sichtbar.
 */
@Service
public class EnqueueMailUseCase {

  private final OutboxRepository outbox;
  private final Clock clock;

  public EnqueueMailUseCase(final OutboxRepository outbox, final Clock clock) {
    this.outbox = outbox;
    this.clock = clock;
  }

  /**
   * Nimmt eine Nachricht zur Zustellung an.
   *
   * @param recipient Empfaengeradresse
   * @param subject Betreff
   * @param body Rumpf der Nachricht
   * @throws org.springframework.transaction.IllegalTransactionStateException wenn der Aufruf
   *     ausserhalb einer Transaktion geschieht
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void enqueue(final String recipient, final String subject, final String body) {
    outbox.save(OutboxMessage.pending(recipient, subject, body, clock.instant()));
  }
}
