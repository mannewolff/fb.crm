package org.mwolff.fbcrm.auftrag.infrastructure;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring-Data-Zugriff auf {@code auftrag_position}.
 *
 * <p>Gelesen wird immer je Auftrag und immer nach Platz — die Abfrage trifft damit den Schluessel
 * von {@code auftrag_position_reihenfolge}.
 */
interface SpringDataAuftragPositionRepository extends JpaRepository<AuftragPositionEntity, Long> {

  /** Die Positionen eines Auftrags in der Reihenfolge ihrer Plaetze. */
  @Query(
      """
      select p from AuftragPositionEntity p
      where p.auftragId = :auftragId
      order by p.position
      """)
  List<AuftragPositionEntity> findByAuftrag(@Param("auftragId") long auftragId);

  /**
   * Die Positionen mehrerer Auftraege in einer Abfrage, nach Auftrag und Platz geordnet.
   *
   * <p>Fuer die Auftragsliste eines Vorgangs: Ein {@code findByAuftrag} je Zeile ergaebe die
   * bekannte Abfrage-Lawine, und ohne die Positionen liesse sich die Summe nicht rechnen (E11).
   *
   * @param auftragIds die Kennungen der Auftraege; nie leer — der Aufrufer faengt den Fall ab, weil
   *     {@code in ()} kein gueltiges SQL ist
   */
  @Query(
      """
      select p from AuftragPositionEntity p
      where p.auftragId in :auftragIds
      order by p.auftragId, p.position
      """)
  List<AuftragPositionEntity> findByAuftraege(@Param("auftragIds") Collection<Long> auftragIds);

  /**
   * Loescht alle Positionen eines Auftrags.
   *
   * <p>Als Massenloeschung und nicht als {@code deleteAll} ueber geladene Zeilen: Hibernate ordnet
   * Einfuegungen vor Loeschungen, und die neuen Plaetze stiessen dann auf die alten (UNIQUE je
   * Platz). Diese Anweisung laeuft sofort — {@code flushAutomatically} schreibt vorher aus, {@code
   * clearAutomatically} raeumt die Sitzung danach auf, damit keine geloeschte Zeile darin
   * zurueckbleibt.
   *
   * @param auftragId Kennung des Auftrags
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("delete from AuftragPositionEntity p where p.auftragId = :auftragId")
  void loescheZuAuftrag(@Param("auftragId") long auftragId);
}
