package org.mwolff.fbcrm.angebot.infrastructure;

import java.util.Collection;
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

  /**
   * Die Vorgaenge unter {@code vorgangIds}, an denen mindestens ein festgeschriebenes Angebot
   * haengt — die Abfrage trifft {@code angebot_vorgang_idx}.
   *
   * <p>{@code nummer is not null} ist die Auswahl und nicht {@code zustand = 'VERSENDET'}: Ein
   * angenommenes, abgelehntes oder abgeloestes Angebot traegt seine Nummer weiter, und die Phase
   * des Vorgangs bleibt darum „Angebot" (F6). {@code distinct}, weil ein Vorgang mehrere Angebote
   * haben darf und jede Kennung nur einmal gebraucht wird.
   *
   * @param vorgangIds die Kennungen der gefragten Vorgaenge; nie leer — der Aufrufer faengt den
   *     Fall ab, weil {@code in ()} kein gueltiges SQL ist
   */
  @Query(
      """
      select distinct a.vorgangId from AngebotEntity a
      where a.vorgangId in :vorgangIds and a.nummer is not null
      """)
  List<Long> vorgaengeMitFestgeschriebenemAngebot(@Param("vorgangIds") Collection<Long> vorgangIds);
}
