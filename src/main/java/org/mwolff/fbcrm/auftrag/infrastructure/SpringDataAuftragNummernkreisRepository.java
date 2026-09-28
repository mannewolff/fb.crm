package org.mwolff.fbcrm.auftrag.infrastructure;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring-Data-Zugriff auf {@code auftrag_nummernkreis}.
 *
 * <p>{@link LockModeType#PESSIMISTIC_WRITE} laesst Hibernate {@code SELECT … FOR UPDATE} erzeugen:
 * Die Jahreszeile bleibt bis zum Ende der Transaktion des Aufrufers gesperrt, damit die Nummern des
 * Jahres bei 1 beginnen und ein zurueckgerollter Anlegeversuch seine Nummer wieder freigibt (E6).
 * Ohne Transaktion scheitert der Aufruf — genau richtig, denn ein Zug ohne Transaktionsgrenze
 * koennte die Zusage nicht halten.
 */
interface SpringDataAuftragNummernkreisRepository
    extends JpaRepository<AuftragNummernkreisEntity, Integer> {

  /**
   * Legt die Jahreszeile an, falls sie fehlt.
   *
   * <p>Als natives {@code INSERT … ON CONFLICT DO NOTHING} und nicht als „lesen, dann anlegen":
   * Zwei gleichzeitige erste Zuege desselben Jahres lasen beide „nicht da" und der zweite
   * scheiterte am Primaerschluessel. Der Zaehler beginnt bei 1 — die erste Nummer des Jahres ist
   * 001.
   *
   * @param jahr Kalenderjahr des Anlegens in {@code common.Geschaeftszone}
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          """
          INSERT INTO auftrag_nummernkreis (jahr, naechste) VALUES (:jahr, 1)
          ON CONFLICT (jahr) DO NOTHING
          """,
      nativeQuery = true)
  void legeJahrAn(@Param("jahr") int jahr);

  /** Sperrt die Jahreszeile und liest sie. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select n from AuftragNummernkreisEntity n where n.jahr = :jahr")
  AuftragNummernkreisEntity sperreUndLies(@Param("jahr") int jahr);
}
