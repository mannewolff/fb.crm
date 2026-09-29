package org.mwolff.fbcrm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Prueft {@code V10__vorgang_entfernen.sql}: Das Angebot geht direkt an eine Firma (Issue #126).
 *
 * <p>Zwei Blickwinkel. Erstens das Endschema im Schema der Suite: Die drei Tabellen der alten
 * Klammer fehlen, und {@code angebot} traegt {@code firma_id} als Pflicht und {@code
 * ansprechpartner_id} als Kann. Zweitens die Datenuebernahme: Ein Angebot, das vor V10 an einer
 * Klammer hing, traegt danach deren Firma und Ansprechpartner. Dafuer migriert der Test ein eigenes
 * Schema zuerst bis Version 9, legt dort die Daten von damals an und migriert danach zu Ende — im
 * Schema der Suite gibt es den Zustand vor V10 nicht mehr.
 */
class KundenMigrationIT extends AbstractIntegrationTest {

  private static final String PROBE = "migration_v10";

  private final JdbcTemplate jdbc;
  private final DataSource dataSource;

  @Autowired
  KundenMigrationIT(final JdbcTemplate jdbc, final DataSource dataSource) {
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

  @Test
  void migration_thenTheOldTablesAreGone() {
    // When
    final Integer tabellen =
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'"
                + " AND table_name IN ('vorgang', 'vorgang_eintrag', 'vorgang_nummernkreis')",
            Integer.class);

    // Then
    assertThat(tabellen).isZero();
  }

  @Test
  void migration_thenTheAngebotCarriesFirmaAndContactButNoLongerTheOldLink() {
    // When
    final List<Map<String, Object>> spalten =
        jdbc.queryForList(
            "SELECT column_name, is_nullable FROM information_schema.columns"
                + " WHERE table_schema = 'public' AND table_name = 'angebot'"
                + " AND column_name IN ('firma_id', 'ansprechpartner_id', 'vorgang_id')"
                + " ORDER BY column_name");

    // Then
    assertThat(spalten)
        .containsExactly(
            Map.of("column_name", "ansprechpartner_id", "is_nullable", "YES"),
            Map.of("column_name", "firma_id", "is_nullable", "NO"));
  }

  @Test
  void migration_givenAnAngebotFromBefore_thenItKeepsTheFirmaAndTheContactOfItsOldLink() {
    // Given — ein eigenes Schema im Stand von Version 9, mit Daten von damals.
    final Flyway bisNeun = flywayBis("9");
    bisNeun.clean();
    bisNeun.migrate();
    jdbc.update("INSERT INTO " + PROBE + ".firma (name) VALUES ('Adler AG')");
    final Long firma = jdbc.queryForObject("SELECT id FROM " + PROBE + ".firma", Long.class);
    jdbc.update(
        "INSERT INTO " + PROBE + ".ansprechpartner (firma_id, nachname) VALUES (?, 'Adler')",
        firma);
    final Long person =
        jdbc.queryForObject("SELECT id FROM " + PROBE + ".ansprechpartner", Long.class);
    jdbc.update(
        "INSERT INTO "
            + PROBE
            + ".vorgang (nummer, titel, firma_id, ansprechpartner_id)"
            + " VALUES (1, 'Website-Relaunch', ?, ?)",
        firma,
        person);
    final Long klammer = jdbc.queryForObject("SELECT id FROM " + PROBE + ".vorgang", Long.class);
    jdbc.update(
        "INSERT INTO "
            + PROBE
            + ".angebot (vorgang_id, zustand, angebot_datum, gueltig_bis)"
            + " VALUES (?, 'ENTWURF', DATE '2026-09-20', DATE '2026-10-20')",
        klammer);

    // When
    flywayBis("latest").migrate();

    // Then
    final Map<String, Object> angebot =
        jdbc.queryForMap("SELECT firma_id, ansprechpartner_id FROM " + PROBE + ".angebot");
    assertThat(angebot)
        .containsEntry("firma_id", firma)
        .containsEntry("ansprechpartner_id", person);
    flywayBis("latest").clean();
  }
}
