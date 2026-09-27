package org.mwolff.fbcrm.angebot.infrastructure;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring-Data-Zugriff auf {@code angebot_position}.
 *
 * <p>Gelesen wird immer je Angebot und immer nach Platz — die Abfrage trifft damit den Schluessel
 * von {@code angebot_position_reihenfolge}.
 */
interface SpringDataAngebotPositionRepository extends JpaRepository<AngebotPositionEntity, Long> {

  /** Die Positionen eines Angebots in der Reihenfolge ihrer Plaetze. */
  @Query(
      """
      select p from AngebotPositionEntity p
      where p.angebotId = :angebotId
      order by p.position
      """)
  List<AngebotPositionEntity> findByAngebot(@Param("angebotId") long angebotId);

  /**
   * Loescht alle Positionen eines Angebots.
   *
   * <p>Als Massenloeschung und nicht als {@code deleteAll} ueber geladene Zeilen: Hibernate ordnet
   * Einfuegungen vor Loeschungen, und die neuen Plaetze stiessen dann auf die alten (UNIQUE je
   * Platz). Diese Anweisung laeuft sofort — {@code flushAutomatically} schreibt vorher aus, {@code
   * clearAutomatically} raeumt die Sitzung danach auf, damit keine geloeschte Zeile darin
   * zurueckbleibt.
   *
   * @param angebotId Kennung des Angebots
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("delete from AngebotPositionEntity p where p.angebotId = :angebotId")
  void loescheZuAngebot(@Param("angebotId") long angebotId);
}
