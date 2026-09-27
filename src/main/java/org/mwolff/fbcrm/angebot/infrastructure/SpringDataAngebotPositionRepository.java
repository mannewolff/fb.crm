package org.mwolff.fbcrm.angebot.infrastructure;

import java.util.Collection;
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
   * Die Positionen mehrerer Angebote in einer Abfrage, nach Angebot und Platz geordnet.
   *
   * <p>Fuer die Angebotsliste eines Vorgangs: Ein {@code findByAngebot} je Zeile ergaebe die
   * bekannte Abfrage-Lawine, und ohne die Positionen liesse sich die Summe nicht rechnen (E5, E20).
   *
   * @param angebotIds die Kennungen der Angebote; nie leer — der Aufrufer faengt den Fall ab, weil
   *     {@code in ()} kein gueltiges SQL ist
   */
  @Query(
      """
      select p from AngebotPositionEntity p
      where p.angebotId in :angebotIds
      order by p.angebotId, p.position
      """)
  List<AngebotPositionEntity> findByAngebote(@Param("angebotIds") Collection<Long> angebotIds);

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
