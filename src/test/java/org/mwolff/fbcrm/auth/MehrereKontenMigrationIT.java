package org.mwolff.fbcrm.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
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
 * Prueft {@code V30__mehrere_konten_mit_rollen.sql} am Bestand: Das bisherige Konto wird C-Level
 * (Issue #417; Plan #414, A3 und A4; fachliche Quelle #409, Kriterium 2).
 *
 * <p>Wie in {@code AngebotStornoMigrationIT} migriert der Test ein eigenes Schema zuerst bis
 * Version 29, legt dort das Konto an, wie es vor dem Update im Bestand steht — mit der Rolle {@code
 * ADMIN} —, und migriert danach zu Ende. Im Schema der Suite gibt es den Zustand vor V30 nicht
 * mehr.
 *
 * <p>Gegenstand ist, dass die Migration das Konto behaelt und nur umbenennt: dieselbe Adresse,
 * derselbe Name, die Rolle {@code C_LEVEL}, aktiv, und eine um eins erhoehte Sitzungs-Generation,
 * die jede laufende Sitzung einmal beendet. Dazu, dass der ersetzte CHECK den alten Namen danach
 * abweist.
 */
class MehrereKontenMigrationIT extends AbstractIntegrationTest {

  private static final String PROBE = "migration_v30";
  private static final String ADRESSE = "manne@example.org";
  private static final int GENERATION_VORHER = 3;

  private final JdbcTemplate jdbc;
  private final DataSource dataSource;

  @Autowired
  MehrereKontenMigrationIT(final JdbcTemplate jdbc, final DataSource dataSource) {
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
  void migriereBisNeunundzwanzigUndLegeDasBisherigeKontoAn() {
    final Flyway bisNeunundzwanzig = flywayBis("29");
    bisNeunundzwanzig.clean();
    bisNeunundzwanzig.migrate();
    jdbc.update(
        "INSERT INTO "
            + PROBE
            + ".account (email, display_name, role, session_generation)"
            + " VALUES (?, 'Manne', 'ADMIN', ?)",
        ADRESSE,
        Integer.valueOf(GENERATION_VORHER));
  }

  @AfterEach
  void raeumeDasSchemaAb() {
    flywayBis("latest").clean();
  }

  private Map<String, Object> konto() {
    return jdbc.queryForMap(
        "SELECT email, display_name, role, aktiv, session_generation FROM "
            + PROBE
            + ".account WHERE email = ?",
        ADRESSE);
  }

  @Test
  void migration_givenTheFormerAdmin_thenItIsAnActiveCLevelWithItsData() {
    // When
    flywayBis("latest").migrate();

    // Then — das bisherige Konto ist C-Level, alles andere bleibt (Kriterium 2 von #409).
    assertThat(konto())
        .containsEntry("email", ADRESSE)
        .containsEntry("display_name", "Manne")
        .containsEntry("role", "C_LEVEL")
        .containsEntry("aktiv", true);
  }

  @Test
  void migration_givenTheFormerAdmin_thenEndsEveryRunningSessionOnce() {
    // When
    flywayBis("latest").migrate();

    // Then — jedes vorher ausgestellte Session-Token traegt danach eine veraltete Generation.
    assertThat(konto()).containsEntry("session_generation", GENERATION_VORHER + 1);
  }

  @Test
  void migration_thenTheFormerRoleNameIsRejected() {
    // Given
    flywayBis("latest").migrate();

    // When / Then — A4: der ersetzte CHECK laesst ADMIN nicht wieder entstehen.
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO "
                        + PROBE
                        + ".account (email, display_name, role) VALUES (?, 'Alt', 'ADMIN')",
                    "alt@example.org"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("account_role_known");
  }

  @Test
  void migration_thenASecondCLevelBesideTheFormerAdminIsAccepted() {
    // Given
    flywayBis("latest").migrate();

    // When — der Index account_single_admin ist gestrichen.
    final int betroffen =
        jdbc.update(
            "INSERT INTO "
                + PROBE
                + ".account (email, display_name, role) VALUES (?, 'Anna', 'C_LEVEL')",
            "anna@firma.de");

    // Then
    assertThat(betroffen).isEqualTo(1);
  }
}
