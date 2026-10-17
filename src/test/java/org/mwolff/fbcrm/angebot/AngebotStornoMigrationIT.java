package org.mwolff.fbcrm.angebot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Prueft {@code V25__angebot_storno.sql}: Das Angebot bekommt das Kennzeichen {@code storniert} und
 * den Grund {@code storno_grund} (Issue #333, Plan #332 E1, E3 und E4).
 *
 * <p>Wie in {@code AngebotInternMigrationIT} migriert der Test ein eigenes Schema zuerst bis
 * Version 24, legt dort ein Angebot an und migriert danach zu Ende. Im Schema der Suite gibt es den
 * Zustand vor V25 nicht mehr.
 *
 * <p>Gegenstand ist, dass die Migration nichts verschiebt — eine bestehende Zeile ist danach nicht
 * storniert und ohne Grund —, und dass jeder der vier CHECKs seinen Fall abweist und dabei seinen
 * eigenen Namen nennt (E4). Jeder Fall verletzt genau einen CHECK. Einen Fall „Storno am internen
 * Angebot" gibt es nicht: Er faellt mit dem Statusfall zusammen, weil {@code angebot_art_status}
 * aus V20 {@code intern} an {@code LAEUFT} und {@code ABGESCHLOSSEN} bindet.
 */
class AngebotStornoMigrationIT extends AbstractIntegrationTest {

  private static final String PROBE = "migration_v25";

  private final JdbcTemplate jdbc;
  private final DataSource dataSource;

  private long firma;

  @Autowired
  AngebotStornoMigrationIT(final JdbcTemplate jdbc, final DataSource dataSource) {
    this.jdbc = jdbc;
    this.dataSource = dataSource;
  }

  private Flyway flywayBis(final String ziel) {
    return Flyway.configure()
        .dataSource(dataSource)
        .schemas(PROBE)
        .locations("classpath:db/migration")
        .target(ziel)
        .cleanDisabled(false)
        .load();
  }

  @BeforeEach
  void migriereBisVierundzwanzigUndLegeEineFirmaAn() {
    final Flyway bisVierundzwanzig = flywayBis("24");
    bisVierundzwanzig.clean();
    bisVierundzwanzig.migrate();
    jdbc.update("INSERT INTO " + PROBE + ".firma (name) VALUES ('Adler AG')");
    firma = jdbc.queryForObject("SELECT id FROM " + PROBE + ".firma", Long.class).longValue();
  }

  @AfterEach
  void raeumeDasSchemaAb() {
    flywayBis("latest").clean();
  }

  private int angebot(
      final String status,
      final boolean intern,
      final boolean storniert,
      final @Nullable String grund) {
    return jdbc.update(
        "INSERT INTO "
            + PROBE
            + ".angebot (firma_id, status, intern, storniert, storno_grund, angebot_datum)"
            + " VALUES (?, ?, ?, ?, ?, DATE '2026-09-20')",
        Long.valueOf(firma),
        status,
        Boolean.valueOf(intern),
        Boolean.valueOf(storniert),
        grund);
  }

  @Test
  void migration_givenAnOfferFromBefore_thenItIsNotStornoedAndHasNoReason() {
    // Given — eine Zeile, wie sie vor V25 im Bestand steht.
    jdbc.update(
        "INSERT INTO "
            + PROBE
            + ".angebot (firma_id, status, angebot_datum) VALUES (?, 'ABGEGEBEN', DATE"
            + " '2026-09-20')",
        Long.valueOf(firma));

    // When
    flywayBis("latest").migrate();
    final Map<String, Object> zeile =
        jdbc.queryForMap("SELECT status, storniert, storno_grund FROM " + PROBE + ".angebot");

    // Then — gueltig, nicht storniert, ohne Grund und im alten Status.
    assertThat(zeile)
        .containsEntry("status", "ABGEGEBEN")
        .containsEntry("storniert", false)
        .containsEntry("storno_grund", null);
  }

  @Test
  void migration_thenAStornoWithAReasonIsAccepted() {
    // Given
    flywayBis("latest").migrate();

    // When — der Fall, fuer den die Spalten da sind.
    final int betroffen = angebot("BESTELLT", false, true, "Auftrag zurueckgezogen");

    // Then
    assertThat(betroffen).isEqualTo(1);
  }

  @Test
  void stornoGrundBindung_givenAReasonWithoutStorno_thenRejected() {
    // Given
    flywayBis("latest").migrate();

    // When / Then
    assertThatThrownBy(() -> angebot("ABGEGEBEN", false, false, "vom Kunden abgelehnt"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("angebot_storno_grund_bindung");
  }

  @Test
  void stornoGrundText_givenAReasonOfOnlyWhitespace_thenRejected() {
    // Given
    flywayBis("latest").migrate();

    // When / Then — der Zeichensatz aus V12: auch Zeilenumbruch und Tabulator zaehlen als leer.
    assertThatThrownBy(() -> angebot("ABGEGEBEN", false, true, " \n\t "))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("angebot_storno_grund_text");
  }

  @Test
  void stornoGrundLaenge_givenAReasonOf2001Characters_thenRejected() {
    // Given
    flywayBis("latest").migrate();

    // When / Then
    assertThatThrownBy(() -> angebot("ABGEGEBEN", false, true, "x".repeat(2001)))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("angebot_storno_grund_laenge");
  }

  @Test
  void stornoGrundLaenge_givenAReasonOf2000Characters_thenAccepted() {
    // Given
    flywayBis("latest").migrate();

    // When / Then — die Grenze selbst geht durch.
    assertThat(angebot("ABGEGEBEN", false, true, "x".repeat(2000))).isEqualTo(1);
  }

  @ParameterizedTest
  @CsvSource({"ERLEDIGT, false", "ABGERECHNET, false", "LAEUFT, true", "ABGESCHLOSSEN, true"})
  void stornoStatus_givenAStornoInAStatusAfterTheWorkOrOfInternalWork_thenRejected(
      final String status, final boolean intern) {
    // Given
    flywayBis("latest").migrate();

    // When / Then — E3: dieselbe Statusmenge wie im Konstruktor von Angebot.
    assertThatThrownBy(() -> angebot(status, intern, true, null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("angebot_storno_status");
  }
}
