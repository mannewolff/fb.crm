package org.mwolff.fbcrm.rechnung.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring-Data-Zugriff auf {@code rechnung_nachgetragen}.
 *
 * <p>Jede Abfrage bindet ihre Werte als Parameter und setzt nichts zusammen (CLAUDE-security.md).
 * Die beiden Nummernfragen vergleichen ueber {@code lower(…)} wie der eindeutige Index {@code
 * rechnung_nachgetragen_nummer_lower_key}: „RE-9" und „re-9" sind dieselbe Nummer (#254, Kriterium
 * 4).
 */
interface SpringDataNachgetrageneRechnungRepository
    extends JpaRepository<NachgetrageneRechnungEntity, Long> {

  /**
   * Ob eine nachgetragene Rechnung diese Nummer schon traegt.
   *
   * @param nummer die zu pruefende Rechnungsnummer
   */
  @Query(
      "select count(r) > 0 from NachgetrageneRechnungEntity r"
          + " where lower(r.nummer) = lower(:nummer)")
  boolean existiertNummer(@Param("nummer") String nummer);

  /**
   * Dasselbe, ohne die Rechnung mit der Kennung {@code id}.
   *
   * @param nummer die zu pruefende Rechnungsnummer
   * @param id Kennung der Rechnung, die nicht mitzaehlt
   */
  @Query(
      "select count(r) > 0 from NachgetrageneRechnungEntity r"
          + " where lower(r.nummer) = lower(:nummer) and r.id <> :id")
  boolean existiertNummerAusser(@Param("nummer") String nummer, @Param("id") long id);
}
