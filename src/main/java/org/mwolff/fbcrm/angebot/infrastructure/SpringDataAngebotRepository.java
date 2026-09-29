package org.mwolff.fbcrm.angebot.infrastructure;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring-Data-Zugriff auf {@code angebot}.
 *
 * <p>Gelesen wird ein Angebot ueber seine Kennung oder ueber seine Firma, geschrieben als Ganzes.
 * Die Auswertungen kommen mit den Paketen, die sie brauchen.
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
}
