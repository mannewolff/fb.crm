package org.mwolff.fbcrm.arbeitszeit.infrastructure;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring-Data-Zugriff auf {@code arbeitszeit}.
 *
 * <p>Vier Lesewege, jeder entlang einer Spur der Tabelle: ueber den Tag ({@code
 * arbeitszeit_tag_idx}) fuer die Monatsliste aller Personen, ueber Konto und Tag ({@code
 * arbeitszeit_account_tag_idx}, Plan #426, E9) fuer die Monatsliste einer Person und die
 * Ueberschneidungspruefung je Person, ueber die Position ({@code arbeitszeit_angebot_position_idx})
 * fuer die angefallenen Stunden, und Position und Tag zusammen fuer die Stunden eines Monats. Jede
 * Abfrage bindet ihre Werte als Parameter und setzt nichts zusammen (CLAUDE-security.md).
 *
 * <p>Ein {@code order by} steht in keiner: Die Reihenfolge der Ansicht setzt die Anwendungsschicht,
 * wie bei den Listen der Belege.
 */
interface SpringDataZeiteintragRepository extends JpaRepository<ZeiteintragEntity, Long> {

  /**
   * Die Eintraege eines Zeitraums ueber alle Positionen, beide Grenzen eingeschlossen.
   *
   * @param von erster Tag des Zeitraums
   * @param bis letzter Tag des Zeitraums
   */
  @Query(
      """
      select z from ZeiteintragEntity z
      where z.tag between :von and :bis
      """)
  List<ZeiteintragEntity> findImZeitraum(@Param("von") LocalDate von, @Param("bis") LocalDate bis);

  /**
   * Die Eintraege eines Kontos in einem Zeitraum, beide Grenzen eingeschlossen (Plan #426, E8).
   *
   * @param von erster Tag des Zeitraums
   * @param bis letzter Tag des Zeitraums
   * @param accountId Kennung des Kontos
   */
  @Query(
      """
      select z from ZeiteintragEntity z
      where z.accountId = :accountId and z.tag between :von and :bis
      """)
  List<ZeiteintragEntity> findImZeitraumFuerKonto(
      @Param("von") LocalDate von, @Param("bis") LocalDate bis, @Param("accountId") long accountId);

  /**
   * Alle Eintraege zu einer Menge von Positionen, ohne Beschraenkung des Zeitraums.
   *
   * <p>Die Summe entsteht nicht hier, sondern im Adapter aus den Minuten der Zeilen: Die Umrechnung
   * in Stunden soll an einer Stelle stehen (Plan #194, E5), und eine Zeitarithmetik in der Abfrage
   * waere eine zweite.
   *
   * @param angebotPositionIds die Kennungen der Positionen
   */
  @Query(
      """
      select z from ZeiteintragEntity z
      where z.angebotPositionId in :angebotPositionIds
      """)
  List<ZeiteintragEntity> findByPositionen(
      @Param("angebotPositionIds") Collection<Long> angebotPositionIds);

  /**
   * Dasselbe, begrenzt auf einen Zeitraum — der Monat der Rechnung.
   *
   * @param angebotPositionIds die Kennungen der Positionen
   * @param von erster Tag des Zeitraums
   * @param bis letzter Tag des Zeitraums
   */
  @Query(
      """
      select z from ZeiteintragEntity z
      where z.angebotPositionId in :angebotPositionIds and z.tag between :von and :bis
      """)
  List<ZeiteintragEntity> findByPositionenImZeitraum(
      @Param("angebotPositionIds") Collection<Long> angebotPositionIds,
      @Param("von") LocalDate von,
      @Param("bis") LocalDate bis);
}
