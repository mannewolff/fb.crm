package org.mwolff.fbcrm.angebot.infrastructure;

import java.util.List;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring-Data-Zugriff auf {@code angebot}.
 *
 * <p>Gelesen wird ein Angebot ueber seine Kennung, ueber seine Firma oder ueber seinen Status,
 * geschrieben als Ganzes.
 */
interface SpringDataAngebotRepository extends JpaRepository<AngebotEntity, Long> {

  /**
   * Die Angebote einer Firma — die Abfrage trifft {@code angebot_firma_idx}.
   *
   * <p>Ohne {@code order by}: In welcher Reihenfolge die Ansicht sie zeigt, entscheidet Kriterium
   * 20 und damit die Anwendungsschicht (E25).
   *
   * @param firmaId Kennung der Firma
   */
  @Query("select a from AngebotEntity a where a.firmaId = :firmaId")
  List<AngebotEntity> findByFirma(@Param("firmaId") long firmaId);

  /**
   * Die Angebote in einem Status, fuer die Uebersicht mit Filter (Kriterium 8).
   *
   * <p>Ohne {@code order by} aus demselben Grund wie {@link #findByFirma(long)}.
   *
   * @param status der gesuchte Status
   */
  @Query("select a from AngebotEntity a where a.status = :status")
  List<AngebotEntity> findByStatus(@Param("status") Angebotsstatus status);
}
