package org.mwolff.fbcrm.mail.infrastructure;

import java.time.Clock;
import java.time.Instant;
import org.mwolff.fbcrm.mail.MailProperties;
import org.mwolff.fbcrm.mail.OutboxProperties;
import org.mwolff.fbcrm.mail.domain.MailGateway;
import org.mwolff.fbcrm.mail.domain.OutboxMessage;
import org.mwolff.fbcrm.mail.domain.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Der Hintergrundjob, der das Postausgangsfach leert (E7).
 *
 * <p><b>Warum der Riegel auf {@code fbcrm.mail.enabled} nicht fehlen darf:</b> {@code
 * docker-compose.yml} liefert {@code FBCRM_OUTBOX_ENABLED=true} zusammen mit {@code
 * FBCRM_MAIL_ENABLED=false} und einem leeren SMTP-Host. Ohne ihn liefe jeder Auftrag gegen diesen
 * leeren Host und haette seine acht Versuche verbrannt, bevor der Betreiber den Mailversand
 * ueberhaupt eingerichtet hat. Mit ihm bleiben die Auftraege mit {@code attempts = 0} liegen und
 * gehen hinaus, sobald ein Mailserver eingetragen ist.
 *
 * <p><b>Der Riegel auf {@code maxAttempts} steht zweimal:</b> in der Abfrage von {@code
 * SpringDataOutboxRepository}, damit aufgegebene Auftraege nicht in jedem Durchgang mitgelesen
 * werden, und hier, damit die Grenze eine Entscheidung im Code bleibt, die ein Test ohne Datenbank
 * pruefen kann. Dass beide dasselbe Ziel haben, ist Absicht.
 *
 * <p>Das Protokoll nennt ausschliesslich die Id des Auftrags — nie Empfaenger, Betreff oder Rumpf.
 * Der Rumpf einer Reset-Mail traegt den Link, und ein Log-Archiv ueberlebt die Stunde, die er gilt,
 * bei weitem ({@code NoTokenInLogTest}, CLAUDE-security.md).
 */
@Component
@ConditionalOnProperty(name = "fbcrm.outbox.enabled", havingValue = "true")
public class OutboxDispatcher {

  /**
   * Zahl der Auftraege, die ein Durchgang hoechstens mitnimmt.
   *
   * <p>Ohne Obergrenze zoege ein Durchgang das gesamte Postausgangsfach in den Speicher — und
   * hielte waehrenddessen eine Transaktion offen.
   */
  static final int BATCH_SIZE = 50;

  private static final Logger LOG = LoggerFactory.getLogger(OutboxDispatcher.class);

  private final OutboxRepository outbox;
  private final MailGateway gateway;
  private final OutboxProperties properties;
  private final MailProperties mail;
  private final Clock clock;

  public OutboxDispatcher(
      final OutboxRepository outbox,
      final MailGateway gateway,
      final OutboxProperties properties,
      final MailProperties mail,
      final Clock clock) {
    this.outbox = outbox;
    this.gateway = gateway;
    this.properties = properties;
    this.mail = mail;
    this.clock = clock;
  }

  /** Stellt zu, was faellig ist. */
  @Scheduled(fixedDelayString = "${fbcrm.outbox.poll-interval-ms}")
  @Transactional
  public void dispatch() {
    if (!mail.enabled()) {
      return;
    }
    final Instant jetzt = clock.instant();
    outbox
        .findDue(jetzt, properties.maxAttempts(), BATCH_SIZE)
        .forEach(auftrag -> stelleZu(auftrag, jetzt));
  }

  private void stelleZu(final OutboxMessage auftrag, final Instant jetzt) {
    if (auftrag.isExhausted(properties.maxAttempts())) {
      return;
    }
    try {
      gateway.send(auftrag.recipient(), auftrag.subject(), auftrag.body());
      outbox.save(auftrag.delivered(jetzt));
    } catch (final MailException nichtZugestellt) {
      final OutboxMessage gescheitert = auftrag.failed(jetzt, properties.retryBackoffBase());
      outbox.save(gescheitert);
      protokolliere(gescheitert);
      LOG.debug("Ursache des fehlgeschlagenen Zustellversuchs", nichtZugestellt);
    }
  }

  private void protokolliere(final OutboxMessage gescheitert) {
    if (gescheitert.isExhausted(properties.maxAttempts())) {
      LOG.error(
          "Zustellauftrag {} nach {} Versuchen aufgegeben; er bleibt im Postausgangsfach liegen.",
          gescheitert.requireId(),
          gescheitert.attempts());
      return;
    }
    LOG.warn(
        "Zustellauftrag {} fehlgeschlagen (Versuch {}); ein weiterer Versuch folgt.",
        gescheitert.requireId(),
        gescheitert.attempts());
  }
}
