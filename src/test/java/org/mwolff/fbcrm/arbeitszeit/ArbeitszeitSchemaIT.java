package org.mwolff.fbcrm.arbeitszeit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.jspecify.annotations.Nullable;
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
 * Prueft das Schema der Arbeitszeit nach {@code V19__arbeitszeit.sql} gegen eine echte
 * PostgreSQL-Instanz.
 *
 * <p>Gegenstand sind die Zusagen, die allein die Datenbank haelt: der Fremdschluessel auf die
 * Angebotsposition und die beiden CHECKs um {@code bis > von} und das Raster der Viertelstunde
 * (Plan #194, A4). Gearbeitet wird mit {@link JdbcTemplate} und direkten Anweisungen — der Weg
 * ueber den Bestand kaeme an diesen Faellen gar nicht vorbei, weil der Record sie vorher abweist.
 *
 * <p>Seit {@code V32__arbeitszeit_person.sql} gehoert jeder Eintrag einem Konto (Plan #426, E1, E3,
 * E9): Die Spalte {@code account_id} ist Pflicht, verweist auf {@code account (id)} und liegt mit
 * dem Tag in einem eigenen Index. Der Vorlauf legt dafuer ein Konto an, und jeder Eintrag gehoert
 * ihm. Die Uebernahme des Altbestands prueft {@code ArbeitszeitMigrationIT}.
 */
class ArbeitszeitSchemaIT extends AbstractIntegrationTest {

  private static final String INSERT =
      "INSERT INTO arbeitszeit (account_id, angebot_position_id, tag, von, bis)"
          + " VALUES (?, ?, DATE '2026-11-12', CAST(? AS time), CAST(? AS time))";

  private final JdbcTemplate jdbc;

  private long angebotPositionId;
  private long accountId;

  @Autowired
  ArbeitszeitSchemaIT(final JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @BeforeEach
  void leereFachtabellenUndLegeEineAngebotspositionAn() {
    jdbc.execute(
        "TRUNCATE arbeitszeit, rechnung_position, rechnung, angebot_position, angebot,"
            + " ansprechpartner, firma RESTART IDENTITY CASCADE");
    jdbc.update("INSERT INTO firma (name) VALUES ('IT Bildungshaus')");
    final long firmaId =
        jdbc.queryForObject("SELECT id FROM firma LIMIT 1", Long.class).longValue();
    jdbc.update(
        "INSERT INTO angebot (firma_id, status, angebot_datum)"
            + " VALUES (?, 'BESTELLT', DATE '2026-11-01')",
        Long.valueOf(firmaId));
    final long angebotId =
        jdbc.queryForObject("SELECT id FROM angebot LIMIT 1", Long.class).longValue();
    jdbc.update(
        "INSERT INTO angebot_position"
            + " (angebot_id, position, bezeichnung, abrechnungsmodus, menge, einheit, einzelpreis,"
            + " leistungsjahr)"
            + " VALUES (?, 1, 'Konzeption', 'AUFWAND', 20, 'STUNDE', 120.00, 2026)",
        Long.valueOf(angebotId));
    angebotPositionId =
        jdbc.queryForObject("SELECT id FROM angebot_position LIMIT 1", Long.class).longValue();
    leereKonten(jdbc);
    accountId =
        jdbc.queryForObject(
                "INSERT INTO account (email, display_name, role)"
                    + " VALUES ('manne@example.org', 'Manne', 'C_LEVEL') RETURNING id",
                Long.class)
            .longValue();
  }

  private int eintrag(final long positionId, final String von, final String bis) {
    return eintrag(Long.valueOf(accountId), positionId, von, bis);
  }

  private int eintrag(
      final @Nullable Long konto, final long positionId, final String von, final String bis) {
    return jdbc.update(INSERT, konto, Long.valueOf(positionId), von, bis);
  }

  @Test
  void arbeitszeit_givenAnEntryOnTheQuarterHourGrid_thenAccepted() {
    // When — 9:15 liegt im Raster und kommt durch; sonst belegte der Test nur einen Tippfehler.
    final int betroffen = eintrag(angebotPositionId, "09:15", "11:00");

    // Then
    assertThat(betroffen).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"09:10", "09:01", "09:00:30", "09:00:00.5"})
  void arbeitszeitRaster_givenATimeOutsideTheGrid_thenRejectedByTheDatabase(final String von) {
    // When / Then — 9:10 ist keine Viertelstunde.
    assertThatThrownBy(() -> eintrag(angebotPositionId, von, "11:00"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("arbeitszeit_raster");
  }

  @Test
  void arbeitszeitRaster_givenAnEndOutsideTheGrid_thenRejectedByTheDatabase() {
    // When / Then — der CHECK gilt fuer beide Uhrzeiten.
    assertThatThrownBy(() -> eintrag(angebotPositionId, "09:00", "11:10"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("arbeitszeit_raster");
  }

  @Test
  void arbeitszeitZeitraum_givenAnEndBeforeTheStart_thenRejectedByTheDatabase() {
    // When / Then
    assertThatThrownBy(() -> eintrag(angebotPositionId, "11:00", "09:00"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("arbeitszeit_zeitraum");
  }

  @Test
  void arbeitszeitZeitraum_givenAnEndEqualToTheStart_thenRejectedByTheDatabase() {
    // When / Then — ein Eintrag ohne Dauer ist kein Eintrag.
    assertThatThrownBy(() -> eintrag(angebotPositionId, "09:00", "09:00"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("arbeitszeit_zeitraum");
  }

  @Test
  void arbeitszeit_givenAnUnknownAngebotsposition_thenRejectedByTheDatabase() {
    // When / Then — ohne Angebotsposition gibt es keine Arbeitszeit.
    assertThatThrownBy(() -> eintrag(4711L, "09:00", "11:00"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void arbeitszeit_givenAnUnknownAccount_thenRejectedByTheForeignKey() {
    // When / Then — Plan #426, E1: ein Eintrag gehoert einem vorhandenen Konto.
    assertThatThrownBy(() -> eintrag(Long.valueOf(4711L), angebotPositionId, "09:00", "11:00"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("arbeitszeit_account_id_fkey");
  }

  @Test
  void arbeitszeit_givenNoAccount_thenRejectedByTheDatabase() {
    // When / Then — E3: nach der Uebernahme des Altbestands ist die Spalte Pflicht.
    assertThatThrownBy(() -> eintrag(null, angebotPositionId, "09:00", "11:00"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("account_id");
  }

  @Test
  void migration_thenTheAccountColumnIsMandatory() {
    // When
    final String nullable =
        jdbc.queryForObject(
            "SELECT is_nullable FROM information_schema.columns WHERE table_schema = 'public'"
                + " AND table_name = 'arbeitszeit' AND column_name = 'account_id'",
            String.class);

    // Then
    assertThat(nullable).isEqualTo("NO");
  }

  @Test
  void migration_thenTheAccountColumnReferencesTheAccountWithoutOnDelete() {
    // When — ohne ON DELETE wie an jedem Fremdschluessel dieses Schemas: Konten werden
    // stillgelegt, nicht geloescht.
    final String regel =
        jdbc.queryForObject(
            "SELECT rc.delete_rule FROM information_schema.referential_constraints rc"
                + " JOIN information_schema.constraint_column_usage ccu"
                + " ON ccu.constraint_name = rc.constraint_name"
                + " AND ccu.constraint_schema = rc.constraint_schema"
                + " WHERE rc.constraint_schema = 'public'"
                + " AND rc.constraint_name = 'arbeitszeit_account_id_fkey'"
                + " AND ccu.table_name = 'account' AND ccu.column_name = 'id'",
            String.class);

    // Then
    assertThat(regel).isEqualTo("NO ACTION");
  }

  @Test
  void migration_thenNoStoredDurationColumnExists() {
    // When — A3: Die Dauer wird aus von und bis gerechnet, nicht gefuehrt.
    final Integer spalten =
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public'"
                + " AND table_name = 'arbeitszeit'"
                + " AND column_name IN ('dauer', 'minuten', 'stunden')",
            Integer.class);

    // Then
    assertThat(spalten).isZero();
  }

  @Test
  void migration_thenEveryLookupPathHasAnIndex() {
    // When — nachgeschlagen wird je Monat (tag), je Position (A4) und je Person und Tag (Plan
    // #426, E9).
    final Integer spuren =
        jdbc.queryForObject(
            "SELECT count(*) FROM pg_indexes WHERE schemaname = 'public'"
                + " AND tablename = 'arbeitszeit'"
                + " AND indexname IN ('arbeitszeit_tag_idx', 'arbeitszeit_angebot_position_idx',"
                + " 'arbeitszeit_account_tag_idx')",
            Integer.class);

    // Then
    assertThat(spuren).isEqualTo(3);
  }

  @Test
  void migration_thenThePersonIndexLeadsWithTheAccountAndThenTheDay() {
    // When — E9: das Konto an erster Stelle, damit die Abfrage je Person auf ihm liegt.
    final String definition =
        jdbc.queryForObject(
            "SELECT indexdef FROM pg_indexes WHERE schemaname = 'public'"
                + " AND indexname = 'arbeitszeit_account_tag_idx'",
            String.class);

    // Then
    assertThat(definition).endsWith("(account_id, tag)");
  }

  @Test
  void migration_thenTheVersionStartsAtZeroAndTheAuthorReferencesTheAccount() {
    // When — V33 (Plan #415, E1): ein eigener Zaehler und der letzte Urheber.
    final String version = Schutzspalten.spalte(jdbc, "arbeitszeit", "version");
    final String urheber = Schutzspalten.spalte(jdbc, "arbeitszeit", "geaendert_von");
    final String verweis = Schutzspalten.verweis(jdbc, "arbeitszeit", "geaendert_von");

    // Then — ohne ON DELETE wie jeder Fremdschluessel dieses Schemas.
    assertThat(version).isEqualTo("bigint NO 0");
    assertThat(urheber).isEqualTo("bigint YES -");
    assertThat(verweis).isEqualTo("account.id NO ACTION");
  }

  @Test
  void migration_thenTheDeletionMarkIsOptionalAndNamesTheAccount() {
    // When — V34 (Plan #415, E17): Zeitpunkt und Urheber der Loeschung.
    final String zeitpunkt = Schutzspalten.spalte(jdbc, "arbeitszeit", "geloescht_am");
    final String urheber = Schutzspalten.spalte(jdbc, "arbeitszeit", "geloescht_von");
    final String verweis = Schutzspalten.verweis(jdbc, "arbeitszeit", "geloescht_von");

    // Then
    assertThat(zeitpunkt).isEqualTo("timestamp with time zone YES -");
    assertThat(urheber).isEqualTo("bigint YES -");
    assertThat(verweis).isEqualTo("account.id NO ACTION");
  }

  @Test
  void geloeschtVon_givenNoDeletionTime_thenRejectedByTheDatabase() {
    // Given
    eintrag(angebotPositionId, "09:00", "11:00");
    final Long konto = Long.valueOf(accountId);

    // When / Then — ein Urheber der Loeschung steht nur an einer geloeschten Zeile.
    assertThatThrownBy(() -> jdbc.update("UPDATE arbeitszeit SET geloescht_von = ?", konto))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("arbeitszeit_geloescht_bindung");
  }

  @Test
  void geloeschtVon_givenADeletionTime_thenAccepted() {
    // Given
    eintrag(angebotPositionId, "09:00", "11:00");

    // When
    final int betroffen =
        jdbc.update(
            "UPDATE arbeitszeit SET geloescht_am = now(), geloescht_von = ?",
            Long.valueOf(accountId));

    // Then
    assertThat(betroffen).isEqualTo(1);
  }
}
