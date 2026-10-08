package org.mwolff.fbcrm.rechnung.infrastructure;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring-Data-Zugriff auf {@code rechnung_nummernkreis}.
 *
 * <p>{@link LockModeType#PESSIMISTIC_WRITE} laesst Hibernate {@code SELECT … FOR UPDATE} erzeugen:
 * Die Jahreszeile bleibt bis zum Ende der Transaktion des Aufrufers gesperrt, damit die Nummern des
 * Zaehlerjahrs bei 1 beginnen und keine Luecke haben. Ohne Transaktion scheitert der Aufruf — genau
 * richtig, denn ein Zug ohne Transaktionsgrenze koennte die Zusage nicht halten.
 *
 * <p>Beide schreibenden Anweisungen sind nativ und tragen ihre Werte als gebundene Parameter; als
 * zusammengesetzter Text waere die Jahreszahl eine Einfallstelle (CLAUDE-security.md).
 */
interface SpringDataRechnungNummernkreisRepository
    extends JpaRepository<RechnungNummernkreisEntity, Integer> {

  /**
   * Legt die Jahreszeile mit dem Zaehlerstand 1 an, falls sie fehlt.
   *
   * <p>Als {@code INSERT … ON CONFLICT DO NOTHING} und nicht als „lesen, dann anlegen": Zwei
   * gleichzeitige erste Zuege desselben Zaehlerjahrs laesen beide „nicht da", und der zweite
   * scheiterte am Primaerschluessel.
   *
   * @param jahr das Zaehlerjahr
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          """
          INSERT INTO rechnung_nummernkreis (jahr, naechste_nummer) VALUES (:jahr, 1)
          ON CONFLICT (jahr) DO NOTHING
          """,
      nativeQuery = true)
  void legeJahrAn(@Param("jahr") int jahr);

  /**
   * Setzt den Zaehlerstand eines Zaehlerjahrs und legt die Zeile an, falls sie fehlt.
   *
   * @param jahr das Zaehlerjahr
   * @param naechsteNummer die naechste laufende Nummer, ab 1
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          """
          INSERT INTO rechnung_nummernkreis (jahr, naechste_nummer) VALUES (:jahr, :naechsteNummer)
          ON CONFLICT (jahr) DO UPDATE SET naechste_nummer = EXCLUDED.naechste_nummer
          """,
      nativeQuery = true)
  void setzeZaehler(@Param("jahr") int jahr, @Param("naechsteNummer") int naechsteNummer);

  /** Sperrt die Jahreszeile und liest sie. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select n from RechnungNummernkreisEntity n where n.jahr = :jahr")
  RechnungNummernkreisEntity sperreUndLies(@Param("jahr") int jahr);
}
