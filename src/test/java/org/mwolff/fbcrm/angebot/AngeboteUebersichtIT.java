package org.mwolff.fbcrm.angebot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;
import org.mwolff.fbcrm.angebot.web.AngeboteUebersichtResponse;
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
 * Die Uebersicht aller Angebote ueber HTTP, mit Sitzung und gegen eine echte PostgreSQL-Instanz
 * (Issue #127, Kriterium 8): drei Angebote an zwei Firmen, ungefiltert und nach Status gefiltert.
 *
 * <p>Nur hier pruefbar: dass die Abfrage nach Status in der Datenbank filtert und dass der Pfad
 * {@code /api/angebote} neben {@code /api/angebote/{id}} besteht und ohne Sitzung verschlossen ist.
 */
class AngeboteUebersichtIT extends AbstractIntegrationTest {

  private static final String MAIL = "manne@example.org";
  private static final String PASSWORT = "richtiges-passwort";
  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");

  private final TestRestTemplate rest;
  private final AccountRepository accounts;
  private final PasswordHasher hasher;
  private final JdbcTemplate jdbc;

  private HttpHeaders sitzung = new HttpHeaders();
  private long adler;
  private long biber;

  @Autowired
  AngeboteUebersichtIT(
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
  void legeDreiAngeboteAnZweiFirmenAnUndMeldeAn() {
    jdbc.execute(
        "TRUNCATE angebot_position, angebot, ansprechpartner, firma RESTART IDENTITY CASCADE");
    adler = firma("Adler AG");
    biber = firma("Biber GmbH");
    angebot(adler, "ANGELEGT", "2026-09-20");
    angebot(biber, "BESTELLT", "2026-09-25");
    angebot(adler, "BESTELLT", "2026-09-22");
    jdbc.execute("TRUNCATE outbox_message, password_reset_token, account RESTART IDENTITY CASCADE");
    accounts.save(
        new Account(null, MAIL, "Manne", hasher.hash(PASSWORT), Role.ADMIN, 0, ANGELEGT, ANGELEGT));
    sitzung = angemeldeterKopf();
  }

  private long firma(final String name) {
    jdbc.update("INSERT INTO firma (name) VALUES (?)", name);
    return Objects.requireNonNull(
            jdbc.queryForObject("SELECT id FROM firma WHERE name = ?", Long.class, name))
        .longValue();
  }

  private void angebot(final long firma, final String status, final String datum) {
    jdbc.update(
        "INSERT INTO angebot (firma_id, status, angebot_datum) VALUES (?, ?, CAST(? AS date))",
        Long.valueOf(firma),
        status,
        datum);
  }

  private HttpHeaders angemeldeterKopf() {
    final ResponseEntity<String> anmeldung =
        rest.postForEntity(
            "/api/auth/login", Map.of("email", MAIL, "password", PASSWORT), String.class);
    final String gesetzt = String.valueOf(anmeldung.getHeaders().getFirst(HttpHeaders.SET_COOKIE));
    final HttpHeaders kopf = new HttpHeaders();
    kopf.add(HttpHeaders.COOKIE, gesetzt.substring(0, gesetzt.indexOf(';')));
    return kopf;
  }

  private <T> ResponseEntity<T> hole(final String pfad, final Class<T> typ) {
    return rest.exchange(pfad, HttpMethod.GET, new HttpEntity<>(null, sitzung), typ);
  }

  @Test
  void angebote_withoutAFilter_thenEveryAngebotNewestFirstWithItsFirmaName() {
    // When
    final AngeboteUebersichtResponse antwort =
        Objects.requireNonNull(hole("/api/angebote", AngeboteUebersichtResponse.class).getBody());

    // Then
    assertThat(antwort.angebote())
        .extracting(
            AngeboteUebersichtResponse.Zeile::firmaName, AngeboteUebersichtResponse.Zeile::status)
        .containsExactly(
            tuple("Biber GmbH", Angebotsstatus.BESTELLT),
            tuple("Adler AG", Angebotsstatus.BESTELLT),
            tuple("Adler AG", Angebotsstatus.ANGELEGT));
  }

  @Test
  void angebote_withAStatus_thenOnlyThoseInThatStatus() {
    // When
    final AngeboteUebersichtResponse antwort =
        Objects.requireNonNull(
            hole("/api/angebote?status=ANGELEGT", AngeboteUebersichtResponse.class).getBody());

    // Then
    assertThat(antwort.angebote())
        .singleElement()
        .satisfies(
            zeile -> assertThat(zeile.firmaId()).isEqualTo(adler),
            zeile -> assertThat(zeile.status()).isEqualTo(Angebotsstatus.ANGELEGT));
  }

  @Test
  void angebote_withAnUnknownStatus_thenAnswers400() {
    // When / Then
    assertThat(hole("/api/angebote?status=VERHANDELT", String.class).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void angebote_withoutASession_thenUnauthorized() {
    // Given
    sitzung = new HttpHeaders();

    // When / Then
    assertThat(hole("/api/angebote", String.class).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
