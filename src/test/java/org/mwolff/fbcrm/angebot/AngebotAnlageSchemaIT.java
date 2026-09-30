package org.mwolff.fbcrm.angebot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Prueft das Schema der Anlagen nach {@code V13__angebot_anlage.sql} gegen eine echte
 * PostgreSQL-Instanz.
 *
 * <p>Gegenstand sind die Zusagen, die allein die Datenbank haelt: eine Anlage hat eine Groesse
 * zwischen einem Byte und der Upload-Grenze, ihre Vorschauart ist leer oder eine der fuenf
 * bekannten, und sie haengt an einem vorhandenen Angebot. Gearbeitet wird mit {@link JdbcTemplate}
 * und direkten Anweisungen — der Weg ueber die Entities kaeme an einigen dieser Faelle gar nicht
 * vorbei.
 */
class AngebotAnlageSchemaIT extends AbstractIntegrationTest {

  private static final String INSERT_ANLAGE =
      "INSERT INTO angebot_anlage (angebot_id, datei_name, groesse, vorschau_art,"
          + " objekt_schluessel) VALUES (?, ?, ?, ?, ?)";

  private static final String FIRMA = "Adler AG";

  private static final String NAME = "Bericht.pdf";

  private static final String SCHLUESSEL = "angebot/1/anlage/3f2c";

  /** Die Grenze aus Kriterium 6 der Quelle #148 — 25 MiB, angeschrieben als 25 MB. */
  private static final long MAX_BYTE = 26_214_400L;

  private final JdbcTemplate jdbc;

  private long angebotId;

  @Autowired
  AngebotAnlageSchemaIT(final JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @BeforeEach
  void leereFachtabellenUndLegeEinAngebotAn() {
    jdbc.execute(
        "TRUNCATE angebot_anlage, angebot_kommentar, angebot_position, angebot, ansprechpartner,"
            + " firma RESTART IDENTITY CASCADE");
    jdbc.update("INSERT INTO firma (name) VALUES (?)", FIRMA);
    final long firmaId =
        jdbc.queryForObject("SELECT id FROM firma WHERE name = ?", Long.class, FIRMA).longValue();
    jdbc.update(
        "INSERT INTO angebot (firma_id, status, angebot_datum)"
            + " VALUES (?, 'ANGELEGT', DATE '2026-09-20')",
        firmaId);
    angebotId = jdbc.queryForObject("SELECT id FROM angebot LIMIT 1", Long.class).longValue();
  }

  private int anlage(final long groesse, final String vorschauArt) {
    return jdbc.update(INSERT_ANLAGE, angebotId, NAME, groesse, vorschauArt, SCHLUESSEL);
  }

  @Test
  void anlage_givenAValidRow_thenAcceptedWithTheCreationTimeSet() {
    // When
    final int betroffen = anlage(1024L, "PDF");

    // Then
    assertThat(betroffen).isEqualTo(1);
    final Integer ohneZeitpunkt =
        jdbc.queryForObject(
            "SELECT count(*) FROM angebot_anlage WHERE created_at IS NULL", Integer.class);
    assertThat(ohneZeitpunkt).isZero();
  }

  @Test
  void anlageGroesse_givenZero_thenRejectedByTheDatabase() {
    // When / Then — eine leere Datei ist keine Anlage (Kriterium 5).
    assertThatThrownBy(() -> anlage(0L, "PDF"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("angebot_anlage_groesse");
  }

  @Test
  void anlageGroesse_givenOneByteAboveTheLimit_thenRejectedByTheDatabase() {
    // When / Then
    assertThatThrownBy(() -> anlage(MAX_BYTE + 1L, "PDF"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("angebot_anlage_groesse");
  }

  @Test
  void anlageGroesse_givenExactlyTheLimit_thenAccepted() {
    // When
    final int betroffen = anlage(MAX_BYTE, "PDF");

    // Then
    assertThat(betroffen).isEqualTo(1);
  }

  @Test
  void anlageVorschauArt_givenAnUnknownKind_thenRejectedByTheDatabase() {
    // When / Then
    assertThatThrownBy(() -> anlage(1024L, "TIFF"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("angebot_anlage_vorschau_art");
  }

  @Test
  void anlageVorschauArt_givenNull_thenAccepted() {
    // When — eine Tabelle hat keine Vorschau und ist trotzdem eine Anlage.
    final int betroffen = anlage(1024L, null);

    // Then
    assertThat(betroffen).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"PNG", "JPEG", "GIF", "WEBP", "PDF"})
  void anlageVorschauArt_givenAKnownKind_thenAccepted(final String art) {
    // When
    final int betroffen = anlage(1024L, art);

    // Then
    assertThat(betroffen).isEqualTo(1);
  }

  @Test
  void anlageAngebotId_givenAnUnknownOffer_thenRejectedByTheDatabase() {
    // When / Then
    assertThatThrownBy(() -> jdbc.update(INSERT_ANLAGE, 4711L, NAME, 1024L, "PDF", SCHLUESSEL))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void migration_thenTheOfferIndexExistsAndThereIsNoUpdatedAtOrReportedType() {
    // When
    final Integer indizes =
        jdbc.queryForObject(
            "SELECT count(*) FROM pg_indexes WHERE schemaname = 'public'"
                + " AND indexname = 'angebot_anlage_angebot_idx'",
            Integer.class);
    final Integer ueberfluessig =
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public'"
                + " AND table_name = 'angebot_anlage'"
                + " AND column_name IN ('updated_at', 'content_type', 'gemeldete_art')",
            Integer.class);

    // Then — E2: eine Anlage aendert sich nie, und die vom Browser gemeldete Art wird nirgends
    // gelesen.
    assertThat(indizes).isEqualTo(1);
    assertThat(ueberfluessig).isZero();
  }

  @Test
  void anlage_givenTheOfferIsTruncated_thenTheAttachmentGoesWithIt() {
    // Given — die bestehenden Angebots-ITs leeren per TRUNCATE … CASCADE; der Fremdschluessel
    // ohne ON DELETE darf das nicht verhindern.
    anlage(1024L, "PDF");

    // When
    jdbc.execute("TRUNCATE angebot RESTART IDENTITY CASCADE");

    // Then
    final Integer verbleibend =
        jdbc.queryForObject("SELECT count(*) FROM angebot_anlage", Integer.class);
    assertThat(verbleibend).isZero();
  }
}
