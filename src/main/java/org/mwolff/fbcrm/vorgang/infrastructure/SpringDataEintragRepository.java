package org.mwolff.fbcrm.vorgang.infrastructure;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring-Data-Zugriff auf {@code vorgang_eintrag}.
 *
 * <p>Die Historie wird immer je Vorgang und immer in derselben Reihenfolge gelesen — juengstes
 * Geschehen oben, bei gleichem Zeitpunkt der zuletzt erfasste Eintrag oben (Kriterium 15). Die
 * Abfrage trifft damit genau den Index {@code vorgang_eintrag_verlauf_idx} aus der Migration.
 *
 * <p>Ohne Seitenteilung: Die Detailansicht zeigt die Historie in einem Stueck (E25).
 */
interface SpringDataEintragRepository extends JpaRepository<VorgangEintragEntity, Long> {

  @Query(
      """
      select e from VorgangEintragEntity e
      where e.vorgangId = :vorgangId
      order by e.geschehenAm desc, e.id desc
      """)
  List<VorgangEintragEntity> findByVorgang(@Param("vorgangId") long vorgangId);
}
