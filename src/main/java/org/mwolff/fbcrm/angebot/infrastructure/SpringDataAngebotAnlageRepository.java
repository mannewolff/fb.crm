package org.mwolff.fbcrm.angebot.infrastructure;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring-Data-Zugriff auf {@code angebot_anlage}.
 *
 * <p>Gelesen wird je Angebot — die Abfrage trifft damit {@code angebot_anlage_angebot_idx}. Ein
 * {@code order by} steht bewusst nicht darin: Die Reihenfolge der Ansicht setzt die
 * Anwendungsschicht, wie bei Angeboten und Kommentaren (Plan #150, E10).
 *
 * <p>Die Kennung geht als gebundener Parameter in die Abfrage, wie in jeder Abfrage dieses Moduls.
 */
interface SpringDataAngebotAnlageRepository extends JpaRepository<AngebotAnlageEntity, Long> {

  /**
   * Die Anlagen eines Angebots, in keiner zugesagten Reihenfolge.
   *
   * @param angebotId Kennung des Angebots
   */
  @Query(
      """
      select a from AngebotAnlageEntity a
      where a.angebotId = :angebotId
      """)
  List<AngebotAnlageEntity> findByAngebot(@Param("angebotId") long angebotId);
}
