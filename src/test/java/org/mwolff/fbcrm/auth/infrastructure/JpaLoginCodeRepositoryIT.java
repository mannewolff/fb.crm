package org.mwolff.fbcrm.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.auth.domain.LoginCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Der Adapter des Einmalcodes gegen eine echte PostgreSQL-Instanz.
 *
 * <p>Geprueft wird, was der gemockte {@code JpaLoginCodeRepositoryTest} nicht sehen kann: dass
 * Suche und Loeschen die Adresse wie der Index {@code login_code_email_key} ohne Ruecksicht auf
 * Gross- und Kleinschreibung treffen, und dass das Loeschen ohne umgebende Transaktion gelingt.
 */
class JpaLoginCodeRepositoryIT extends AbstractIntegrationTest {

  private static final Instant AUSGESTELLT = Instant.parse("2026-10-20T10:00:00Z");

  private final JpaLoginCodeRepository repository;
  private final JdbcTemplate jdbc;

  @Autowired
  JpaLoginCodeRepositoryIT(final JpaLoginCodeRepository repository, final JdbcTemplate jdbc) {
    this.repository = repository;
    this.jdbc = jdbc;
  }

  private static LoginCode neuerCode(final String email) {
    return LoginCode.ausgestellt(email, "hash", AUSGESTELLT, Duration.ofMinutes(10));
  }

  @BeforeEach
  void leereTabelle() {
    jdbc.execute("TRUNCATE login_code RESTART IDENTITY");
  }

  @Test
  void findByEmail_givenTheAddressInAnotherCase_thenReturnsTheStoredCode() {
    // Given
    final LoginCode gesichert = repository.save(neuerCode("manne@example.org"));

    // When / Then
    assertThat(repository.findByEmail("Manne@Example.ORG")).contains(gesichert);
  }

  @Test
  void save_givenAnExistingCodeWithAFailedAttempt_thenUpdatesTheRow() {
    // Given
    final LoginCode gesichert = repository.save(neuerCode("manne@example.org"));

    // When
    repository.save(gesichert.mitFehlversuch());

    // Then
    assertThat(repository.findByEmail("manne@example.org"))
        .hasValueSatisfying(code -> assertThat(code.failedAttempts()).isEqualTo(1));
  }

  @Test
  void deleteByEmail_givenTheAddressInAnotherCase_thenRemovesTheCode() {
    // Given
    repository.save(neuerCode("manne@example.org"));

    // When
    repository.deleteByEmail("MANNE@example.org");

    // Then
    assertThat(repository.findByEmail("manne@example.org")).isEmpty();
  }

  @Test
  void deleteByEmail_thenLeavesTheCodesOfOtherAddresses() {
    // Given
    repository.save(neuerCode("manne@example.org"));
    final LoginCode fremd = repository.save(neuerCode("fremd@example.org"));

    // When
    repository.deleteByEmail("manne@example.org");

    // Then
    assertThat(repository.findByEmail("fremd@example.org")).contains(fremd);
  }
}
