package org.mwolff.fbcrm;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.auth.application.LoginResult;
import org.mwolff.fbcrm.auth.application.LoginUseCase;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.PasswordHasher;
import org.mwolff.fbcrm.auth.domain.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * K2: Nach einem Neustart meldet sich derselbe Admin mit denselben Daten an.
 *
 * <p>Der Neustart ist echt — es entsteht ein zweiter Anwendungskontext mit eigenem Tomcat gegen
 * dieselbe Datenbank. Nur so ist belegt, dass nichts am Konto im Speicher haengt: Ein zweiter Test
 * im selben Kontext bewiese allein, dass die Datenbank ein SELECT beantwortet.
 */
class AccountPersistenceIT extends AbstractIntegrationTest {

  private static final String MAIL = "manne@example.org";
  private static final String PASSWORT = "richtiges-passwort";
  private static final String IP = "203.0.113.7";
  private static final String NEUES_GEHEIMNIS = "ein-zweites-geheimnis-mit-genug-zeichen-drin";

  private final AccountRepository accounts;
  private final PasswordHasher hasher;
  private final JdbcTemplate jdbc;

  @Autowired
  AccountPersistenceIT(
      final AccountRepository accounts, final PasswordHasher hasher, final JdbcTemplate jdbc) {
    this.accounts = accounts;
    this.hasher = hasher;
    this.jdbc = jdbc;
  }

  @BeforeEach
  void legeDasAdminKontoAn() {
    jdbc.execute("TRUNCATE outbox_message, password_reset_token, account RESTART IDENTITY CASCADE");
    final Instant jetzt = Instant.parse("2026-09-18T10:00:00Z");
    accounts.save(
        new Account(null, MAIL, "Manne", hasher.hash(PASSWORT), Role.ADMIN, 0, jetzt, jetzt));
  }

  /**
   * Eine zweite Instanz gegen dieselbe Datenbank.
   *
   * <p>Die Werte gehen als Kommandozeilenargumente hinein und nicht als {@code properties(...)}:
   * Letztere sind Vorgabewerte und stehen in der Rangfolge <b>unter</b> der {@code application.yml}
   * — die Instanz verbaende sich dann mit {@code localhost:5432}.
   */
  private static ConfigurableApplicationContext neueInstanz() {
    return new SpringApplicationBuilder(FbCrmApplication.class)
        .run(
            "--server.port=0",
            "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
            "--spring.datasource.username=" + POSTGRES.getUsername(),
            "--spring.datasource.password=" + POSTGRES.getPassword(),
            "--fbcrm.auth.session-secret=" + NEUES_GEHEIMNIS);
  }

  @Test
  void login_afterRestartingTheInstance_thenTheSameAccountSignsIn() {
    // When
    try (ConfigurableApplicationContext neu = neueInstanz()) {
      final LoginResult ergebnis = neu.getBean(LoginUseCase.class).login(MAIL, PASSWORT, IP);

      // Then
      assertThat(ergebnis.account().email()).isEqualTo(MAIL);
    }
  }

  @Test
  void login_afterRestartingTheInstance_thenTheDisplayNameSurvived() {
    // When
    try (ConfigurableApplicationContext neu = neueInstanz()) {
      final LoginResult ergebnis = neu.getBean(LoginUseCase.class).login(MAIL, PASSWORT, IP);

      // Then
      assertThat(ergebnis.account().displayName()).isEqualTo("Manne");
    }
  }
}
