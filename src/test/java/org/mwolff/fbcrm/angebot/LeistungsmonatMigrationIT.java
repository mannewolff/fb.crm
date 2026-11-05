package org.mwolff.fbcrm.angebot;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Prueft {@code V40__angebot_position_leistungsmonat.sql}: Die Angebotsposition bekommt freiwillig
 * den Monat ihrer Leistung (#483; Plan #484, A1).
 *
 * <p>Wie in {@code LeistungsjahrMigrationIT} migriert der Test ein eigenes Schema zuerst bis
 * Version 39, legt dort das Angebot „KI Workshop I" mit zwei Positionen an, die nur ein Jahr
 * tragen, und migriert danach zu Ende. Im Schema der Suite gibt es den Zustand vor V40 nicht mehr.
 *
 * <p>Gegenstand ist, dass die Migration nichts fuellt: Bestehende Positionen tragen danach keinen
 * Monat (#483, Kriterium 7) und behalten ihr Jahr.
 */
class LeistungsmonatMigrationIT extends AbstractIntegrationTest {

  private static final String PROBE = "migration_v40";

  private final JdbcTemplate jdbc;
  private final DataSource dataSource;

  private long angebot;

  @Autowired
  LeistungsmonatMigrationIT(final JdbcTemplate jdbc, final DataSource dataSource) {
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
  void migriereBisNeununddreissigUndLegeEinAngebotAn() {
    final Flyway bisNeununddreissig = flywayBis("39");
    bisNeununddreissig.clean();
    bisNeununddreissig.migrate();
    jdbc.update("INSERT INTO " + PROBE + ".firma (name) VALUES ('IT Bildungshaus')");
    final Long firma = jdbc.queryForObject("SELECT id FROM " + PROBE + ".firma", Long.class);
    jdbc.update(
        "INSERT INTO "
            + PROBE
            + ".angebot (firma_id, status, titel, angebot_datum)"
            + " VALUES (?, 'ANGELEGT', 'KI Workshop I', DATE '2026-09-15')",
        firma);
    angebot = jdbc.queryForObject("SELECT id FROM " + PROBE + ".angebot", Long.class).longValue();
  }

  @AfterEach
  void raeumeDasSchemaAb() {
    flywayBis("latest").clean();
  }

  /* Eine Position im Stand vor V40 — nur mit Jahr, weil es die Spalte fuer den Monat nicht gibt. */
  private void positionVorV40(final int platz, final String bezeichnung) {
    jdbc.update(
        "INSERT INTO "
            + PROBE
            + ".angebot_position (angebot_id, position, bezeichnung, abrechnungsmodus, menge,"
            + " einheit, einzelpreis, leistungsjahr)"
            + " VALUES (?, ?, ?, 'AUFWAND', 135, 'STUNDE', 60.00, 2027)",
        Long.valueOf(angebot),
        Integer.valueOf(platz),
        bezeichnung);
  }

  @Test
  @DisplayName("Szenario: Bestehende Positionen nach der Änderung")
  void migration_givenPositionsWithOnlyAYear_thenNoneCarriesAMonthAndEachKeepsItsYear() {
    // Given — #483, Kriterium 7: vor der Änderung trugen alle Positionen nur ein Jahr.
    positionVorV40(1, "Aufwände Februar 2027");
    positionVorV40(2, "Aufwände März 2027");

    // When
    flywayBis("latest").migrate();

    // Then — keine Zeile bekommt einen Monat, und das Jahr bleibt.
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM "
                    + PROBE
                    + ".angebot_position WHERE leistungsmonat IS NOT NULL",
                Integer.class))
        .isZero();
    final List<Short> jahre =
        jdbc.queryForList(
            "SELECT leistungsjahr FROM " + PROBE + ".angebot_position ORDER BY position",
            Short.class);
    assertThat(jahre).containsExactly((short) 2027, (short) 2027);
  }
}
