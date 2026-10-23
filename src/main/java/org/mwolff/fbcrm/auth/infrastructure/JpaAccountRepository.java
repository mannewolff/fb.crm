package org.mwolff.fbcrm.auth.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.Role;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
  public boolean existsAnyAccount() {
    return jpa.existsBy();
  }

  @Override
  public List<Account> findAllOrderedByDisplayName() {
    return jpa.findAllByOrderByDisplayNameAscIdAsc().stream()
        .map(JpaAccountRepository::toDomain)
        .toList();
  }

  @Override
  public Map<Long, String> displayNamesByIds(final Collection<Long> ids) {
    return jpa.findAllById(ids).stream()
        .map(JpaAccountRepository::toDomain)
        .collect(Collectors.toMap(Account::requireId, Account::displayName));
  }

  /**
   * {@inheritDoc}
   *
   * <p>{@link Propagation#MANDATORY}: Eine Sperre, die mit dem Aufruf selbst endete, hielte nichts
   * fest — ohne Transaktion des Aufrufers scheitert der Aufruf, statt still nichts zu sperren.
   */
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public List<Account> aktiveCLevelMitSperre() {
    return jpa.sperreAktiveMitRolle(Role.C_LEVEL).stream()
        .map(JpaAccountRepository::toDomain)
        .toList();
  }

  /**
   * {@inheritDoc}
   *
   * <p>{@code saveAndFlush} statt {@code save}: Eine fortgeschriebene Zeile schriebe Hibernate
   * sonst erst beim Commit, und eine Verletzung von {@code account_email_key} kaeme hinter dem
   * Anwendungsfall an, der sie in seine Abweisung uebersetzen soll (Issue #421).
   */
  @Override
  public Account save(final Account account) {
    return toDomain(jpa.saveAndFlush(AccountEntity.aus(account)));
  }

  private static Account toDomain(final AccountEntity zeile) {
    return new Account(
        zeile.getId(),
        zeile.getEmail(),
        zeile.getDisplayName(),
        zeile.getRole(),
        zeile.isAktiv(),
        zeile.getSessionGeneration(),
        zeile.getCreatedAt(),
        zeile.getUpdatedAt());
  }
}
