package org.mwolff.fbcrm.angebot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Prueft das Schema des Angebots nach {@code V11__angebot_ohne_beleg.sql} gegen eine echte
 * PostgreSQL-Instanz.
 *
 * <p>Gegenstand sind die Zusagen, die allein die Datenbank haelt: die fuenf Status als CHECK, die
 * Wertebereiche der Positionen und die Reihenfolge als Schluessel. Gearbeitet wird mit {@link
 * JdbcTemplate} und direkten Anweisungen — der Weg ueber die Entities kaeme an einigen dieser
 * Faelle gar nicht vorbei.
 */
class AngebotSchemaIT extends AbstractIntegrationTest {

  private static final String INSERT_ANGEBOT =
      "INSERT INTO angebot (firma_id, status, angebot_datum) VALUES (?, ?, DATE '2026-09-20')";

  private static final String INSERT_POSITION =
      "INSERT INTO angebot_position"
          + " (angebot_id, position, bezeichnung, abrechnungsmodus, menge, einheit, einzelpreis)"
          + " VALUES (?, ?, ?, ?, CAST(? AS numeric), ?, CAST(? AS numeric))";

  private static final String FIRMA = "Adler AG";
  private final JdbcTemplate jdbc;

  private long firmaId;

  @Autowired
  AngebotSchemaIT(final JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @BeforeEach
  void leereFachtabellenUndLegeEineFirmaAn() {
    jdbc.execute(
        "TRUNCATE angebot_position, angebot, ansprechpartner, firma RESTART IDENTITY CASCADE");
    jdbc.update("INSERT INTO firma (name) VALUES (?)", FIRMA);
    firmaId =
        jdbc.queryForObject("SELECT id FROM firma WHERE name = ?", Long.class, FIRMA).longValue();
  }

  private int angebot(final @Nullable String status) {
    return jdbc.update(INSERT_ANGEBOT, firmaId, status);
  }

  private Long angebotId() {
    angebot("ANGELEGT");
    return jdbc.queryForObject("SELECT id FROM angebot LIMIT 1", Long.class);
  }

  @Test
  void migration_thenBothTablesExistAndTheNumberRangeIsGone() {
    // When
    final Integer tabellen =
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'"
                + " AND table_name IN ('angebot', 'angebot_position', 'angebot_nummernkreis')",
            Integer.class);

    // Then
    assertThat(tabellen).isEqualTo(2);
  }

  @Test
  void migration_thenOnlyTheFirmaIndexRemains() {
    // When
    final Integer indizes =
        jdbc.queryForObject(
            "SELECT count(*) FROM pg_indexes WHERE schemaname = 'public'"
                + " AND indexname IN ('angebot_firma_idx', 'angebot_zustand_gueltigkeit_idx')",
            Integer.class);

    // Then
    assertThat(indizes).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"ANGELEGT", "ABGEGEBEN", "BESTELLT", "ERLEDIGT", "ABGERECHNET"})
  void angebot_givenEachOfTheFiveStatus_thenAccepted(final String status) {
    // When
    final int betroffen = angebot(status);

    // Then
    assertThat(betroffen).isEqualTo(1);
  }

  @Test
  void angebotStatus_givenAnUnknownStatus_thenRejectedByTheDatabase() {
    // When / Then
    assertThatThrownBy(() -> angebot("ENTWURF"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("angebot_status");
  }

  @Test
  void angebotStatus_givenNoStatus_thenRejectedByTheDatabase() {
    // When / Then
    assertThatThrownBy(() -> angebot(null)).isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void angebotFirmaId_givenAnUnknownFirma_thenRejectedByTheDatabase() {
    // When / Then
    assertThatThrownBy(() -> jdbc.update(INSERT_ANGEBOT, 4711L, "ANGELEGT"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void position_givenABlankLabel_thenAccepted() {
    // When — die fehlende Bezeichnung weist der Eingang der Maske ab, nicht die Datenbank.
    final int betroffen =
        jdbc.update(INSERT_POSITION, angebotId(), 1, "   ", "AUFWAND", "2.50", "PERSONENTAG", "0");

    // Then
    assertThat(betroffen).isEqualTo(1);
  }

  @Test
  void position_givenTheSamePlaceTwiceAtOneOffer_thenRejectedByTheDatabase() {
    // Given
    final Long angebotId = angebotId();
    jdbc.update(INSERT_POSITION, angebotId, 1, "Konzeption", "AUFWAND", "1.00", "STUNDE", "95.00");

    // When / Then
    assertThatThrownBy(
            () ->
                jdbc.update(
                    INSERT_POSITION,
                    angebotId,
                    1,
                    "Schulung",
                    "FESTPREIS",
                    "1.00",
                    "PAUSCHAL",
                    "1200.00"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("angebot_position_reihenfolge");
  }

  @Test
  void position_givenANegativeQuantity_thenRejectedByTheDatabase() {
    // Given
    final Long angebotId = angebotId();

    // When / Then
    assertThatThrownBy(
            () ->
                jdbc.update(
                    INSERT_POSITION,
                    angebotId,
                    1,
                    "Konzeption",
                    "AUFWAND",
                    "-1.00",
                    "STUNDE",
                    "95.00"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("angebot_position_menge");
  }

  @Test
  void position_givenANegativeUnitPrice_thenRejectedByTheDatabase() {
    // Given
    final Long angebotId = angebotId();

    // When / Then
    assertThatThrownBy(
            () ->
                jdbc.update(
                    INSERT_POSITION,
                    angebotId,
                    1,
                    "Konzeption",
                    "AUFWAND",
                    "1.00",
                    "STUNDE",
                    "-95.00"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("angebot_position_einzelpreis");
  }

  @Test
  void position_givenAnUnknownBillingMode_thenRejectedByTheDatabase() {
    // Given
    final Long angebotId = angebotId();

    // When / Then
    assertThatThrownBy(
            () ->
                jdbc.update(
                    INSERT_POSITION,
                    angebotId,
                    1,
                    "Konzeption",
                    "SCHAETZUNG",
                    "1.00",
                    "STUNDE",
                    "95.00"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("angebot_position_abrechnungsmodus");
  }

  @Test
  void position_givenAnUnknownUnit_thenRejectedByTheDatabase() {
    // Given
    final Long angebotId = angebotId();

    // When / Then
    assertThatThrownBy(
            () ->
                jdbc.update(
                    INSERT_POSITION,
                    angebotId,
                    1,
                    "Konzeption",
                    "AUFWAND",
                    "1.00",
                    "WOCHE",
                    "95.00"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("angebot_position_einheit");
  }

  @Test
  void positionAngebotId_givenAnUnknownOffer_thenRejectedByTheDatabase() {
    // When / Then
    assertThatThrownBy(
            () ->
                jdbc.update(
                    INSERT_POSITION, 4711L, 1, "Konzeption", "AUFWAND", "1.00", "STUNDE", "95.00"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void migration_thenNoStoredAmountOrSumColumnExists() {
    // When — E5: Betrag und Summe werden gerechnet, nicht gefuehrt.
    final Integer spalten =
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public'"
                + " AND table_name IN ('angebot', 'angebot_position')"
                + " AND column_name IN ('betrag', 'summe')",
            Integer.class);

    // Then
    assertThat(spalten).isZero();
  }
}
