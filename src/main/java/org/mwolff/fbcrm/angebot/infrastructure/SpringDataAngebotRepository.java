package org.mwolff.fbcrm.angebot.infrastructure;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.mwolff.fbcrm.angebot.domain.Angebotszustand;
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
   * Die Kandidaten der Pipeline: Angebote in einem Zustand, deren Gueltigkeit {@code tag}
   * einschliesst — die Abfrage trifft {@code angebot_zustand_gueltigkeit_idx}.
   *
   * <p>Der Zustand kommt als Parameter und steht nicht als Literal in der Abfrage: Welcher Zustand
   * gemeint ist, ist eine Aussage in der Sprache der Domaene, und als gebundener Wert ist sie
   * typsicher statt als Text in einer Zeichenkette.
   *
   * <p>{@code gueltig_bis >= :tag} grenzt die Menge ein und entscheidet nicht: Der abgeleitete
   * Stand „abgelaufen" entsteht im Anwendungsfall (E4). Ohne {@code order by} — die Reihenfolge der
   * Pipeline ist eine Aussage der Ansicht.
   *
   * @param zustand der gesuchte gespeicherte Zustand
   * @param tag der Tag, den die Gueltigkeit einschliessen muss
   */
  @Query(
      """
      select a from AngebotEntity a
      where a.zustand = :zustand and a.gueltigBis >= :tag
      """)
  List<AngebotEntity> pipelinekandidaten(
      @Param("zustand") Angebotszustand zustand, @Param("tag") LocalDate tag);

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
