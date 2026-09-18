package org.mwolff.fbcrm.mail.infrastructure;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring-Data-Zugriff auf {@code outbox_message}.
 *
 * <p>Die Faelligkeitsabfrage trifft genau den partiellen Index {@code outbox_message_pending_idx}
 * aus {@code V1__baseline.sql}: Sie fragt nur nach dem, was noch offen ist.
 *
 * <p>Der Filter auf {@code attempts} steht hier <b>und</b> als Riegel in {@code OutboxDispatcher}.
 * Die Doppelung ist Absicht und hat zwei verschiedene Gruende: Hier haelt sie aufgegebene Auftraege
 * aus jedem Durchgang heraus, statt sie bis in alle Ewigkeit mitzulesen; dort macht sie die Grenze
 * zu einer Entscheidung im Code, die ein Test ohne Datenbank pruefen kann.
 */
interface SpringDataOutboxRepository extends JpaRepository<OutboxMessageEntity, Long> {

  @Query(
      """
      select m from OutboxMessageEntity m
      where m.sentAt is null
        and m.nextAttemptAt <= :jetzt
        and m.attempts < :maxAttempts
      order by m.nextAttemptAt asc
      """)
  List<OutboxMessageEntity> findDue(
      @Param("jetzt") Instant jetzt, @Param("maxAttempts") int maxAttempts, Pageable seite);

  /*
   * @Transactional steht hier, nicht nur an OutboxCleanup: Ein @Modifying-Query verlangt eine
   * Transaktion, und ohne sie scheitert der Aufruf zur Laufzeit statt beim Uebersetzen. Spring
   * Data setzt die Grenze bei save und delete aus demselben Grund selbst; die fachliche Klammer
   * bleibt trotzdem OutboxCleanup.
   */
  @Transactional
  @Modifying
  @Query("delete from OutboxMessageEntity m where m.sentAt is not null and m.sentAt < :grenze")
  int deleteSentBefore(@Param("grenze") Instant grenze);
}
