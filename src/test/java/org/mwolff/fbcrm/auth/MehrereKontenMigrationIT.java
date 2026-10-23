package org.mwolff.fbcrm.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.auth.domain.SecretHasher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

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
 *
 * <p>Dazu {@code V31__verfasser_am_kommentar.sql} (Issue #420; Plan #414, E7, E13): Die vorhandenen
 * Kommentare gehoeren danach dem bisherigen Konto. Ein Bestand mit Kommentaren und ohne Konto
 * laeuft durch, und die Einrichtung gibt seine Kommentare dem ersten Konto. Diesen letzten Schritt
 * fuehrt der Test ueber den echten Weg {@code POST /api/setup} — der Anwendungskontext spricht das
 * Schema der Suite, nicht das eigene der Migration. Darum stellt er dort genau den Stand her, den
 * V31 an einem solchen Bestand hinterlaesst; die Schalter der Einrichtung sind dieselben wie in
 * {@code EinrichtungOhnePasswortIT}, damit beide denselben Kontext teilen.
 */
@TestPropertySource(
    properties = {
      "fbcrm.setup.bootstrap-token=" + MehrereKontenMigrationIT.SCHLUESSEL,
      "fbcrm.mail.enabled=true",
      "fbcrm.auth.login-max-attempts=1000"
    })
class MehrereKontenMigrationIT extends AbstractIntegrationTest {

  /** Derselbe Wert wie {@code SetupControllerIT.SCHLUESSEL}, damit der Kontext geteilt wird. */
  static final String SCHLUESSEL = "einmal-schluessel-der-einrichtung";

  private static final String PROBE = "migration_v30";
  private static final String ADRESSE = "manne@example.org";
  private static final int GENERATION_VORHER = 3;

  private final JdbcTemplate jdbc;
  private final DataSource dataSource;
  private final TestRestTemplate rest;
  private final SecretHasher hasher;
  private final TransactionTemplate transaktion;

  @Autowired
  MehrereKontenMigrationIT(
      final JdbcTemplate jdbc,
      final DataSource dataSource,
      final TestRestTemplate rest,
      final SecretHasher hasher,
      final TransactionTemplate transaktion) {
    this.jdbc = jdbc;
    this.dataSource = dataSource;
    this.rest = rest;
    this.hasher = hasher;
    this.transaktion = transaktion;
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

  /** Legt in einem Schema Firma, Angebot und zwei Kommentare an; ohne Verfasser, wie vor V31. */
  private void zweiKommentareIn(final String schema) {
    final Long firmaId =
        jdbc.queryForObject(
            "INSERT INTO " + schema + ".firma (name) VALUES ('Adler AG') RETURNING id", Long.class);
    final Long angebotId =
        jdbc.queryForObject(
            "INSERT INTO "
                + schema
                + ".angebot (firma_id, status, angebot_datum)"
                + " VALUES (?, 'ANGELEGT', DATE '2026-09-20') RETURNING id",
            Long.class,
            firmaId);
    jdbc.update(
        "INSERT INTO "
            + schema
            + ".angebot_kommentar (angebot_id, text) VALUES (?, 'Erster'), (?, 'Zweiter')",
        angebotId,
        angebotId);
  }

  private List<Long> verfasserIn(final String schema) {
    return jdbc.queryForList(
        "SELECT verfasser_id FROM " + schema + ".angebot_kommentar ORDER BY id", Long.class);
  }

  @Test
  void migration_givenTheFormerAccountAndTwoKommentare_thenBothBelongToIt() {
    // Given — E7: bis V31 gibt es genau ein Konto, und ihm gehoeren die Kommentare.
    zweiKommentareIn(PROBE);
    final Long bisherigesKonto =
        jdbc.queryForObject(
            "SELECT id FROM " + PROBE + ".account WHERE email = ?", Long.class, ADRESSE);

    // When
    flywayBis("latest").migrate();

    // Then
    assertThat(verfasserIn(PROBE)).containsExactly(bisherigesKonto, bisherigesKonto);
  }

  @Test
  void migration_givenTwoKommentareAndNoAccount_thenRunsThroughAndLeavesThemWithoutVerfasser() {
    // Given — E13: der Altbestand nach dem frueheren Rueckweg „verlorenes Postfach".
    jdbc.update("DELETE FROM " + PROBE + ".account");
    zweiKommentareIn(PROBE);

    // When
    flywayBis("latest").migrate();

    // Then — die Migration laeuft durch; die Kommentare uebernimmt erst die Einrichtung.
    assertThat(verfasserIn(PROBE)).containsExactly(null, null);
  }

  @Test
  void einrichtung_givenTwoKommentareWithoutVerfasser_thenBothBelongToTheNewAccount() {
    // Given — im Schema der Suite der Stand, den V31 an einem Bestand ohne Konto hinterlaesst:
    // zwei Kommentare ohne Verfasser und der CHECK als NOT VALID. Nur so entstehen die Zeilen,
    // denn der CHECK bindet jeden neuen Kommentar; alles in einer Transaktion, damit der Stand
    // ohne CHECK nie sichtbar wird.
    jdbc.execute(
        "TRUNCATE outbox_message, login_code, account, angebot_kommentar, angebot_position,"
            + " angebot, ansprechpartner, firma RESTART IDENTITY CASCADE");
    transaktion.executeWithoutResult(
        status -> {
          jdbc.execute("ALTER TABLE angebot_kommentar DROP CONSTRAINT angebot_kommentar_verfasser");
          zweiKommentareIn("public");
          jdbc.execute(
              "ALTER TABLE angebot_kommentar ADD CONSTRAINT angebot_kommentar_verfasser"
                  + " CHECK (verfasser_id IS NOT NULL) NOT VALID");
        });
    assertThat(verfasserIn("public")).containsExactly(null, null);

    // When — die Einrichtung ueber den echten Weg.
    final ResponseEntity<String> antwort =
        rest.postForEntity(
            "/api/setup",
            Map.of(
                "bootstrapToken",
                SCHLUESSEL,
                "displayName",
                "Manne",
                "email",
                ADRESSE,
                "code",
                Anmeldung.hinterlegeCode(jdbc, hasher, ADRESSE)),
            String.class);

    // Then — beide Kommentare gehoeren dem neuen Konto (E13).
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
    final Long neuesKonto =
        jdbc.queryForObject("SELECT id FROM account WHERE email = ?", Long.class, ADRESSE);
    assertThat(verfasserIn("public")).containsExactly(neuesKonto, neuesKonto);
  }
}
