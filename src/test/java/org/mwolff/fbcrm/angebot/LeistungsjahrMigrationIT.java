package org.mwolff.fbcrm.angebot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Prueft {@code V23__angebot_position_leistungsjahr.sql}: Jede Angebotsposition bekommt das Jahr
 * ihrer Leistung (Plan #303, E10, E11; fachliche Quelle #302, Kriterium 8).
 *
 * <p>Wie in {@code AngebotInternMigrationIT} migriert der Test ein eigenes Schema zuerst bis
 * Version 22, legt dort ein Angebot vom 15.10.2026 mit zwei Positionen an und migriert danach zu
 * Ende. Im Schema der Suite gibt es den Zustand vor V23 nicht mehr.
 *
 * <p>Gegenstand ist der Fuellschritt: Jede bestehende Position traegt danach das Jahr ihres
 * Angebotsdatums, und keine bleibt ohne Jahr — eine Gruppe „ohne Jahr" gibt es im Forecast nicht.
 * Dazu, dass die Spalte im Endschema Pflicht ist und der CHECK ein Jahr ausserhalb von 2000 bis
 * 2099 abweist.
 */
class LeistungsjahrMigrationIT extends AbstractIntegrationTest {

  private static final String PROBE = "migration_v23";

  private final JdbcTemplate jdbc;
  private final DataSource dataSource;

  private long angebot;

  @Autowired
  LeistungsjahrMigrationIT(final JdbcTemplate jdbc, final DataSource dataSource) {
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
  void migriereBisZweiundzwanzigUndLegeEinAngebotAn() {
    final Flyway bisZweiundzwanzig = flywayBis("22");
    bisZweiundzwanzig.clean();
    bisZweiundzwanzig.migrate();
    jdbc.update("INSERT INTO " + PROBE + ".firma (name) VALUES ('IT Bildungshaus')");
    final Long firma = jdbc.queryForObject("SELECT id FROM " + PROBE + ".firma", Long.class);
    jdbc.update(
        "INSERT INTO "
            + PROBE
            + ".angebot (firma_id, status, angebot_datum) VALUES (?, 'ANGELEGT', DATE '2026-10-15')",
        firma);
    angebot = jdbc.queryForObject("SELECT id FROM " + PROBE + ".angebot", Long.class).longValue();
  }

  @AfterEach
  void raeumeDasSchemaAb() {
    flywayBis("latest").clean();
  }

  /* Eine Position im Stand vor V23 — ohne Jahr, weil es die Spalte noch nicht gibt. */
  private void positionVorV23(final int platz, final String bezeichnung) {
    jdbc.update(
        "INSERT INTO "
            + PROBE
            + ".angebot_position"
            + " (angebot_id, position, bezeichnung, abrechnungsmodus, menge, einheit, einzelpreis)"
            + " VALUES (?, ?, ?, 'AUFWAND', 135, 'STUNDE', 60.00)",
        Long.valueOf(angebot),
        Integer.valueOf(platz),
        bezeichnung);
  }

  @Test
  void migration_givenTwoPositionsOfAnAngebotFrom2026_thenBothCarryTheYear2026() {
    // Given — zwei Positionen, angelegt vor V23 und darum ohne Jahr.
    positionVorV23(1, "Aufwände Mai 2027");
    positionVorV23(2, "Aufwände Juni 2027");

    // When
    flywayBis("latest").migrate();

    // Then — Kriterium 8: das Jahr des Angebotsdatums, und keine Zeile bleibt ohne Jahr.
    final List<Short> jahre =
        jdbc.queryForList(
            "SELECT leistungsjahr FROM " + PROBE + ".angebot_position ORDER BY position",
            Short.class);
    assertThat(jahre).containsExactly((short) 2026, (short) 2026);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM " + PROBE + ".angebot_position WHERE leistungsjahr IS NULL",
                Integer.class))
        .isZero();
  }

  @Test
  void migration_thenTheLeistungsjahrIsMandatory() {
    // When
    flywayBis("latest").migrate();
    final String nullable =
        jdbc.queryForObject(
            "SELECT is_nullable FROM information_schema.columns WHERE table_schema = ?"
                + " AND table_name = 'angebot_position' AND column_name = 'leistungsjahr'",
            String.class,
            PROBE);

    // Then
    assertThat(nullable).isEqualTo("NO");
  }

  @Test
  void migration_thenAYearBeyond2099IsRejectedByTheCheck() {
    // Given — E10: „20267" ist ein Tippfehler und keine Angabe; die Grenze steht auch hier.
    flywayBis("latest").migrate();

    final Long kennung = Long.valueOf(angebot);

    // When / Then
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO "
                        + PROBE
                        + ".angebot_position (angebot_id, position, bezeichnung,"
                        + " abrechnungsmodus, menge, einheit, einzelpreis, leistungsjahr)"
                        + " VALUES (?, 1, 'Aufwände Mai 2027', 'AUFWAND', 135, 'STUNDE', 60.00,"
                        + " 2100)",
                    kennung))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("angebot_position_leistungsjahr");
  }
}
