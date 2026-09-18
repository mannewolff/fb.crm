package org.mwolff.fbcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Prueft die Flyway-Baseline gegen eine echte PostgreSQL-Instanz. */
class SchemaBaselineIT extends AbstractIntegrationTest {

  private static final String INSERT_ACCOUNT =
      "INSERT INTO account (email, display_name, password_hash, role) VALUES (?, ?, ?, ?)";

  private final JdbcTemplate jdbc;

  @Autowired
  SchemaBaselineIT(final JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @BeforeEach
  void leereFachtabellen() {
    jdbc.execute("TRUNCATE outbox_message, password_reset_token, account RESTART IDENTITY CASCADE");
  }

  @Test
  void flyway_thenBaselineTablesExist() {
    // When
    final Integer tabellen =
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'"
                + " AND table_name IN ('account', 'password_reset_token', 'outbox_message')",
            Integer.class);

    // Then
    assertThat(tabellen).isEqualTo(3);
  }

  @Test
  void ddlAutoValidate_thenContextStartsAgainstTheMigratedSchema() {
    // When
    final Integer migrationen =
        jdbc.queryForObject(
            "SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class);

    // Then
    assertThat(migrationen).isPositive();
  }

  @Test
  void accountSingleAdmin_givenSecondAdminWithDifferentEmail_thenRejectedByTheDatabase() {
    // Given
    jdbc.update(INSERT_ACCOUNT, "erste@example.org", "Erste", "hash", "ADMIN");

    // When / Then
    assertThatThrownBy(
            () -> jdbc.update(INSERT_ACCOUNT, "zweite@example.org", "Zweite", "hash", "ADMIN"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("account_single_admin");
  }

  @Test
  void accountSingleAdmin_givenSecondAccountWithRoleUser_thenAccepted() {
    // Given
    jdbc.update(INSERT_ACCOUNT, "erste@example.org", "Erste", "hash", "ADMIN");

    // When
    final int betroffen =
        jdbc.update(INSERT_ACCOUNT, "zweite@example.org", "Zweite", "hash", "USER");

    // Then
    assertThat(betroffen).isEqualTo(1);
  }

  @Test
  void accountEmail_givenSameAddressInDifferentCase_thenRejectedByTheDatabase() {
    // Given
    jdbc.update(INSERT_ACCOUNT, "manne@example.org", "Manne", "hash", "USER");

    // When / Then
    assertThatThrownBy(
            () -> jdbc.update(INSERT_ACCOUNT, "Manne@Example.ORG", "Manne", "hash", "USER"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void accountRole_givenUnknownRole_thenRejectedByTheDatabase() {
    // When / Then
    assertThatThrownBy(
            () -> jdbc.update(INSERT_ACCOUNT, "dritte@example.org", "Dritte", "hash", "ROOT"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void passwordResetToken_givenSameHashTwice_thenRejectedByTheDatabase() {
    // Given
    jdbc.update(INSERT_ACCOUNT, "manne@example.org", "Manne", "hash", "USER");
    final Long accountId =
        jdbc.queryForObject("SELECT id FROM account WHERE email = 'manne@example.org'", Long.class);
    jdbc.update(
        "INSERT INTO password_reset_token (account_id, token_hash, expires_at)"
            + " VALUES (?, ?, now() + interval '1 hour')",
        accountId,
        "derselbe-hash");

    // When / Then
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO password_reset_token (account_id, token_hash, expires_at)"
                        + " VALUES (?, ?, now() + interval '1 hour')",
                    accountId,
                    "derselbe-hash"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void outboxMessage_whenInserted_thenDefaultsToUnsentWithoutAttempts() {
    // Given
    jdbc.update(
        "INSERT INTO outbox_message (recipient, subject, body) VALUES (?, ?, ?)",
        "manne@example.org",
        "Willkommen",
        "Text");

    // When
    final Integer offen =
        jdbc.queryForObject(
            "SELECT count(*) FROM outbox_message WHERE sent_at IS NULL AND attempts = 0",
            Integer.class);

    // Then
    assertThat(offen).isEqualTo(1);
  }
}
