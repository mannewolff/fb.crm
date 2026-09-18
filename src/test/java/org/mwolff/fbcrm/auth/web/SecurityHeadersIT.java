package org.mwolff.fbcrm.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.PasswordHasher;
import org.mwolff.fbcrm.auth.domain.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Die Kopfzeilen, die jede Antwort tragen muss (K9, CLAUDE-security.md).
 *
 * <p>{@code Cache-Control: no-store} traegt den Zurueck-Knopf aus K9: Ohne ihn zeigte der Browser
 * nach dem Abmelden die zuletzt gesehene Seite aus seinem Zwischenspeicher. {@code nosniff} und
 * {@code X-Frame-Options: DENY} sind die Spring-Security-Defaults — dass sie scharf bleiben, ist
 * eine Zusage, die geprueft gehoert und nicht nur angenommen.
 */
class SecurityHeadersIT extends AbstractIntegrationTest {

  private static final String MAIL = "manne@example.org";
  private static final String PASSWORT = "richtiges-passwort";

  private final TestRestTemplate rest;
  private final AccountRepository accounts;
  private final PasswordHasher hasher;
  private final JdbcTemplate jdbc;

  @Autowired
  SecurityHeadersIT(
      final TestRestTemplate rest,
      final AccountRepository accounts,
      final PasswordHasher hasher,
      final JdbcTemplate jdbc) {
    this.rest = rest;
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

  private ResponseEntity<String> authAntwort() {
    return rest.postForEntity(
        "/api/auth/login", Map.of("email", MAIL, "password", PASSWORT), String.class);
  }

  private ResponseEntity<String> apiAntwort() {
    return rest.getForEntity("/api/auth/me", String.class);
  }

  @Test
  void authResponse_thenForbidsAnyCaching() {
    // When
    final HttpHeaders kopf = authAntwort().getHeaders();

    // Then
    assertThat(kopf.getFirst(HttpHeaders.CACHE_CONTROL)).contains("no-store");
  }

  @Test
  void apiResponse_thenForbidsAnyCaching() {
    // When
    final HttpHeaders kopf = apiAntwort().getHeaders();

    // Then
    assertThat(kopf.getFirst(HttpHeaders.CACHE_CONTROL)).contains("no-store");
  }

  @Test
  void authResponse_thenForbidsMimeSniffing() {
    // When
    final HttpHeaders kopf = authAntwort().getHeaders();

    // Then
    assertThat(kopf.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
  }

  @Test
  void authResponse_thenForbidsFraming() {
    // When
    final HttpHeaders kopf = authAntwort().getHeaders();

    // Then
    assertThat(kopf.getFirst("X-Frame-Options")).isEqualTo("DENY");
  }

  @Test
  void apiResponse_thenForbidsMimeSniffing() {
    // When
    final HttpHeaders kopf = apiAntwort().getHeaders();

    // Then
    assertThat(kopf.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
  }

  @Test
  void apiResponse_thenForbidsFraming() {
    // When
    final HttpHeaders kopf = apiAntwort().getHeaders();

    // Then
    assertThat(kopf.getFirst("X-Frame-Options")).isEqualTo("DENY");
  }
}
