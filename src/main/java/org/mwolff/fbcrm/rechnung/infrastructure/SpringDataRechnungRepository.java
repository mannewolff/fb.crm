package org.mwolff.fbcrm.rechnung.infrastructure;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring-Data-Zugriff auf {@code rechnung}.
 *
 * <p>Gelesen wird eine Rechnung ueber ihre Kennung oder ueber ihr Angebot, geschrieben als Ganzes.
 * Beide Abfragen binden ihre Werte als Parameter und setzen nichts zusammen (CLAUDE-security.md).
 */
interface SpringDataRechnungRepository extends JpaRepository<RechnungEntity, Long> {

  /**
   * Die Rechnungen eines Angebots — die Abfrage trifft {@code rechnung_angebot_idx}.
   *
   * <p>Ohne {@code order by}: In welcher Reihenfolge die Ansicht sie zeigt, entscheidet die
   * Anwendungsschicht.
   *
   * @param angebotId Kennung des Angebots
   */
  @Query("select r from RechnungEntity r where r.angebotId = :angebotId")
  List<RechnungEntity> findByAngebot(@Param("angebotId") long angebotId);

  /**
   * Ob eine Rechnung diese Nummer schon traegt (#160, Kriterium 18).
   *
   * <p>Als {@code count(…) > 0} und nicht als Laden der Zeile: Gebraucht wird die Antwort, nicht
   * die Rechnung.
   *
   * @param nummer die zu pruefende Rechnungsnummer
   */
  @Query("select count(r) > 0 from RechnungEntity r where r.nummer = :nummer")
  boolean existiertNummer(@Param("nummer") String nummer);
}
