package org.mwolff.fbcrm.auftrag.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.mwolff.fbcrm.auftrag.domain.Auftragsstatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring-Data-Zugriff auf {@code auftrag}.
 *
 * <p>Gelesen wird ein Auftrag ueber seine Kennung, ueber seinen Vorgang oder ueber das Angebot, aus
 * dem er entstand; geschrieben als Ganzes. Die Abfrage des Auftragsbestands steht hier, seit das
 * Paket sie liest — eine Abfrage ohne Leser haette keinen Test, der ihre Auswahl belegt.
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
   * Die Kandidaten des Auftragsbestands: alle Auftraege ausser den abgeschlossenen (Kriterium 12,
   * F7).
   *
   * <p>Der Status kommt als Parameter und steht nicht als Literal in der Abfrage — dieselbe
   * Abwaegung wie bei {@code SpringDataAngebotRepository.pipelinekandidaten}: Welcher Status
   * gemeint ist, ist eine Aussage in der Sprache der Domaene, und als gebundener Wert ist sie
   * typsicher statt als Text in einer Zeichenkette.
   *
   * <p><b>Ohne Bedingung auf den tragenden Vorgang.</b> Ob dieser offen ist, steht in einer Tabelle
   * eines anderen Moduls, und ein Verbund darauf waere ein zweiter Zugriffsweg auf fremdes Schema
   * neben dem Port des Vorgang-Moduls; die Auswahl trifft darum die Anwendungsschicht.
   *
   * <p>Ohne {@code order by} — die Reihenfolge des Bestands ist eine Aussage der Ansicht (E13).
   *
   * @param abgeschlossen der Status, der einen Auftrag aus dem Bestand nimmt
   */
  @Query("select a from AuftragEntity a where a.status <> :abgeschlossen")
  List<AuftragEntity> bestandskandidaten(@Param("abgeschlossen") Auftragsstatus abgeschlossen);

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
