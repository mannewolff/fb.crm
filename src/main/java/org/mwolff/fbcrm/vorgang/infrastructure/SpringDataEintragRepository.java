package org.mwolff.fbcrm.vorgang.infrastructure;

import java.time.Instant;
import java.util.Collection;
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

  /**
   * Der juengste Zeitpunkt je Vorgang, fuer alle Zeilen der Uebersicht auf einmal (Kriterium 2) —
   * eine Abfrage statt einer je Zeile, wie die Zaehlung der Ansprechpartner an der Firma.
   *
   * <p>Ein Vorgang ohne Eintrag kommt nicht vor: Die Gruppierung laeuft ueber die Eintraege, und wo
   * keiner ist, entsteht auch keine Gruppe. Der Adapter gibt den Fall so weiter, statt ihn mit
   * einem erfundenen Zeitpunkt zu fuellen.
   */
  @Query(
      """
      select e.vorgangId as vorgangId, max(e.geschehenAm) as geschehenAm
      from VorgangEintragEntity e
      where e.vorgangId in :vorgangIds
      group by e.vorgangId
      """)
  List<JuengstesGeschehen> juengstesGeschehenJeVorgang(
      @Param("vorgangIds") Collection<Long> vorgangIds);

  /** Eine Zeile der Aggregation: ein Vorgang und der Zeitpunkt seines juengsten Eintrags. */
  interface JuengstesGeschehen {

    /** Kennung des Vorgangs. */
    long getVorgangId();

    /** Zeitpunkt des juengsten Geschehens in seiner Historie. */
    Instant getGeschehenAm();
  }
}
