package org.mwolff.fbcrm.auth.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.mwolff.fbcrm.auth.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring-Data-Zugriff auf {@code account}.
 *
 * <p>Die Suche ueber die Adresse ignoriert Gross- und Kleinschreibung und trifft damit denselben
 * Schluessel wie der eindeutige Index {@code account_email_key} auf {@code lower(email)}.
 *
 * <p>{@link LockModeType#PESSIMISTIC_WRITE} laesst Hibernate {@code SELECT … FOR UPDATE} erzeugen,
 * wie {@code SpringDataRechnungNummernkreisRepository} es fuer die Jahreszeile haelt. Gesperrt wird
 * eine Entity-Abfrage, keine Zaehlung: {@code FOR UPDATE} ist in Postgres mit Aggregatfunktionen
 * nicht erlaubt (Plan #414, A6). Die Sortierung nach Id legt die Reihenfolge fest, in der zwei
 * Transaktionen die Zeilen sperren, und vermeidet so, dass sie sich gegenseitig blockieren.
 */
interface SpringDataAccountRepository extends JpaRepository<AccountEntity, Long> {

  Optional<AccountEntity> findByEmailIgnoreCase(String email);

  boolean existsBy();

  List<AccountEntity> findAllByOrderByDisplayNameAscIdAsc();

  /** Sperrt die aktiven Konten einer Rolle und liest sie. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from AccountEntity a where a.role = :role and a.aktiv = true order by a.id")
  List<AccountEntity> sperreAktiveMitRolle(@Param("role") Role role);

  /** Sperrt ein Konto und liest es (Plan #415, E19). */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from AccountEntity a where a.id = :id")
  Optional<AccountEntity> sperreKonto(@Param("id") long id);
}
