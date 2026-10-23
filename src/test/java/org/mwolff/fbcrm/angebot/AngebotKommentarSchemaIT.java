package org.mwolff.fbcrm.angebot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.Schutzspalten;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Prueft das Schema der Kommentare nach {@code V12__angebot_kommentar.sql} gegen eine echte
 * PostgreSQL-Instanz.
 *
 * <p>Gegenstand sind die Zusagen, die allein die Datenbank haelt: der Text ist nie leer und nie
 * laenger als 2.000 Zeichen, und ein Kommentar haengt an einem vorhandenen Angebot. Seit {@code
 * V31} traegt jeder Kommentar einen Verfasser; die Faelle dazu stehen in {@code SchemaBaselineIT}
 * und {@code MehrereKontenMigrationIT}, hier haengt jeder Kommentar am Konto aus dem Vorlauf.
 * Gearbeitet wird mit {@link JdbcTemplate} und direkten Anweisungen — der Weg ueber die Entities
 * kaeme an einigen dieser Faelle gar nicht vorbei.
 */
class AngebotKommentarSchemaIT extends AbstractIntegrationTest {

  private static final String INSERT_KOMMENTAR =
      "INSERT INTO angebot_kommentar (angebot_id, verfasser_id, text) VALUES (?, ?, ?)";

  private static final String FIRMA = "Adler AG";

  private final JdbcTemplate jdbc;

  private long angebotId;
  private long verfasserId;

  @Autowired
  AngebotKommentarSchemaIT(final JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @BeforeEach
  void leereFachtabellenUndLegeEinAngebotAn() {
    jdbc.execute(
        "TRUNCATE angebot_kommentar, angebot_position, angebot, ansprechpartner, firma"
            + " RESTART IDENTITY CASCADE");
    jdbc.update("INSERT INTO firma (name) VALUES (?)", FIRMA);
    final long firmaId =
        jdbc.queryForObject("SELECT id FROM firma WHERE name = ?", Long.class, FIRMA).longValue();
    jdbc.update(
        "INSERT INTO angebot (firma_id, status, angebot_datum)"
            + " VALUES (?, 'ANGELEGT', DATE '2026-09-20')",
        firmaId);
    angebotId = jdbc.queryForObject("SELECT id FROM angebot LIMIT 1", Long.class).longValue();
    leereKonten(jdbc);
    verfasserId =
        jdbc.queryForObject(
                "INSERT INTO account (email, display_name, role)"
                    + " VALUES ('manne@example.org', 'Manne', 'C_LEVEL') RETURNING id",
                Long.class)
            .longValue();
  }

  private int kommentar(final String text) {
    return jdbc.update(INSERT_KOMMENTAR, angebotId, verfasserId, text);
  }

  private static String zeichen(final int laenge) {
    return "x".repeat(laenge);
  }

  @Test
  void kommentar_givenAText_thenAcceptedWithBothTimestampsSet() {
    // When
    final int betroffen = kommentar("Kunde ruft zurueck.");

    // Then
    assertThat(betroffen).isEqualTo(1);
    final Integer ohneZeitpunkt =
        jdbc.queryForObject(
            "SELECT count(*) FROM angebot_kommentar"
                + " WHERE created_at IS NULL OR updated_at IS NULL",
            Integer.class);
    assertThat(ohneZeitpunkt).isZero();
  }

  @Test
  void kommentarText_givenAnEmptyText_thenRejectedByTheDatabase() {
    // When / Then
    assertThatThrownBy(() -> kommentar(""))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("angebot_kommentar_text");
  }

  /**
   * Jede Art Leerraum einzeln, nicht nur das Leerzeichen: {@code btrim} ohne Zeichensatz schneidet
   * in PostgreSQL nur das Leerzeichen ab, und ein Text aus Zeilenumbruechen kaeme sonst durch,
   * obwohl {@code strip()} ihn zu nichts macht.
   */
  @ParameterizedTest
  @ValueSource(strings = {"   ", "\n", "\t", "\r", "\f", "\u000B", "  \n\t \u000B\r\f "})
  void kommentarText_givenATextOfWhitespaceOnly_thenRejectedByTheDatabase(final String leerraum) {
    // When / Then
    assertThatThrownBy(() -> kommentar(leerraum))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("angebot_kommentar_text");
  }

  @Test
  void kommentarText_given2001Characters_thenRejectedByTheDatabase() {
    final String zuLang = zeichen(2001);

    // When / Then
    assertThatThrownBy(() -> kommentar(zuLang))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("angebot_kommentar_laenge");
  }

  @Test
  void kommentarText_givenExactly2000Characters_thenAccepted() {
    // When
    final int betroffen = kommentar(zeichen(2000));

    // Then
    assertThat(betroffen).isEqualTo(1);
  }

  @Test
  void kommentarAngebotId_givenAnUnknownOffer_thenRejectedByTheDatabase() {
    // When / Then
    assertThatThrownBy(
            () -> jdbc.update(INSERT_KOMMENTAR, 4711L, verfasserId, "Kunde ruft zurueck."))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void migration_thenBothIndexesExistAndTheVerfasserIsAnIdNotAName() {
    // When
    final Integer indizes =
        jdbc.queryForObject(
            "SELECT count(*) FROM pg_indexes WHERE schemaname = 'public'"
                + " AND indexname IN ('angebot_kommentar_angebot_idx',"
                + " 'angebot_kommentar_verfasser_idx')",
            Integer.class);
    final Integer verfasser =
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public'"
                + " AND table_name = 'angebot_kommentar'"
                + " AND column_name IN ('verfasser', 'verfasser_name', 'benutzer_id')",
            Integer.class);

    // Then — Plan #414, A7: verfasser_id und kein kopierter Name, der nach einer Umbenennung
    // veraltete.
    assertThat(indizes).isEqualTo(2);
    assertThat(verfasser).isZero();
  }

  @Test
  void kommentar_givenTheOfferIsTruncated_thenTheCommentGoesWithIt() {
    // Given — die bestehenden Angebots-ITs leeren per TRUNCATE … CASCADE; der Fremdschluessel
    // ohne ON DELETE darf das nicht verhindern.
    kommentar("Kunde ruft zurueck.");

    // When
    jdbc.execute("TRUNCATE angebot RESTART IDENTITY CASCADE");

    // Then
    final Integer verbleibend =
        jdbc.queryForObject("SELECT count(*) FROM angebot_kommentar", Integer.class);
    assertThat(verbleibend).isZero();
  }

  @Test
  void migration_thenTheVersionStartsAtZeroAndTheAuthorReferencesTheAccount() {
    // When — V33 (Plan #415, E1): ein eigener Zaehler und der letzte Urheber.
    final String version = Schutzspalten.spalte(jdbc, "angebot_kommentar", "version");
    final String urheber = Schutzspalten.spalte(jdbc, "angebot_kommentar", "geaendert_von");
    final String verweis = Schutzspalten.verweis(jdbc, "angebot_kommentar", "geaendert_von");

    // Then — ohne ON DELETE wie jeder Fremdschluessel dieses Schemas.
    assertThat(version).isEqualTo("bigint NO 0");
    assertThat(urheber).isEqualTo("bigint YES -");
    assertThat(verweis).isEqualTo("account.id NO ACTION");
  }

  @Test
  void migration_thenTheDeletionMarkIsOptionalAndNamesTheAccount() {
    // When — V34 (Plan #415, E17): Zeitpunkt und Urheber der Loeschung.
    final String zeitpunkt = Schutzspalten.spalte(jdbc, "angebot_kommentar", "geloescht_am");
    final String urheber = Schutzspalten.spalte(jdbc, "angebot_kommentar", "geloescht_von");
    final String verweis = Schutzspalten.verweis(jdbc, "angebot_kommentar", "geloescht_von");

    // Then
    assertThat(zeitpunkt).isEqualTo("timestamp with time zone YES -");
    assertThat(urheber).isEqualTo("bigint YES -");
    assertThat(verweis).isEqualTo("account.id NO ACTION");
  }

  @Test
  void geloeschtVon_givenNoDeletionTime_thenRejectedByTheDatabase() {
    // Given
    kommentar("Kunde ruft zurueck.");
    final Long konto = Long.valueOf(verfasserId);

    // When / Then — ein Urheber der Loeschung steht nur an einer geloeschten Zeile.
    assertThatThrownBy(() -> jdbc.update("UPDATE angebot_kommentar SET geloescht_von = ?", konto))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("angebot_kommentar_geloescht_bindung");
  }

  @Test
  void geloeschtVon_givenADeletionTime_thenAccepted() {
    // Given
    kommentar("Kunde ruft zurueck.");

    // When
    final int betroffen =
        jdbc.update(
            "UPDATE angebot_kommentar SET geloescht_am = now(), geloescht_von = ?",
            Long.valueOf(verfasserId));

    // Then
    assertThat(betroffen).isEqualTo(1);
  }
}
