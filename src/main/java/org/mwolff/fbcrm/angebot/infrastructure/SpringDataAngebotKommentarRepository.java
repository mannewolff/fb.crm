package org.mwolff.fbcrm.angebot.infrastructure;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring-Data-Zugriff auf {@code angebot_kommentar}.
 *
 * <p>Gelesen wird je Angebot — die Abfrage trifft damit {@code angebot_kommentar_angebot_idx}. Ein
 * {@code order by} steht bewusst nicht darin: Die Reihenfolge der Ansicht setzt die
 * Anwendungsschicht, wie bei den Angebotslisten (Plan #141, E8).
 *
 * <p>Die Kennung geht als gebundener Parameter in die Abfrage, wie in jeder Abfrage dieses Moduls.
 */
interface SpringDataAngebotKommentarRepository extends JpaRepository<AngebotKommentarEntity, Long> {

  /**
   * Die Kommentare eines Angebots, in keiner zugesagten Reihenfolge.
   *
   * @param angebotId Kennung des Angebots
   */
  @Query(
      """
      select k from AngebotKommentarEntity k
      where k.angebotId = :angebotId
      """)
  List<AngebotKommentarEntity> findByAngebot(@Param("angebotId") long angebotId);
}
