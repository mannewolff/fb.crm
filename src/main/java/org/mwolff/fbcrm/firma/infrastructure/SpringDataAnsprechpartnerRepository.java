package org.mwolff.fbcrm.firma.infrastructure;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring-Data-Zugriff auf {@code ansprechpartner}.
 *
 * <p>Beide Abfragen laufen entlang {@code firma_id} und treffen damit den Index {@code
 * ansprechpartner_firma_idx}. Die Zaehlung nimmt alle Firmen der Uebersicht auf einmal entgegen,
 * statt je Zeile einmal zu fragen.
 */
interface SpringDataAnsprechpartnerRepository extends JpaRepository<AnsprechpartnerEntity, Long> {

  @Query(
      """
      select a from AnsprechpartnerEntity a
      where a.firmaId = :firmaId
      order by lower(a.nachname), a.id
      """)
  List<AnsprechpartnerEntity> findByFirma(@Param("firmaId") long firmaId);

  @Query(
      """
      select a.firmaId as firmaId, count(a) as anzahl from AnsprechpartnerEntity a
      where a.aktiv = true and a.firmaId in :firmaIds
      group by a.firmaId
      """)
  List<AktiveJeFirma> zaehleAktiveJeFirma(@Param("firmaIds") Collection<Long> firmaIds);

  /** Eine Zeile der Zaehlung: eine Firma und die Zahl ihrer aktiven Ansprechpartner. */
  interface AktiveJeFirma {

    /** Kennung der Firma. */
    long getFirmaId();

    /** Zahl ihrer aktiven Ansprechpartner. */
    long getAnzahl();
  }
}
