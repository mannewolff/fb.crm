package org.mwolff.fbcrm.angebot.infrastructure;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring-Data-Zugriff auf {@code angebot_position}.
 *
 * <p>Gelesen wird immer je Angebot und immer nach Platz — die Abfrage trifft damit den Schluessel
 * von {@code angebot_position_reihenfolge}.
 *
 * <p><b>Keine Massenloeschung je Angebot.</b> Bis Plan #169, E2 ersetzte {@code
 * JpaAngebotRepository.save} die Positionszeilen und loeschte sie dafuer zuvor alle. Seit die
 * Position eine dauerhafte Kennung traegt, werden die Zeilen fortgeschrieben, und weg muessen nur
 * die, die die neue Liste nicht mehr nennt — die kennt der Adapter namentlich und gibt sie an
 * {@code deleteAll}.
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
   * <p>Fuer die Angebotsliste einer Firma: Ein {@code findByAngebot} je Zeile ergaebe die bekannte
   * Abfrage-Lawine, und ohne die Positionen liesse sich die Summe nicht rechnen (E5, E20).
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
}
