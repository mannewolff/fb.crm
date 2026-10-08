package org.mwolff.fbcrm.rechnung;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Prueft {@code V17__rechnung_nummernkreis.sql}: Die naechste Nummer zieht aus den Einstellungen in
 * den Nummernkreis um (Issue #175, Plan #169, E4).
 *
 * <p>Wie {@code AngebotStatusMigrationIT} migriert der Test ein eigenes Schema zuerst bis Version
 * 16, setzt dort Muster und Wert von damals und migriert danach zu Ende. Im Schema der Suite gibt
 * es den Zustand vor V17 nicht mehr.
 *
 * <p>Gegenstand ist, dass der bisherige Wert nicht verloren geht und im <b>richtigen</b> Kreis
 * landet: unter dem laufenden Jahr, wenn das Muster ein Jahr traegt, sonst unter der 0. Das
 * erwartete Jahr liest der Test mit demselben Ausdruck aus der Datenbank, mit dem die Migration es
 * rechnet — die Zeitzone der Datenbanksitzung darf hier nichts entscheiden.
 */
class RechnungNummernkreisMigrationIT extends AbstractIntegrationTest {

  private static final String PROBE = "migration_v17";
  private static final String VOR_DER_MIGRATION = "16";
  private static final int WERT = 4;

  private final JdbcTemplate jdbc;
  private final DataSource dataSource;

  @Autowired
  RechnungNummernkreisMigrationIT(final JdbcTemplate jdbc, final DataSource dataSource) {
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
  void migriereBisVorDieMigration() {
    final Flyway davor = flywayBis(VOR_DER_MIGRATION);
    davor.clean();
    davor.migrate();
  }

  @AfterEach
  void raeumeDasSchemaAb() {
    flywayBis("latest").clean();
  }

  /** Muster und naechste Nummer, wie sie vor V17 an den Einstellungen standen. */
  private void einstellungenVonDamals(final String muster) {
    jdbc.update(
        "UPDATE " + PROBE + ".rechnung_einstellungen SET nummer_muster = ?, naechste_nummer = ?",
        muster,
        Integer.valueOf(WERT));
  }

  /** Das laufende Jahr, mit demselben Ausdruck gerechnet wie in der Migration. */
  private int laufendesJahrDerGeschaeftszone() {
    return jdbc.queryForObject(
            "SELECT EXTRACT(YEAR FROM (now() AT TIME ZONE 'Europe/Berlin'))::integer",
            Integer.class)
        .intValue();
  }

  private @Nullable Integer zaehlerNachMigration(final int zaehlerjahr) {
    flywayBis("latest").migrate();
    final List<Integer> stand =
        jdbc.queryForList(
            "SELECT naechste_nummer FROM " + PROBE + ".rechnung_nummernkreis WHERE jahr = ?",
            Integer.class,
            Integer.valueOf(zaehlerjahr));
    return stand.isEmpty() ? null : stand.get(0);
  }

  @ParameterizedTest
  @ValueSource(strings = {"{NNNN}-{JJJJ}", "R{JJ}-{NNNN}"})
  void migration_givenAPatternWithAYear_thenTheValueCountsUnderTheCurrentYear(final String muster) {
    // Given — beide Jahresformen zaehlen je Jahr.
    einstellungenVonDamals(muster);
    final int jahr = laufendesJahrDerGeschaeftszone();

    // When / Then
    assertThat(zaehlerNachMigration(jahr)).isEqualTo(WERT);
    assertThat(zaehlerNachMigration(0)).isNull();
  }

  @Test
  void migration_givenAPatternWithoutAYear_thenTheValueCountsUnderZero() {
    // Given — ohne Jahres-Platzhalter laeuft ein einziger Kreis durch.
    einstellungenVonDamals("{NNNN}");

    // When / Then
    assertThat(zaehlerNachMigration(0)).isEqualTo(WERT);
    assertThat(zaehlerNachMigration(laufendesJahrDerGeschaeftszone())).isNull();
  }

  @Test
  void migration_thenTheColumnOfTheSettingsIsGone() {
    // When
    flywayBis("latest").migrate();
    final Integer spalten =
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.columns"
                + " WHERE table_schema = ? AND table_name = 'rechnung_einstellungen'"
                + " AND column_name = 'naechste_nummer'",
            Integer.class,
            PROBE);

    // Then — die naechste Nummer steht ab jetzt nur noch im Nummernkreis.
    assertThat(spalten).isZero();
  }
}
