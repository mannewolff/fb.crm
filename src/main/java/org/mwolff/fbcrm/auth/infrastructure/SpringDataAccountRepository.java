package org.mwolff.fbcrm.auth.infrastructure;

import java.util.Optional;
import org.mwolff.fbcrm.auth.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring-Data-Zugriff auf {@code account}.
 *
 * <p>Die Suche ueber die Adresse ignoriert Gross- und Kleinschreibung und trifft damit denselben
 * Schluessel wie der eindeutige Index {@code account_email_key} auf {@code lower(email)}.
 */
interface SpringDataAccountRepository extends JpaRepository<AccountEntity, Long> {

  Optional<AccountEntity> findByEmailIgnoreCase(String email);

  boolean existsByRole(Role role);
}
