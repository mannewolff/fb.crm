package org.mwolff.fbcrm.eigeneangaben;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.PasswordHasher;
import org.mwolff.fbcrm.auth.domain.Role;
import org.mwolff.fbcrm.eigeneangaben.web.EigeneAngabenResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Die beiden Wege der Selbstauskunft ueber HTTP, mit Sitzung und gegen eine echte
 * PostgreSQL-Instanz (Kriterium 1).
 *
 * <p>Drei Zusagen sind nur hier pruefbar. Erstens: Auf der frischen Datenbank antwortet {@code GET}
 * ohne Sonderzweig, alle Felder leer — die Migration hat die eine Zeile schon angelegt. Zweitens:
 * Ein per {@code PUT} gesendeter Leerstring landet als {@code NULL} in der Spalte und nicht als
 * Leerstring (E9) — der Nachweis liest die Spalte direkt, weil beides an der Schnittstelle gleich
 * aussaehe. Drittens: Beide Wege liegen unter {@code /api} und sind ohne Sitzung verschlossen.
 *
 * <p>Der Ausgangszustand wird vor jeder Methode wiederhergestellt, wie ihn die Migration
 * hinterlaesst; die Datenbank der Suite ist geteilt.
 */
class EigeneAngabenPersistenceIT extends AbstractIntegrationTest {

  private static final String PFAD = "/api/eigene-angaben";
  private static final String MAIL = "manne@example.org";
  private static final String PASSWORT = "richtiges-passwort";
  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");
  private static final String ZAHLUNGSBEDINGUNGEN = "Zahlbar innerhalb von 14 Tagen ohne Abzug.";

  private final TestRestTemplate rest;
  private final AccountRepository accounts;
  private final PasswordHasher hasher;
  private final JdbcTemplate jdbc;

  private HttpHeaders sitzung = new HttpHeaders();

  @Autowired
  EigeneAngabenPersistenceIT(
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
  void stelleDenStandDerMigrationHerUndMeldeAn() {
    jdbc.execute("DELETE FROM eigene_angaben");
    jdbc.execute("INSERT INTO eigene_angaben DEFAULT VALUES");
    jdbc.execute("TRUNCATE outbox_message, password_reset_token, account RESTART IDENTITY CASCADE");
    accounts.save(
        new Account(null, MAIL, "Manne", hasher.hash(PASSWORT), Role.ADMIN, 0, ANGELEGT, ANGELEGT));
    sitzung = angemeldeterKopf();
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

  private <T> ResponseEntity<T> ruf(
      final HttpMethod methode, final Object rumpf, final Class<T> typ) {
    return rest.exchange(PFAD, methode, new HttpEntity<>(rumpf, sitzung), typ);
  }

  /* Jackson schreibt ein nicht gesetztes Feld als null-Wert — genau die Lage „Angabe fehlt". */
  private static Map<String, Object> rumpf(final String wert) {
    final Map<String, Object> felder = new LinkedHashMap<>();
    felder.put("name", wert);
    felder.put("strasse", wert);
    felder.put("plz", wert);
    felder.put("ort", wert);
    felder.put("land", wert);
    felder.put("email", wert);
    felder.put("telefon", wert);
    felder.put("steuernummer", wert);
    felder.put("umsatzsteuerId", wert);
    felder.put("bankverbindung", wert);
    felder.put("zahlungsbedingungen", wert);
    return felder;
  }

  private EigeneAngabenResponse gelesen() {
    return Objects.requireNonNull(ruf(HttpMethod.GET, null, EigeneAngabenResponse.class).getBody());
  }

  private ResponseEntity<Void> gepflegt(final Map<String, Object> felder) {
    return ruf(HttpMethod.PUT, felder, Void.class);
  }

  private Integer gesetzteSpalten() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM eigene_angaben WHERE name IS NOT NULL OR strasse IS NOT NULL"
            + " OR plz IS NOT NULL OR ort IS NOT NULL OR land IS NOT NULL"
            + " OR email IS NOT NULL OR telefon IS NOT NULL OR steuernummer IS NOT NULL"
            + " OR umsatzsteuer_id IS NOT NULL OR bankverbindung IS NOT NULL"
            + " OR zahlungsbedingungen IS NOT NULL",
        Integer.class);
  }

  @Test
  void lesen_givenTheFreshDatabase_thenAnswersWithEveryFieldAbsent() {
    // When — Kriterium 1: der Bereich ist von Anfang an da.
    final EigeneAngabenResponse antwort = gelesen();

    // Then
    assertThat(antwort)
        .isEqualTo(
            new EigeneAngabenResponse(
                null, null, null, null, null, null, null, null, null, null, null));
  }

  @Test
  void pflegen_thenAnswersNoContent() {
    // When
    final ResponseEntity<Void> antwort = gepflegt(rumpf("Wert"));

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  void lesen_afterPflegen_thenAnswersWithTheStoredValues() {
    // Given
    final Map<String, Object> felder = rumpf(null);
    felder.put("name", "Manfred Wolff");
    felder.put("ort", "Bremen");
    felder.put("zahlungsbedingungen", ZAHLUNGSBEDINGUNGEN);
    gepflegt(felder);

    // When
    final EigeneAngabenResponse antwort = gelesen();

    // Then
    assertThat(antwort)
        .extracting(
            EigeneAngabenResponse::name,
            EigeneAngabenResponse::ort,
            EigeneAngabenResponse::zahlungsbedingungen)
        .containsExactly("Manfred Wolff", "Bremen", ZAHLUNGSBEDINGUNGEN);
  }

  @Test
  void pflegen_givenEmptyStrings_thenTheColumnsHoldNullAndNotTheEmptyString() {
    // Given — E9: zwei Schreibweisen fuer „nicht angegeben" gibt es nicht.
    gepflegt(rumpf("Wert"));

    // When
    gepflegt(rumpf(""));

    // Then
    assertThat(gesetzteSpalten()).isZero();
  }

  @Test
  void pflegen_givenEmptyStrings_thenLesenAnswersWithEveryFieldAbsent() {
    // Given
    gepflegt(rumpf("Wert"));

    // When — E9.
    gepflegt(rumpf(""));

    // Then
    assertThat(gelesen())
        .isEqualTo(
            new EigeneAngabenResponse(
                null, null, null, null, null, null, null, null, null, null, null));
  }

  @Test
  void lesen_withoutASession_thenUnauthorized() {
    // When — beide Wege liegen unter /api und stehen in keiner Ausnahmeliste.
    sitzung = new HttpHeaders();

    // Then
    assertThat(ruf(HttpMethod.GET, null, String.class).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void pflegen_withoutASession_thenUnauthorized() {
    // When
    sitzung = new HttpHeaders();

    // Then
    assertThat(ruf(HttpMethod.PUT, rumpf("Wert"), String.class).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
