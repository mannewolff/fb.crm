package org.mwolff.fbcrm.rechnung.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring-Data-Zugriff auf {@code rechnung}.
 *
 * <p>Gelesen wird eine Rechnung ueber ihre Kennung oder ueber ihr Angebot, geschrieben als Ganzes.
 * Jede Abfrage bindet ihre Werte als Parameter und setzt nichts zusammen (CLAUDE-security.md).
 *
 * <p><b>Jede Liste und die Nummernpruefung lassen gekennzeichnete Zeilen aus</b> (Plan #415, E17):
 * Ein geloeschter Entwurf ist fuer jeden Leseweg fort. Nur der Zugriff ueber die Kennung — {@code
 * findById} und {@link #sperreUndLies(long)} — liefert ihn mit, damit der Anwendungsfall Urheber
 * und Zeitpunkt der Loeschung nennen kann.
 *
 * <p>{@link #sperreUndLies(long)} steht neben {@code findById} und ersetzt es nicht: Jedes Lesen zu
 * sperren machte aus jeder Ansicht einen Engpass. Gesperrt wird nur fuer das Stellen.
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
  @Query("select r from RechnungEntity r where r.angebotId = :angebotId and r.geloeschtAm is null")
  List<RechnungEntity> findByAngebot(@Param("angebotId") long angebotId);

  /**
   * Alle Rechnungen ohne die geloeschten.
   *
   * <p>Steht statt {@code findAll}, das auch die gekennzeichneten Zeilen liefert.
   */
  @Query("select r from RechnungEntity r where r.geloeschtAm is null")
  List<RechnungEntity> findLebende();

  /**
   * Ob eine Rechnung diese Nummer schon traegt (#160, Kriterium 18).
   *
   * <p>Als {@code count(…) > 0} und nicht als Laden der Zeile: Gebraucht wird die Antwort, nicht
   * die Rechnung. Verglichen wird ueber {@code lower(…)} wie im eindeutigen Index {@code
   * rechnung_nummer_lower_key}: „RE-1" und „re-1" sind dieselbe Nummer (#254, Kriterium 4). Eine
   * geloeschte Rechnung haelt ihre Nummer nicht fest — wie der Index seit V35.
   *
   * @param nummer die zu pruefende Rechnungsnummer
   */
  @Query(
      """
      select count(r) > 0 from RechnungEntity r
      where lower(r.nummer) = lower(:nummer) and r.geloeschtAm is null
      """)
  boolean existiertNummer(@Param("nummer") String nummer);

  /**
   * Sperrt die Zeile der Rechnung und liest sie.
   *
   * <p>{@link LockModeType#PESSIMISTIC_WRITE} laesst Hibernate {@code SELECT … FOR UPDATE}
   * erzeugen: Die Zeile bleibt bis zum Ende der Transaktion des Aufrufers gesperrt. Dieselbe
   * Schreibweise wie am Nummernkreis ({@code SpringDataRechnungNummernkreisRepository}).
   *
   * @param id Kennung der Rechnung
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from RechnungEntity r where r.id = :id")
  Optional<RechnungEntity> sperreUndLies(@Param("id") long id);
}
