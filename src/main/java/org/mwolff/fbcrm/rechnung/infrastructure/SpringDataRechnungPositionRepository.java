package org.mwolff.fbcrm.rechnung.infrastructure;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring-Data-Zugriff auf {@code rechnung_position}.
 *
 * <p>Gelesen wird immer je Rechnung und immer nach Platz — die Abfrage trifft damit den Schluessel
 * von {@code rechnung_position_reihenfolge}.
 *
 * <p>Keine Massenloeschung je Rechnung: Was weg muss, kennt der Adapter namentlich und gibt es an
 * {@code deleteAll} — beim Fortschreiben die Zeilen, die die neue Liste nicht mehr nennt, beim
 * Loeschen einer Rechnung alle ihre Zeilen.
 */
interface SpringDataRechnungPositionRepository extends JpaRepository<RechnungPositionEntity, Long> {

  /**
   * Die Positionen einer Rechnung in der Reihenfolge ihrer Plaetze.
   *
   * @param rechnungId Kennung der Rechnung
   */
  @Query(
      """
      select p from RechnungPositionEntity p
      where p.rechnungId = :rechnungId
      order by p.position
      """)
  List<RechnungPositionEntity> findByRechnung(@Param("rechnungId") long rechnungId);

  /**
   * Die Positionen mehrerer Rechnungen in einer Abfrage, nach Rechnung und Platz geordnet.
   *
   * <p>Fuer die Rechnungsliste eines Angebots: Ein {@code findByRechnung} je Zeile ergaebe die
   * bekannte Abfrage-Lawine, und ohne die Positionen liesse sich die Summe nicht rechnen (E5).
   *
   * @param rechnungIds die Kennungen der Rechnungen; nie leer — der Aufrufer faengt den Fall ab,
   *     weil {@code in ()} kein gueltiges SQL ist
   */
  @Query(
      """
      select p from RechnungPositionEntity p
      where p.rechnungId in :rechnungIds
      order by p.rechnungId, p.position
      """)
  List<RechnungPositionEntity> findByRechnungen(@Param("rechnungIds") Collection<Long> rechnungIds);
}
