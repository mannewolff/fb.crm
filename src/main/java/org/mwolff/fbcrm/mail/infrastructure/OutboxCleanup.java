package org.mwolff.fbcrm.mail.infrastructure;

import java.time.Clock;
import java.util.concurrent.TimeUnit;
import org.mwolff.fbcrm.mail.OutboxProperties;
import org.mwolff.fbcrm.mail.domain.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Die Reinigung des Postausgangsfachs (E7).
 *
 * <p>Geloescht wird ausschliesslich <b>Zugestelltes</b>, und erst nach {@code
 * FBCRM_OUTBOX_RETENTION_DAYS}. Ein Auftrag, der nie hinausging, bleibt liegen — er ist der einzige
 * Beleg dafuer, dass der Mailweg dieser Instanz nicht funktioniert, und den nimmt ihm keine
 * Aufraeumroutine weg.
 *
 * <p>Der Takt ist bewusst kein Schalter: Eine Aufbewahrung wird in Tagen gemessen, da aendert eine
 * Stunde mehr oder weniger nichts, und ein weiterer Schalter waere eine weitere Stellschraube, die
 * jemand falsch stellen kann.
 */
@Component
@ConditionalOnProperty(name = "fbcrm.outbox.enabled", havingValue = "true")
public class OutboxCleanup {

  private static final Logger LOG = LoggerFactory.getLogger(OutboxCleanup.class);

  private final OutboxRepository outbox;
  private final OutboxProperties properties;
  private final Clock clock;

  public OutboxCleanup(
      final OutboxRepository outbox, final OutboxProperties properties, final Clock clock) {
    this.outbox = outbox;
    this.properties = properties;
    this.clock = clock;
  }

  /** Raeumt zugestellte Auftraege ab, die aelter sind als die Aufbewahrungsfrist. */
  @Scheduled(initialDelay = 1, fixedDelay = 1, timeUnit = TimeUnit.HOURS)
  @Transactional
  public void purge() {
    final int geloescht = outbox.deleteSentBefore(clock.instant().minus(properties.retention()));
    LOG.debug("{} zugestellte Auftraege aus dem Postausgangsfach entfernt.", geloescht);
  }
}
