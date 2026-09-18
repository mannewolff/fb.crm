package org.mwolff.fbcrm.instance.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Der Versionsstand der laufenden Instanz ueber HTTP (K11, E10).
 *
 * <p>Zwei Nachweise tragen das Paket. Erstens der Zugang: Die Auskunft verlangt eine Sitzung —
 * deshalb traegt die Auth-Platte vor der Anmeldung keine Version, und deshalb erfaehrt kein Scanner
 * im Netz, welcher Stand hier laeuft. Zweitens die Herkunft: Was der Endpunkt sagt, muss die
 * Version des Maven-Laufs sein und damit die aus {@code VERSION}. Ein Wert, der irgendwo anders
 * herkommt, saehe nach einem Hot-Deploy richtig aus und waere falsch.
 */
class InstanceControllerIT extends AbstractIntegrationTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  private static final String MAIL = "manne@example.org";
  private static final String PASSWORT = "richtiges-passwort";

  private final TestRestTemplate rest;
  private final AccountRepository accounts;
  private final PasswordHasher hasher;
  private final JdbcTemplate jdbc;

  @Autowired
  InstanceControllerIT(
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
  void legeDasKontoAn() {
    jdbc.execute("TRUNCATE outbox_message, password_reset_token, account RESTART IDENTITY CASCADE");
    final Instant jetzt = Instant.parse("2026-09-18T10:00:00Z");
    accounts.save(
        new Account(null, MAIL, "Manne", hasher.hash(PASSWORT), Role.ADMIN, 0, jetzt, jetzt));
  }

  private String sessionCookie() {
    final ResponseEntity<String> anmeldung =
        rest.postForEntity(
            "/api/auth/login", Map.of("email", MAIL, "password", PASSWORT), String.class);
    final String gesetzt = String.valueOf(anmeldung.getHeaders().getFirst(HttpHeaders.SET_COOKIE));
    return gesetzt.substring(0, gesetzt.indexOf(';'));
  }

  private ResponseEntity<String> mitSitzung() {
    final HttpHeaders kopf = new HttpHeaders();
    kopf.add(HttpHeaders.COOKIE, sessionCookie());
    return rest.exchange("/api/instance", HttpMethod.GET, new HttpEntity<>(kopf), String.class);
  }

  private ResponseEntity<String> ohneSitzung() {
    return rest.getForEntity("/api/instance", String.class);
  }

  @Test
  void instance_withoutASession_thenAnswersUnauthorized() {
    // When — E10: der Versionsstand ist keine oeffentliche Auskunft.
    final ResponseEntity<String> antwort = ohneSitzung();

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void instance_withoutASession_thenLeaksNothingInTheBody() {
    // When
    final ResponseEntity<String> antwort = ohneSitzung();

    // Then — der abgewiesene Aufruf traegt gar keinen Rumpf, erst recht keine Version.
    assertThat(String.valueOf(antwort.getBody())).doesNotContain("version");
  }

  @Test
  void instance_givenASession_thenAnswersOk() {
    // When
    final ResponseEntity<String> antwort = mitSitzung();

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void instance_givenASession_thenAnswersWithTheVersionOfTheMavenBuild() throws Exception {
    // Given — Quelle der Wahrheit ist VERSION; pom.xml zieht daran (VersionConsistencyTest).
    final String erwartet = Files.readString(Path.of("VERSION"), StandardCharsets.UTF_8).strip();

    // When
    final Map<String, Object> rumpf = rumpf();

    // Then — K11: was an der Marke steht, ist der Stand, der wirklich laeuft.
    assertThat(rumpf).containsEntry("version", erwartet);
  }

  @Test
  void instance_givenASession_thenCarriesExactlyOneField() throws Exception {
    // When
    final Map<String, Object> rumpf = rumpf();

    // Then — die Schiene braucht die Version, sonst nichts.
    assertThat(rumpf).containsOnlyKeys("version");
  }

  private Map<String, Object> rumpf() throws Exception {
    return JSON.readValue(String.valueOf(mitSitzung().getBody()), new TypeReference<>() {});
  }
}
