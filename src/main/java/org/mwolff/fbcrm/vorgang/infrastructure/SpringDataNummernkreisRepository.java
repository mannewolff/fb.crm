package org.mwolff.fbcrm.vorgang.infrastructure;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/**
 * Spring-Data-Zugriff auf {@code vorgang_nummernkreis}.
 *
 * <p>{@link LockModeType#PESSIMISTIC_WRITE} laesst Hibernate {@code SELECT … FOR UPDATE} erzeugen:
 * Die eine Zaehlerzeile bleibt bis zum Ende der Transaktion des Aufrufers gesperrt, damit die
 * Nummern ab 1 und ohne Luecke bleiben (E3). Ohne Transaktion scheitert der Aufruf — genau richtig,
 * denn ein Zug ohne Transaktionsgrenze koennte die Zusage nicht halten.
 *
 * <p>Die Zeile wird ueber ihre Kennung 1 geholt und nicht als erste einer Menge: Dass es genau eine
 * gibt, sichert ein Check der Tabelle.
 */
interface SpringDataNummernkreisRepository extends JpaRepository<NummernkreisEntity, Short> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select n from NummernkreisEntity n where n.id = 1")
  NummernkreisEntity sperreUndLies();
}
