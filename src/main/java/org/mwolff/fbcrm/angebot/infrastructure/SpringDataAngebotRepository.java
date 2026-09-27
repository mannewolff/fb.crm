package org.mwolff.fbcrm.angebot.infrastructure;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring-Data-Zugriff auf {@code angebot}.
 *
 * <p>Gelesen wird ein Angebot ueber seine Kennung oder ueber seinen Vorgang, geschrieben als
 * Ganzes. Die Auswertungen kommen mit den Paketen, die sie brauchen.
 */
interface SpringDataAngebotRepository extends JpaRepository<AngebotEntity, Long> {

  /**
   * Die Angebote eines Vorgangs — die Abfrage trifft {@code angebot_vorgang_idx}.
   *
   * <p>Ohne {@code order by}: In welcher Reihenfolge die Ansicht sie zeigt, entscheidet Kriterium
   * 20 und damit die Anwendungsschicht (E25).
   *
   * @param vorgangId Kennung des Vorgangs
   */
  @Query("select a from AngebotEntity a where a.vorgangId = :vorgangId")
  List<AngebotEntity> findByVorgang(@Param("vorgangId") long vorgangId);
}
