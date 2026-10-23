package org.mwolff.fbcrm.arbeitszeit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Prueft {@code V32__arbeitszeit_person.sql} am Bestand: Die Zeiteintraege, die es beim Update
 * schon gibt, gehoeren danach dem bisherigen Konto (Plan #426, E3; fachliche Quelle #410, Kriterium
 * 1).
 *
 * <p>Wie in {@code KundenMigrationIT} und {@code MehrereKontenMigrationIT} migriert der Test ein
 * eigenes Schema zuerst bis Version 31, legt dort den Bestand von damals an — Zeiteintraege ohne
 * Person — und migriert danach zu Ende. Im Schema der Suite gibt es den Zustand vor V32 nicht mehr.
 *
 * <p>Gegenstand sind die beiden Lagen, die E3 nennt: Ein Bestand mit Konto gibt jeden Eintrag dem
 * aeltesten Konto, und eine Installation ohne Konto hat keine Zeiteintraege — dort laeuft die
 * Migration ueber die leere Tabelle und setzt die Spalte trotzdem auf Pflicht.
 */
class ArbeitszeitMigrationIT extends AbstractIntegrationTest {

  private static final String PROBE = "migration_v32";

  private final JdbcTemplate jdbc;
  private final DataSource dataSource;

  @Autowired
  ArbeitszeitMigrationIT(final JdbcTemplate jdbc, final DataSource dataSource) {
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
  void migriereBisEinunddreissig() {
    final Flyway bisEinunddreissig = flywayBis("31");
    bisEinunddreissig.clean();
    bisEinunddreissig.migrate();
  }

  @AfterEach
  void raeumeDasSchemaAb() {
    flywayBis("latest").clean();
  }

  private long konto(final String email, final String name) {
    return jdbc.queryForObject(
            "INSERT INTO "
                + PROBE
                + ".account (email, display_name, role) VALUES (?, ?, 'C_LEVEL') RETURNING id",
            Long.class,
            email,
            name)
        .longValue();
  }

  /**
   * Legt Firma, Angebot und eine Position an und darauf zwei Eintraege; ohne Person, wie vor V32.
   */
  private void zweiZeiteintraege() {
    final Long firmaId =
        jdbc.queryForObject(
            "INSERT INTO " + PROBE + ".firma (name) VALUES ('IT Bildungshaus') RETURNING id",
            Long.class);
    final Long angebotId =
        jdbc.queryForObject(
            "INSERT INTO "
                + PROBE
                + ".angebot (firma_id, status, angebot_datum)"
                + " VALUES (?, 'BESTELLT', DATE '2026-10-01') RETURNING id",
            Long.class,
            firmaId);
    final Long positionId =
        jdbc.queryForObject(
            "INSERT INTO "
                + PROBE
                + ".angebot_position"
                + " (angebot_id, position, bezeichnung, abrechnungsmodus, menge, einheit,"
                + " einzelpreis, leistungsjahr)"
                + " VALUES (?, 1, 'Konzeption', 'AUFWAND', 20, 'STUNDE', 120.00, 2026)"
                + " RETURNING id",
            Long.class,
            angebotId);
    jdbc.update(
        "INSERT INTO "
            + PROBE
            + ".arbeitszeit (angebot_position_id, tag, von, bis)"
            + " VALUES (?, DATE '2026-10-05', TIME '09:00', TIME '12:00'),"
            + " (?, DATE '2026-10-06', TIME '13:00', TIME '15:00')",
        positionId,
        positionId);
  }

  private List<Long> personenDerEintraege() {
    return jdbc.queryForList(
        "SELECT account_id FROM " + PROBE + ".arbeitszeit ORDER BY id", Long.class);
  }

  private String pflichtangabe() {
    return jdbc.queryForObject(
        "SELECT is_nullable FROM information_schema.columns WHERE table_schema = ?"
            + " AND table_name = 'arbeitszeit' AND column_name = 'account_id'",
        String.class,
        PROBE);
  }

  @Test
  void migration_givenEntriesAndTheFormerAccount_thenEveryEntryBelongsToTheOldestAccount() {
    // Given — Kriterium 1 von #410: das bisherige Konto ist das aelteste; ein spaeter angelegtes
    // zweites Konto bekommt nichts.
    final long bisheriges = konto("manne@example.org", "Manne");
    konto("anna@firma.de", "Anna");
    zweiZeiteintraege();

    // When
    flywayBis("latest").migrate();

    // Then
    assertThat(personenDerEintraege()).containsExactly(bisheriges, bisheriges);
    assertThat(pflichtangabe()).isEqualTo("NO");
  }

  @Test
  void migration_givenAnEmptyTableAndNoAccount_thenRunsThroughAndTheColumnIsMandatory() {
    // Given — E3: ohne Konto gibt es keine Zeiteintraege; der UPDATE trifft keine Zeile.

    // When
    flywayBis("latest").migrate();

    // Then
    assertThat(personenDerEintraege()).isEmpty();
    assertThat(pflichtangabe()).isEqualTo("NO");
  }
}
