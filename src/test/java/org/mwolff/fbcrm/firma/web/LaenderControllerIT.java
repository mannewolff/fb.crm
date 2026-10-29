package org.mwolff.fbcrm.firma.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.auth.Anmeldung;
import org.mwolff.fbcrm.auth.Konten;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.Role;
import org.mwolff.fbcrm.auth.domain.SecretHasher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Wer die Laenderliste lesen darf, gegen den laufenden Server (Plan #461, E21): jede angemeldete
 * Rolle, ohne Sitzung niemand, und schreiben niemand.
 */
class LaenderControllerIT extends AbstractIntegrationTest {

  private static final Instant ANGELEGT = Instant.parse("2026-10-01T08:00:00Z");

  private final TestRestTemplate rest;
  private final AccountRepository accounts;
  private final SecretHasher hasher;
  private final JdbcTemplate jdbc;

  @Autowired
  LaenderControllerIT(
      final TestRestTemplate rest,
      final AccountRepository accounts,
      final SecretHasher hasher,
      final JdbcTemplate jdbc) {
    this.rest = rest;
    this.accounts = accounts;
    this.hasher = hasher;
    this.jdbc = jdbc;
  }

  @BeforeEach
  void leereDieKonten() {
    jdbc.execute("TRUNCATE login_code RESTART IDENTITY");
    leereKonten(jdbc);
  }

  private HttpHeaders sitzung(final Role rolle) {
    final String email = rolle.name().toLowerCase(Locale.ROOT) + "@example.org";
    accounts.save(Konten.mit(null, email, rolle, true, 0, ANGELEGT));
    return Anmeldung.perCode(rest, jdbc, hasher, email);
  }

  private ResponseEntity<String> ruf(final HttpHeaders kopf, final HttpMethod methode) {
    return rest.exchange("/api/laender", methode, new HttpEntity<>(kopf), String.class);
  }

  @ParameterizedTest(name = "{0} liest die Laenderliste")
  @EnumSource(Role.class)
  void alle_givenAnyRole_thenAnswersWithTheList(final Role rolle) {
    // When
    final ResponseEntity<String> antwort = ruf(sitzung(rolle), HttpMethod.GET);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(antwort.getBody()).contains("{\"kuerzel\":\"DE\",\"name\":\"Deutschland\"}");
  }

  @Test
  void alle_withoutASession_thenAnswersUnauthorized() {
    // When / Then
    assertThat(ruf(new HttpHeaders(), HttpMethod.GET).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @ParameterizedTest(name = "{0} schreibt nicht in die Laenderliste")
  @EnumSource(Role.class)
  void post_givenAnyRole_thenIsRefused(final Role rolle) {
    // When
    final ResponseEntity<String> antwort = ruf(sitzung(rolle), HttpMethod.POST);

    // Then — ReadOnly weist die Zugangsregel ab, den anderen fehlt der Weg.
    assertThat(antwort.getStatusCode().is4xxClientError()).isTrue();
    assertThat(antwort.getStatusCode()).isNotEqualTo(HttpStatus.NOT_FOUND);
  }
}
