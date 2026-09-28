package org.mwolff.fbcrm.auftrag.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring-Data-Zugriff auf {@code auftrag}.
 *
 * <p>Gelesen wird ein Auftrag ueber seine Kennung, ueber seinen Vorgang oder ueber das Angebot, aus
 * dem er entstand; geschrieben als Ganzes. Die Abfrage des Auftragsbestands kommt mit dem Paket,
 * das sie braucht — eine Abfrage ohne Leser haette keinen Test, der ihre Auswahl belegt.
 */
interface SpringDataAuftragRepository extends JpaRepository<AuftragEntity, Long> {

  /**
   * Die Auftraege eines Vorgangs — die Abfrage trifft {@code auftrag_vorgang_idx}.
   *
   * <p>Ohne {@code order by}: In welcher Reihenfolge die Ansicht sie zeigt, entscheidet Kriterium 9
   * und damit die Anwendungsschicht (E13).
   *
   * @param vorgangId Kennung des Vorgangs
   */
  @Query("select a from AuftragEntity a where a.vorgangId = :vorgangId")
  List<AuftragEntity> findByVorgang(@Param("vorgangId") long vorgangId);

  /**
   * Der Auftrag zu einem Angebot — die Abfrage trifft den eindeutigen Schluessel auf {@code
   * angebot_id}.
   *
   * <p>Ein {@link Optional} und keine Liste: {@code UNIQUE} auf der Spalte laesst hoechstens einen
   * zu (F9).
   *
   * @param angebotId Kennung des Angebots
   */
  @Query("select a from AuftragEntity a where a.angebotId = :angebotId")
  Optional<AuftragEntity> findByAngebot(@Param("angebotId") long angebotId);

  /**
   * Die Vorgaenge unter {@code vorgangIds}, an denen mindestens ein Auftrag haengt — die Abfrage
   * trifft {@code auftrag_vorgang_idx}.
   *
   * <p>Ohne Bedingung auf den Status: Ein Auftrag zaehlt ab dem Anlegen, und ein abgeschlossener
   * zaehlt weiter (F6). {@code distinct}, weil an einem Vorgang mehrere Auftraege haengen duerfen —
   * ein Rahmenauftrag mit Abrufen — und jede Kennung nur einmal gebraucht wird.
   *
   * @param vorgangIds die Kennungen der gefragten Vorgaenge; nie leer — der Aufrufer faengt den
   *     Fall ab, weil {@code in ()} kein gueltiges SQL ist
   */
  @Query(
      """
      select distinct a.vorgangId from AuftragEntity a
      where a.vorgangId in :vorgangIds
      """)
  List<Long> vorgaengeMitAuftrag(@Param("vorgangIds") Collection<Long> vorgangIds);
}
