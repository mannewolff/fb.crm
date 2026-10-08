package org.mwolff.fbcrm.angebot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Prueft {@code V20__angebot_intern.sql}: Das Angebot bekommt das Kennzeichen {@code intern} und
 * zwei Status fuer die interne Arbeit (Issue #226).
 *
 * <p>Wie in {@code AngebotStatusMigrationIT} migriert der Test ein eigenes Schema zuerst bis
 * Version 19, legt dort je ein Angebot in allen fuenf damals moeglichen Status an und migriert
 * danach zu Ende. Im Schema der Suite gibt es den Zustand vor V20 nicht mehr.
 *
 * <p>Gegenstand ist, dass die Migration nichts verschiebt: Jedes bestehende Angebot ist ein Angebot
 * an einen Kunden, traegt danach {@code intern = false} und behaelt seinen Status. Dazu, dass die
 * Spalte Pflicht ist und der erweiterte CHECK die zwei neuen Werte kennt.
 */
class AngebotInternMigrationIT extends AbstractIntegrationTest {

  private static final String PROBE = "migration_v20";

  private static final List<String> ALTE_STATUS =
      List.of("ANGELEGT", "ABGEGEBEN", "BESTELLT", "ERLEDIGT", "ABGERECHNET");

  private final JdbcTemplate jdbc;
  private final DataSource dataSource;

  private long firma;

  @Autowired
  AngebotInternMigrationIT(final JdbcTemplate jdbc, final DataSource dataSource) {
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
  void migriereBisNeunzehnUndLegeEineFirmaAn() {
    final Flyway bisNeunzehn = flywayBis("19");
    bisNeunzehn.clean();
    bisNeunzehn.migrate();
    jdbc.update("INSERT INTO " + PROBE + ".firma (name) VALUES ('Adler AG')");
    firma = jdbc.queryForObject("SELECT id FROM " + PROBE + ".firma", Long.class).longValue();
  }

  @AfterEach
  void raeumeDasSchemaAb() {
    flywayBis("latest").clean();
  }

  private void angebot(final String status) {
    jdbc.update(
        "INSERT INTO "
            + PROBE
            + ".angebot (firma_id, status, angebot_datum, leistungsbeschreibung)"
            + " VALUES (?, ?, DATE '2026-09-20', 'Konzeption')",
        Long.valueOf(firma),
        status);
  }

  private List<Map<String, Object>> zeilenNachMigration() {
    flywayBis("latest").migrate();
    return jdbc.queryForList("SELECT status, intern FROM " + PROBE + ".angebot ORDER BY id");
  }

  @Test
  void migration_givenAnAngebotInEveryOldStatus_thenEachIsACustomerOfferAndKeepsItsStatus() {
    // Given — alle fuenf Status, die es vor V20 gab.
    ALTE_STATUS.forEach(this::angebot);

    // When / Then — kein Status verschoben, und keine Zeile wird intern.
    assertThat(zeilenNachMigration())
        .extracting(zeile -> zeile.get("status"), zeile -> zeile.get("intern"))
        .containsExactly(
            tuple("ANGELEGT", false),
            tuple("ABGEGEBEN", false),
            tuple("BESTELLT", false),
            tuple("ERLEDIGT", false),
            tuple("ABGERECHNET", false));
  }

  @Test
  void migration_thenTheArtIsMandatory() {
    // When
    flywayBis("latest").migrate();
    final String nullable =
        jdbc.queryForObject(
            "SELECT is_nullable FROM information_schema.columns"
                + " WHERE table_schema = ? AND table_name = 'angebot' AND column_name = 'intern'",
            String.class,
            PROBE);

    // Then
    assertThat(nullable).isEqualTo("NO");
  }

  @Test
  void migration_thenTheStatusCheckKnowsBothStatusOfTheInternalWork() {
    // Given
    flywayBis("latest").migrate();

    // When — erst nach V20 laesst sich eine interne Zeile schreiben.
    final int betroffen =
        jdbc.update(
            "INSERT INTO "
                + PROBE
                + ".angebot (firma_id, status, intern, angebot_datum)"
                + " VALUES (?, 'ABGESCHLOSSEN', true, DATE '2026-09-20')",
            Long.valueOf(firma));

    // Then
    assertThat(betroffen).isEqualTo(1);
  }
}
