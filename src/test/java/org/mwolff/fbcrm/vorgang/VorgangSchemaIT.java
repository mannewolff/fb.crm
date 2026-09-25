package org.mwolff.fbcrm.vorgang;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Prueft die Migration {@code V3__vorgang_und_historie.sql} gegen eine echte PostgreSQL-Instanz.
 *
 * <p>In diesem Paket zeigt noch keine Entity auf das Schema — der Nachweis laeuft deshalb ueber
 * {@link JdbcTemplate} mit direkten Anweisungen. Ohne ihn bliebe die Migration eine Runde lang
 * unbelegt.
 */
class VorgangSchemaIT extends AbstractIntegrationTest {

  private static final String INSERT_VORGANG =
      "INSERT INTO vorgang (nummer, titel, firma_id) VALUES (?, ?, ?)";

  private static final String INSERT_EINTRAG =
      "INSERT INTO vorgang_eintrag"
          + " (vorgang_id, art, text, geschehen_am, herkunft, datei_name, datei_groesse,"
          + " objekt_schluessel)"
          + " VALUES (?, ?, ?, now(), 'VON_HAND', ?, ?, ?)";

  private final JdbcTemplate jdbc;

  @Autowired
  VorgangSchemaIT(final JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @BeforeEach
  void leereFachtabellen() {
    jdbc.execute(
        "TRUNCATE vorgang_eintrag, vorgang, ansprechpartner, firma RESTART IDENTITY CASCADE");
  }

  private Long firmaId() {
    jdbc.update("INSERT INTO firma (name) VALUES (?)", "Adler AG");
    return jdbc.queryForObject("SELECT id FROM firma WHERE name = 'Adler AG'", Long.class);
  }

  private Long vorgangId(final long nummer) {
    jdbc.update(INSERT_VORGANG, nummer, "Website-Relaunch", firmaId());
    return jdbc.queryForObject(
        "SELECT id FROM vorgang WHERE nummer = ?", Long.class, Long.valueOf(nummer));
  }

  @Test
  void migration_thenTheThreeTablesExist() {
    // When
    final Integer tabellen =
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'"
                + " AND table_name IN ('vorgang', 'vorgang_eintrag', 'vorgang_nummernkreis')",
            Integer.class);

    // Then
    assertThat(tabellen).isEqualTo(3);
  }

  @Test
  void vorgangNummer_givenTheSameNumberTwice_thenRejectedByTheDatabase() {
    // Given
    final Long firmaId = firmaId();
    jdbc.update(INSERT_VORGANG, 1L, "Erster", firmaId);

    // When / Then
    assertThatThrownBy(() -> jdbc.update(INSERT_VORGANG, 1L, "Zweiter", firmaId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void migration_thenTheThreeIndexesExist() {
    // When
    final Integer indizes =
        jdbc.queryForObject(
            "SELECT count(*) FROM pg_indexes WHERE schemaname = 'public'"
                + " AND indexname IN ('vorgang_titel_idx', 'vorgang_firma_idx',"
                + " 'vorgang_eintrag_verlauf_idx')",
            Integer.class);

    // Then
    assertThat(indizes).isEqualTo(3);
  }

  @Test
  void nummernkreis_thenHoldsExactlyOneRowStartingAtOne() {
    // When
    final Integer zeilen =
        jdbc.queryForObject(
            "SELECT count(*) FROM vorgang_nummernkreis WHERE naechste = 1", Integer.class);

    // Then
    assertThat(zeilen).isEqualTo(1);
  }

  @Test
  void nummernkreis_givenASecondRow_thenRejectedByTheDatabase() {
    // When / Then
    assertThatThrownBy(
            () -> jdbc.update("INSERT INTO vorgang_nummernkreis (id, naechste) VALUES (2, 1)"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void vorgangTitel_givenOnlyWhitespace_thenRejectedByTheDatabase() {
    // Given
    final Long firmaId = firmaId();

    // When / Then
    assertThatThrownBy(() -> jdbc.update(INSERT_VORGANG, 1L, "   ", firmaId))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("vorgang_titel_nicht_leer");
  }

  @Test
  void vorgangFirmaId_givenAnUnknownFirma_thenRejectedByTheDatabase() {
    // When / Then
    assertThatThrownBy(() -> jdbc.update(INSERT_VORGANG, 1L, "Website-Relaunch", 4711L))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void vorgang_whenInsertedWithoutTheColumn_thenIsOpen() {
    // Given
    final Long vorgangId = vorgangId(1L);

    // When
    final Boolean abgeschlossen =
        jdbc.queryForObject(
            "SELECT abgeschlossen FROM vorgang WHERE id = ?", Boolean.class, vorgangId);

    // Then
    assertThat(abgeschlossen).isFalse();
  }

  @Test
  void kommentar_givenTextAndNoFileData_thenAccepted() {
    // Given
    final Long vorgangId = vorgangId(1L);

    // When
    final int betroffen =
        jdbc.update(INSERT_EINTRAG, vorgangId, "KOMMENTAR", "Angerufen", null, null, null);

    // Then
    assertThat(betroffen).isEqualTo(1);
  }

  @Test
  void kommentar_givenNoText_thenRejectedByTheDatabase() {
    // Given
    final Long vorgangId = vorgangId(1L);

    // When / Then
    assertThatThrownBy(
            () -> jdbc.update(INSERT_EINTRAG, vorgangId, "KOMMENTAR", null, null, null, null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("vorgang_eintrag_kommentar");
  }

  @Test
  void kommentar_givenFileData_thenRejectedByTheDatabase() {
    // Given
    final Long vorgangId = vorgangId(1L);

    // When / Then
    assertThatThrownBy(
            () ->
                jdbc.update(
                    INSERT_EINTRAG,
                    vorgangId,
                    "KOMMENTAR",
                    "Angerufen",
                    "Angebot.pdf",
                    4096L,
                    "vorgang/1/abc"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("vorgang_eintrag_kommentar");
  }

  @Test
  void anhang_givenAllThreeFileValues_thenAccepted() {
    // Given
    final Long vorgangId = vorgangId(1L);

    // When
    final int betroffen =
        jdbc.update(
            INSERT_EINTRAG, vorgangId, "ANHANG", null, "Angebot.pdf", 4096L, "vorgang/1/abc");

    // Then
    assertThat(betroffen).isEqualTo(1);
  }

  @Test
  void anhang_givenNoObjectKey_thenRejectedByTheDatabase() {
    // Given
    final Long vorgangId = vorgangId(1L);

    // When / Then
    assertThatThrownBy(
            () ->
                jdbc.update(INSERT_EINTRAG, vorgangId, "ANHANG", null, "Angebot.pdf", 4096L, null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("vorgang_eintrag_anhang");
  }

  @Test
  void eintragArt_givenAnUnknownArt_thenRejectedByTheDatabase() {
    // Given
    final Long vorgangId = vorgangId(1L);

    // When / Then
    assertThatThrownBy(
            () -> jdbc.update(INSERT_EINTRAG, vorgangId, "NACHRICHT", "Text", null, null, null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void eintragVorgangId_givenAnUnknownVorgang_thenRejectedByTheDatabase() {
    // When / Then
    assertThatThrownBy(
            () -> jdbc.update(INSERT_EINTRAG, 4711L, "KOMMENTAR", "Angerufen", null, null, null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
