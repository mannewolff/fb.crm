package org.mwolff.fbcrm.arbeitszeit;

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
 * Prueft das Schema der Arbeitszeit nach {@code V19__arbeitszeit.sql} gegen eine echte
 * PostgreSQL-Instanz.
 *
 * <p>Gegenstand sind die Zusagen, die allein die Datenbank haelt: der Fremdschluessel auf die
 * Angebotsposition und die beiden CHECKs um {@code bis > von} und das Raster der Viertelstunde
 * (Plan #194, A4). Gearbeitet wird mit {@link JdbcTemplate} und direkten Anweisungen — der Weg
 * ueber den Bestand kaeme an diesen Faellen gar nicht vorbei, weil der Record sie vorher abweist.
 */
class ArbeitszeitSchemaIT extends AbstractIntegrationTest {

  private static final String INSERT =
      "INSERT INTO arbeitszeit (angebot_position_id, tag, von, bis)"
          + " VALUES (?, DATE '2026-11-12', CAST(? AS time), CAST(? AS time))";

  private final JdbcTemplate jdbc;

  private long angebotPositionId;

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
            + " (angebot_id, position, bezeichnung, abrechnungsmodus, menge, einheit, einzelpreis)"
            + " VALUES (?, 1, 'Konzeption', 'AUFWAND', 20, 'STUNDE', 120.00)",
        Long.valueOf(angebotId));
    angebotPositionId =
        jdbc.queryForObject("SELECT id FROM angebot_position LIMIT 1", Long.class).longValue();
  }

  private int eintrag(final long positionId, final String von, final String bis) {
    return jdbc.update(INSERT, Long.valueOf(positionId), von, bis);
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
  void migration_thenBothLookupPathsHaveAnIndex() {
    // When — nachgeschlagen wird je Monat (tag) und je Position (A4).
    final Integer spuren =
        jdbc.queryForObject(
            "SELECT count(*) FROM pg_indexes WHERE schemaname = 'public'"
                + " AND tablename = 'arbeitszeit'"
                + " AND indexname IN ('arbeitszeit_tag_idx', 'arbeitszeit_angebot_position_idx')",
            Integer.class);

    // Then
    assertThat(spuren).isEqualTo(2);
  }
}
