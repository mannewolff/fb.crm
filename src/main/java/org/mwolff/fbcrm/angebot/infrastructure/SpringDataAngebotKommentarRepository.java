package org.mwolff.fbcrm.angebot.infrastructure;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring-Data-Zugriff auf {@code angebot_kommentar}.
 *
 * <p>Gelesen wird je Angebot — die Abfrage trifft damit {@code angebot_kommentar_angebot_idx}. Ein
 * {@code order by} steht bewusst nicht darin: Die Reihenfolge der Ansicht setzt die
 * Anwendungsschicht, wie bei den Angebotslisten (Plan #141, E8).
 *
 * <p>Die Kennungen gehen als gebundene Parameter in die Abfragen, wie in jeder Abfrage dieses
 * Moduls.
 */
interface SpringDataAngebotKommentarRepository extends JpaRepository<AngebotKommentarEntity, Long> {

  /**
   * Die nicht geloeschten Kommentare eines Angebots, in keiner zugesagten Reihenfolge (Plan #415,
   * E17).
   *
   * @param angebotId Kennung des Angebots
   */
  @Query(
      """
      select k from AngebotKommentarEntity k
      where k.angebotId = :angebotId
        and k.geloeschtAm is null
      """)
  List<AngebotKommentarEntity> findByAngebot(@Param("angebotId") long angebotId);

  /**
   * Gibt jeden Kommentar ohne Verfasser dem genannten Konto (Plan #414, E13).
   *
   * <p>Nativ und nicht als JPQL: Die Zeilen ohne Verfasser gibt es nur aus einem Bestand ohne
   * Konto, und die Entity kennt den Verfasser als Pflichtfeld. Die Anweisung schreibt nur Daten;
   * der {@code CHECK} der Spalte bleibt, wie {@code V31} ihn anlegt.
   *
   * @param kontoId Kennung des Kontos, das die Kommentare bekommt
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value = "UPDATE angebot_kommentar SET verfasser_id = :kontoId WHERE verfasser_id IS NULL",
      nativeQuery = true)
  void gibVerfasserloseAn(@Param("kontoId") long kontoId);
}
