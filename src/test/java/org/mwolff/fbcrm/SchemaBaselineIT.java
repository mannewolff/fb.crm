package org.mwolff.fbcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Prueft die Flyway-Baseline gegen eine echte PostgreSQL-Instanz. */
class SchemaBaselineIT extends AbstractIntegrationTest {

  private static final String INSERT_ACCOUNT =
      "INSERT INTO account (email, display_name, role) VALUES (?, ?, ?)";

  private static final String INSERT_LOGIN_CODE =
      "INSERT INTO login_code (email, code_hash, expires_at)"
          + " VALUES (?, ?, now() + interval '10 minutes')";

  private static final String INSERT_FIRMA = "INSERT INTO firma (name) VALUES (?)";

  private static final String INSERT_ANSPRECHPARTNER =
      "INSERT INTO ansprechpartner (firma_id, nachname) VALUES (?, ?)";

  private static final String INSERT_KOMMENTAR =
      "INSERT INTO angebot_kommentar (angebot_id, verfasser_id, text) VALUES (?, ?, ?)";

  private final JdbcTemplate jdbc;

  @Autowired
  SchemaBaselineIT(final JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @BeforeEach
  void leereFachtabellen() {
    jdbc.execute(
        "TRUNCATE outbox_message, login_code, account, ansprechpartner,"
            + " firma RESTART IDENTITY CASCADE");
  }

  private Long firmaId(final String name) {
    jdbc.update(INSERT_FIRMA, name);
    return jdbc.queryForObject("SELECT id FROM firma WHERE name = ?", Long.class, name);
  }

  @Test
  void flyway_thenBaselineTablesExist() {
    // When
    final Integer tabellen =
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'"
                + " AND table_name IN ('account', 'outbox_message', 'firma', 'ansprechpartner',"
                + " 'login_code')",
            Integer.class);

    // Then
    assertThat(tabellen).isEqualTo(5);
  }

  @Test
  void flyway_thenThePasswordResetTableIsGone() {
    // When — V28 baut die Tabelle des entfallenen Passwort-Resets ab (Issue #404).
    final Integer tabellen =
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'"
                + " AND table_name = 'password_reset_token'",
            Integer.class);

    // Then
    assertThat(tabellen).isZero();
  }

  @Test
  void flyway_thenTheRemovedOrderTablesAreGone() {
    // When — V9 baut die Tabellen des entfallenen Auftrags ab (Issue #125).
    final Integer tabellen =
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'"
                + " AND table_name IN ('auftrag', 'auftrag_position', 'auftrag_nummernkreis')",
            Integer.class);

    // Then
    assertThat(tabellen).isZero();
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
  void accountRole_givenASecondCLevelWithDifferentEmail_thenAccepted() {
    // Given — E12: der Index „genau ein Admin" ist mit V30 entfallen, mehrere C-Level sind erlaubt.
    jdbc.update(INSERT_ACCOUNT, "erste@example.org", "Erste", "C_LEVEL");

    // When
    final int betroffen = jdbc.update(INSERT_ACCOUNT, "zweite@example.org", "Zweite", "C_LEVEL");

    // Then
    assertThat(betroffen).isEqualTo(1);
  }

  @Test
  void accountRole_givenEachOfTheThreeRoles_thenAccepted() {
    // When
    final int betroffen =
        jdbc.update(INSERT_ACCOUNT, "chefin@example.org", "Chefin", "C_LEVEL")
            + jdbc.update(INSERT_ACCOUNT, "anna@example.org", "Anna", "EMPLOYEE")
            + jdbc.update(INSERT_ACCOUNT, "leser@example.org", "Leser", "READ_ONLY");

    // Then
    assertThat(betroffen).isEqualTo(3);
  }

  @Test
  void accountEmail_givenSameAddressInDifferentCase_thenRejectedByTheDatabase() {
    // Given
    jdbc.update(INSERT_ACCOUNT, "manne@example.org", "Manne", "C_LEVEL");

    // When / Then
    assertThatThrownBy(() -> jdbc.update(INSERT_ACCOUNT, "Manne@Example.ORG", "Manne", "C_LEVEL"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("account_email_key");
  }

  @Test
  void accountRole_givenUnknownRole_thenRejectedByTheDatabase() {
    // When / Then
    assertThatThrownBy(() -> jdbc.update(INSERT_ACCOUNT, "dritte@example.org", "Dritte", "ROOT"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("account_role_known");
  }

  @ParameterizedTest
  @ValueSource(strings = {"ADMIN", "USER"})
  void accountRole_givenAFormerRoleName_thenRejectedByTheDatabase(final String alterName) {
    // When / Then — A4: ein CHECK, der die alten Namen noch zuliesse, liesse sie wieder entstehen.
    assertThatThrownBy(() -> jdbc.update(INSERT_ACCOUNT, "alt@example.org", "Alt", alterName))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("account_role_known");
  }

  @Test
  void accountAktiv_givenNoValue_thenANewAccountIsActive() {
    // Given
    jdbc.update(INSERT_ACCOUNT, "manne@example.org", "Manne", "C_LEVEL");

    // When
    final Boolean aktiv =
        jdbc.queryForObject(
            "SELECT aktiv FROM account WHERE email = ?", Boolean.class, "manne@example.org");

    // Then
    assertThat(aktiv).isTrue();
  }

  @Test
  void accountAktiv_givenNull_thenRejectedByTheDatabase() {
    // When / Then
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO account (email, display_name, role, aktiv) VALUES (?, ?, ?, NULL)",
                    "manne@example.org",
                    "Manne",
                    "C_LEVEL"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void loginCode_givenSameAddressInDifferentCase_thenRejectedByTheDatabase() {
    // Given
    jdbc.update(INSERT_LOGIN_CODE, "manne@example.org", "hash");

    // When / Then — hoechstens ein Code je Adresse (E1).
    assertThatThrownBy(() -> jdbc.update(INSERT_LOGIN_CODE, "Manne@Example.ORG", "anderer-hash"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("login_code_email_key");
  }

  @Test
  void loginCode_whenInsertedWithoutAccount_thenDefaultsToNoFailedAttempts() {
    // Given — kein Konto: bei der Einrichtung gibt es noch keines (E1).
    jdbc.update(INSERT_LOGIN_CODE, "neu@example.org", "hash");

    // When
    final Integer frische =
        jdbc.queryForObject(
            "SELECT count(*) FROM login_code WHERE failed_attempts = 0", Integer.class);

    // Then
    assertThat(frische).isEqualTo(1);
  }

  @Test
  void loginCode_givenNegativeFailedAttempts_thenRejectedByTheDatabase() {
    // When / Then
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO login_code (email, code_hash, expires_at, failed_attempts)"
                        + " VALUES (?, ?, now() + interval '10 minutes', -1)",
                    "manne@example.org",
                    "hash"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("login_code_failed_attempts_nonnegative");
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

  @Test
  void firmaName_givenOnlyWhitespace_thenRejectedByTheDatabase() {
    // When / Then
    assertThatThrownBy(() -> jdbc.update(INSERT_FIRMA, "   "))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("firma_name_nicht_leer");
  }

  @Test
  void ansprechpartnerNachname_givenOnlyWhitespace_thenRejectedByTheDatabase() {
    // Given
    final Long firmaId = firmaId("Adler AG");

    // When / Then
    assertThatThrownBy(() -> jdbc.update(INSERT_ANSPRECHPARTNER, firmaId, "   "))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ansprechpartner_nachname_nicht_leer");
  }

  @Test
  void ansprechpartnerFirmaId_givenAnUnknownFirma_thenRejectedByTheDatabase() {
    // When / Then
    assertThatThrownBy(() -> jdbc.update(INSERT_ANSPRECHPARTNER, 4711L, "Mustermann"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void aktiv_whenInsertedWithoutTheColumn_thenDefaultsToTrue() {
    // Given
    final Long firmaId = firmaId("Adler AG");
    jdbc.update(INSERT_ANSPRECHPARTNER, firmaId, "Mustermann");

    // When
    final Integer aktive =
        jdbc.queryForObject(
            "SELECT count(*) FROM firma f JOIN ansprechpartner a ON a.firma_id = f.id"
                + " WHERE f.aktiv AND a.aktiv",
            Integer.class);

    // Then
    assertThat(aktive).isEqualTo(1);
  }

  private Long angebotId() {
    final Long firmaId = firmaId("Adler AG");
    return jdbc.queryForObject(
        "INSERT INTO angebot (firma_id, status, angebot_datum)"
            + " VALUES (?, 'ANGELEGT', DATE '2026-10-23') RETURNING id",
        Long.class,
        firmaId);
  }

  @Test
  void kommentarVerfasser_givenAnUnknownAccount_thenRejectedByTheDatabase() {
    // Given — Plan #414, A7: der Verfasser ist ein Konto, das es gibt.
    final Long angebotId = angebotId();

    // When / Then
    assertThatThrownBy(() -> jdbc.update(INSERT_KOMMENTAR, angebotId, 4711L, "Kunde ruft an."))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("angebot_kommentar_verfasser_id_fkey");
  }

  @Test
  void kommentarVerfasser_givenNone_thenRejectedByTheDatabase() {
    // Given — E13: der CHECK ist NOT VALID, bindet aber jeden neuen Kommentar.
    final Long angebotId = angebotId();

    // When / Then
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO angebot_kommentar (angebot_id, text) VALUES (?, ?)",
                    angebotId,
                    "Kunde ruft an."))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("check constraint \"angebot_kommentar_verfasser\"");
  }

  @Test
  void kommentarVerfasser_givenAnExistingAccount_thenAccepted() {
    // Given
    final Long angebotId = angebotId();
    final Long kontoId =
        jdbc.queryForObject(
            INSERT_ACCOUNT + " RETURNING id", Long.class, "manne@example.org", "Manne", "C_LEVEL");

    // When
    final int betroffen = jdbc.update(INSERT_KOMMENTAR, angebotId, kontoId, "Kunde ruft an.");

    // Then
    assertThat(betroffen).isEqualTo(1);
  }
}
