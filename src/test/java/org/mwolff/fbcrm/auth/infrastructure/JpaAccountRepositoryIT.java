package org.mwolff.fbcrm.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Der Konto-Adapter gegen eine echte PostgreSQL-Instanz. */
class JpaAccountRepositoryIT extends AbstractIntegrationTest {

  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");

  private final JpaAccountRepository repository;
  private final JdbcTemplate jdbc;

  @Autowired
  JpaAccountRepositoryIT(final JpaAccountRepository repository, final JdbcTemplate jdbc) {
    this.repository = repository;
    this.jdbc = jdbc;
  }

  private static Account neuesKonto(final String email) {
    return new Account(null, email, "Manne", "hash", Role.ADMIN, 0, ANGELEGT, ANGELEGT);
  }

  @BeforeEach
  void leereFachtabellen() {
    jdbc.execute("TRUNCATE outbox_message, password_reset_token, account RESTART IDENTITY CASCADE");
  }

  @Test
  void save_thenAssignsAnIdFromTheDatabase() {
    // When
    final Account gesichert = repository.save(neuesKonto("manne@example.org"));

    // Then
    assertThat(gesichert.id()).isNotNull();
  }

  @Test
  void findByEmail_afterSave_thenReturnsTheStoredAccount() {
    // Given
    final Account gesichert = repository.save(neuesKonto("manne@example.org"));

    // When
    final Optional<Account> gefunden = repository.findByEmail("manne@example.org");

    // Then
    assertThat(gefunden).contains(gesichert);
  }

  @Test
  void findByEmail_givenAnotherCase_thenStillFindsTheAccount() {
    // Given
    repository.save(neuesKonto("manne@example.org"));

    // When
    final Optional<Account> gefunden = repository.findByEmail("Manne@Example.ORG");

    // Then
    assertThat(gefunden).isPresent();
  }

  @Test
  void findById_afterSave_thenReturnsTheStoredAccount() {
    // Given
    final Account gesichert = repository.save(neuesKonto("manne@example.org"));

    // When
    final Optional<Account> gefunden = repository.findById(gesichert.requireId());

    // Then
    assertThat(gefunden).contains(gesichert);
  }

  @Test
  void findById_givenAnUnknownId_thenEmpty() {
    // When
    final Optional<Account> gefunden = repository.findById(4711L);

    // Then
    assertThat(gefunden).isEmpty();
  }

  @Test
  void existsAnyAdmin_givenAnEmptyTable_thenFalse() {
    // When
    final boolean vorhanden = repository.existsAnyAdmin();

    // Then
    assertThat(vorhanden).isFalse();
  }

  @Test
  void existsAnyAdmin_afterSavingAnAdmin_thenTrue() {
    // Given
    repository.save(neuesKonto("manne@example.org"));

    // When
    final boolean vorhanden = repository.existsAnyAdmin();

    // Then
    assertThat(vorhanden).isTrue();
  }

  @Test
  void save_givenASecondAccountWithTheSameEmail_thenRejectedByTheDatabase() {
    // Given
    repository.save(neuesKonto("manne@example.org"));

    // When / Then — es greift der eindeutige Index auf lower(email); weil ADMIN derzeit die
    // einzige Rolle ist, deckt sich sein Urteil mit dem des Einzel-Admin-Index.
    assertThatThrownBy(() -> repository.save(neuesKonto("manne@example.org")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void save_givenAChangedPassword_thenStoresTheHigherSessionGeneration() {
    // Given
    final Account gesichert = repository.save(neuesKonto("manne@example.org"));

    // When
    repository.save(gesichert.changePassword("neuer-hash", Instant.parse("2026-09-18T12:00:00Z")));

    // Then
    assertThat(repository.findById(gesichert.requireId()))
        .hasValueSatisfying(konto -> assertThat(konto.sessionGeneration()).isEqualTo(1));
  }
}
