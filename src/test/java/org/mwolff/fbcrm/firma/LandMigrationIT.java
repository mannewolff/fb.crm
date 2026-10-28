package org.mwolff.fbcrm.firma;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Prueft {@code db.migration.LandAlsKuerzelMigration} am Bestand: Das Land jeder Firma wird vom
 * Freitext zum Kuerzel (Plan #461, E11, E12; fachliche Quelle #460, Kriterium 10).
 *
 * <p>Wie in {@code ArbeitszeitMigrationIT} migriert der Test ein eigenes Schema zuerst bis Version
 * 35, legt dort den Bestand von damals an — Firmen mit dem Land als Freitext — und migriert danach
 * bis 36. Im Schema der Suite gibt es den Zustand vor V36 nicht mehr.
 */
class LandMigrationIT extends AbstractIntegrationTest {

  private static final String PROBE = "migration_v36";

  private final JdbcTemplate jdbc;
  private final DataSource dataSource;

  @Autowired
  LandMigrationIT(final JdbcTemplate jdbc, final DataSource dataSource) {
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
  void migriereBisFuenfunddreissig() {
    final Flyway bisFuenfunddreissig = flywayBis("35");
    bisFuenfunddreissig.clean();
    bisFuenfunddreissig.migrate();
  }

  @AfterEach
  void raeumeDasSchemaAb() {
    flywayBis("latest").clean();
  }

  private void firma(final String name, final String land) {
    jdbc.update("INSERT INTO " + PROBE + ".firma (name, land) VALUES (?, ?)", name, land);
  }

  private List<String> laender() {
    return jdbc.queryForList("SELECT land FROM " + PROBE + ".firma ORDER BY id", String.class);
  }

  @Test
  @DisplayName("Szenario: Das Land eines bestehenden Kunden wird umgestellt")
  void szenarioDasLandEinesBestehendenKundenWirdUmgestellt() {
    // Given — Kriterium 10: ein englischer Name in Kleinschreibung, ein Tippfehler, ein Kuerzel
    // mit Leerraum, der englische Name Deutschlands und ein leeres Land.
    firma("Wiener Werkstaetten", "austria");
    firma("Grazer Glas", "Ostereich");
    firma("Adler AG", " DE ");
    firma("Bremer Bau", "Germany");
    firma("Ohne Land", "");

    // When
    flywayBis("36").migrate();

    // Then
    assertThat(laender()).containsExactly("AT", "DE", "DE", "DE", "DE");
    // Ein Freitext kommt nicht mehr hinein. Postgres prueft die Laenge vor dem CHECK; dass der
    // CHECK selbst greift, zeigt migration_givenNoFirm_thenTheCheckRejectsALowercaseCode.
    assertThatThrownBy(() -> firma("Freitext", "Deutschland"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void migration_givenAFirmWithoutAnyLand_thenGermanyAndTheColumnIsMandatory() {
    // Given — vor V36 durfte das Land fehlen.
    jdbc.update("INSERT INTO " + PROBE + ".firma (name) VALUES ('Nur Name')");

    // When
    flywayBis("36").migrate();

    // Then — die Vorgabe greift auch fuer neue Zeilen ohne Land.
    jdbc.update("INSERT INTO " + PROBE + ".firma (name) VALUES ('Neu')");
    assertThat(laender()).containsExactly("DE", "DE");
    assertThat(
            jdbc.queryForObject(
                "SELECT is_nullable FROM information_schema.columns WHERE table_schema = ?"
                    + " AND table_name = 'firma' AND column_name = 'land'",
                String.class,
                PROBE))
        .isEqualTo("NO");
  }

  @Test
  void migration_givenNoFirm_thenTheCheckRejectsALowercaseCode() {
    // When
    flywayBis("36").migrate();

    // Then — die Migration laeuft ueber die leere Tabelle, und der CHECK verlangt Grossbuchstaben.
    assertThat(laender()).isEmpty();
    assertThatThrownBy(() -> firma("Klein", "de"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("firma_land_kuerzel");
  }
}
