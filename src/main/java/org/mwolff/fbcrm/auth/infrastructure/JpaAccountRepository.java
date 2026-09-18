package org.mwolff.fbcrm.auth.infrastructure;

import java.util.Optional;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.Role;
import org.springframework.stereotype.Repository;

/** Setzt den Port {@link AccountRepository} auf JPA um und uebersetzt in beide Richtungen. */
@Repository
class JpaAccountRepository implements AccountRepository {

  private final SpringDataAccountRepository jpa;

  JpaAccountRepository(final SpringDataAccountRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Optional<Account> findById(final long id) {
    return jpa.findById(id).map(JpaAccountRepository::toDomain);
  }

  @Override
  public Optional<Account> findByEmail(final String email) {
    return jpa.findByEmailIgnoreCase(email).map(JpaAccountRepository::toDomain);
  }

  @Override
  public boolean existsAnyAdmin() {
    return jpa.existsByRole(Role.ADMIN);
  }

  @Override
  public Account save(final Account account) {
    return toDomain(jpa.save(toEntity(account)));
  }

  private static Account toDomain(final AccountEntity zeile) {
    return new Account(
        zeile.getId(),
        zeile.getEmail(),
        zeile.getDisplayName(),
        zeile.getPasswordHash(),
        zeile.getRole(),
        zeile.getSessionGeneration(),
        zeile.getCreatedAt(),
        zeile.getUpdatedAt());
  }

  private static AccountEntity toEntity(final Account account) {
    return new AccountEntity(
        account.id(),
        account.email(),
        account.displayName(),
        account.passwordHash(),
        account.role(),
        account.sessionGeneration(),
        account.createdAt(),
        account.updatedAt());
  }
}
