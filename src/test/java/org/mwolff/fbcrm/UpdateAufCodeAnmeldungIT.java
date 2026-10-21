package org.mwolff.fbcrm;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.auth.AuthProperties;
import org.mwolff.fbcrm.auth.Konten;
import org.mwolff.fbcrm.auth.domain.SessionTokenDecoding;
import org.mwolff.fbcrm.auth.domain.SessionTokens;
import org.mwolff.fbcrm.auth.infrastructure.SessionTokenCodec;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Das Update von der Passwort-Version auf die Anmeldung per Code (Kriterium 8, E18).
 *
 * <p>Der Test erbt bewusst <b>nicht</b> von {@link AbstractIntegrationTest}: Dort migriert der
 * gemeinsame Anwendungskontext die geteilte Datenbank beim Hochfahren auf den Kopf, und einen Stand
 * vor {@code V29} gaebe es nicht mehr. Stattdessen faehrt er auf demselben Container ein eigenes
 * Schema hoch, fuellt es auf dem Stand von {@code V28} wie eine Instanz der bisherigen Version,
 * migriert mit einem zweiten Flyway-Lauf auf {@code V29} und wirft das Schema danach weg.
 *
 * <p>Die alte Sitzung pruefen Token und Konto so, wie es {@code SessionAuthenticationFilter} tut:
 * Die Signatur gilt noch, aber die Generation des Tokens passt nicht mehr zum Konto.
 */
class UpdateAufCodeAnmeldungIT {

  private static final String SCHEMA = "update_it";
  private static final String MAIL = "manne@example.org";
  private static final String NAME = "Manne";
  private static final String FIRMA = "Adler AG";
  private static final Instant JETZT = Instant.parse("2026-10-20T10:00:00Z");

  private final PostgreSQLContainer<?> postgres = AbstractIntegrationTest.datenbank();
  private final DriverManagerDataSource quelle =
      new DriverManagerDataSource(
          mitSchema(postgres.getJdbcUrl()), postgres.getUsername(), postgres.getPassword());
  private final JdbcTemplate jdbc = new JdbcTemplate(quelle);

  /** Dieselbe Datenbank, aber mit {@value #SCHEMA} als Suchpfad der Verbindung. */
  private static String mitSchema(final String url) {
    return url + (url.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA;
  }

  private Flyway flyway(final String ziel) {
    return Flyway.configure()
        .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .schemas(SCHEMA)
        .locations("classpath:db/migration")
        .target(ziel)
        .load();
  }

  private static SessionTokens codec() {
    return new SessionTokenCodec(
        new AuthProperties(
            "geheimnis-mit-mindestens-32-zeichen-laenge",
            Duration.ofDays(30),
            "fbcrm_session",
            true,
            10,
            Duration.ofMinutes(15),
            5,
            Duration.ofHours(1),
            10,
            Duration.ofHours(24),
            Duration.ofMinutes(10),
            5,
            List.of()),
        Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  @BeforeEach
  void frischesSchemaAufDemStandDerPasswortVersion() {
    jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    flyway("28").migrate();
  }

  @AfterEach
  void wirfDasSchemaWeg() {
    jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
  }

  private long spaltenPasswort() {
    return Objects.requireNonNull(
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.columns WHERE table_schema = ?"
                + " AND table_name = 'account' AND column_name = 'password_hash'",
            Long.class,
            SCHEMA));
  }

  @Test
  @DisplayName("Szenario: Update von der Passwort-Version")
  void szenarioUpdateVonDerPasswortVersion_thenSessionsEndedAndDataKept() {
    // Given — angemeldet in der bisherigen Version: Konto mit Passwort-Hash, eine Firma und ein
    // Session-Token auf die Generation des Kontos.
    jdbc.update(
        "INSERT INTO account (email, display_name, password_hash, role) VALUES (?, ?, ?, 'ADMIN')",
        MAIL,
        NAME,
        "$argon2id$v=19$m=16384,t=2,p=1$salz$hash-des-alten-passworts");
    jdbc.update("INSERT INTO firma (name) VALUES (?)", FIRMA);
    final Map<String, Object> vorher =
        jdbc.queryForMap("SELECT id, session_generation FROM account WHERE email = ?", MAIL);
    final long kontoId = ((Number) vorher.get("id")).longValue();
    final int alteGeneration = ((Number) vorher.get("session_generation")).intValue();
    final SessionTokens tokens = codec();
    final String altesToken = tokens.encode(kontoId, alteGeneration, JETZT, Duration.ofDays(30));
    assertThat(spaltenPasswort()).isEqualTo(1);

    // When — der Betreiber aktualisiert auf die neue Version.
    flyway("29").migrate();

    // Then — die bisherige Sitzung ist beendet: Die Signatur gilt, die Generation nicht mehr.
    final Map<String, Object> nachher =
        jdbc.queryForMap(
            "SELECT email, display_name, session_generation FROM account WHERE id = ?", kontoId);
    final int neueGeneration = ((Number) nachher.get("session_generation")).intValue();
    assertThat(tokens.decode(altesToken))
        .isInstanceOfSatisfying(
            SessionTokenDecoding.Valid.class,
            gueltig ->
                assertThat(
                        Konten.gespeichert(kontoId, MAIL, neueGeneration, JETZT)
                            .matchesGeneration(gueltig.sessionGeneration()))
                    .isFalse());

    // Und — die Spalte ist fort, Firma, Adresse und Anzeigename sind unveraendert.
    assertThat(spaltenPasswort()).isZero();
    assertThat(nachher).containsEntry("email", MAIL).containsEntry("display_name", NAME);
    assertThat(jdbc.queryForList("SELECT name FROM firma", String.class)).containsExactly(FIRMA);
  }

  @Test
  void update_givenTheNewSchema_thenALoginCodeCanBeStoredForTheAccount() {
    // Given
    jdbc.update(
        "INSERT INTO account (email, display_name, password_hash, role) VALUES (?, ?, ?, 'ADMIN')",
        MAIL,
        NAME,
        "$argon2id$alt");

    // When
    flyway("29").migrate();

    // Then — die naechste Anmeldung laeuft per Code: Die Tabelle dafuer steht bereit.
    jdbc.update(
        "INSERT INTO login_code (email, code_hash, expires_at)"
            + " VALUES (?, ?, now() + interval '10 minutes')",
        MAIL,
        "$argon2id$code");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM login_code", Long.class)).isEqualTo(1L);
  }
}
