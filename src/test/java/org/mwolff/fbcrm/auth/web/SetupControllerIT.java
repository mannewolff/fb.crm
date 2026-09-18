package org.mwolff.fbcrm.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Die Einrichtung ueber HTTP (K3, K6, E5, E6, E26).
 *
 * <p>Der Einmal-Schluessel steht hier als Test-Property und nicht in der {@code application.yml}:
 * Eine Instanz mit gesetztem Schluessel ist eine Instanz, die noch jemand einrichten kann — das
 * soll keine Vorgabe des Repositories sein, sondern eine Entscheidung des Betreibers.
 */
@TestPropertySource(properties = "fbcrm.setup.bootstrap-token=" + SetupControllerIT.SCHLUESSEL)
class SetupControllerIT extends AbstractIntegrationTest {

  /**
   * Auch von {@code SetupRaceIT} und {@code SetupStatusIT} genutzt — gleiche Schalter, ein
   * Anwendungskontext fuer alle drei.
   */
  static final String SCHLUESSEL = "einmal-schluessel-der-einrichtung";

  static final String MAIL = "betreiber@example.org";
  static final String PASSWORT = "sicheres-passwort";

  private final TestRestTemplate rest;
  private final JdbcTemplate jdbc;

  @Autowired
  SetupControllerIT(final TestRestTemplate rest, final JdbcTemplate jdbc) {
    this.rest = rest;
    this.jdbc = jdbc;
  }

  /** Der Rumpf einer Einrichtungsanfrage; {@code null}-Werte bleiben erhalten. */
  static Map<String, String> rumpf(
      final String email,
      final String wiederholung,
      final String passwort,
      final String schluessel) {
    final Map<String, String> felder = new LinkedHashMap<>();
    felder.put("email", email);
    felder.put("emailRepeat", wiederholung);
    felder.put("displayName", "Manne");
    felder.put("password", passwort);
    felder.put("bootstrapToken", schluessel);
    return felder;
  }

  static ResponseEntity<String> richteEin(
      final TestRestTemplate rest, final Map<String, String> rumpf) {
    return rest.postForEntity("/api/setup", rumpf, String.class);
  }

  @BeforeEach
  void leereDieDatenbank() {
    jdbc.execute("TRUNCATE outbox_message, password_reset_token, account RESTART IDENTITY CASCADE");
  }

  private ResponseEntity<String> einrichtungMitDemRichtigenSchluessel() {
    return richteEin(rest, rumpf(MAIL, MAIL, PASSWORT, SCHLUESSEL));
  }

  private long konten() {
    return Objects.requireNonNull(jdbc.queryForObject("SELECT count(*) FROM account", Long.class));
  }

  @Test
  void initialize_givenAFreshInstanceAndTheRightKey_thenAnswersOk() {
    // When
    final ResponseEntity<String> antwort = einrichtungMitDemRichtigenSchluessel();

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void initialize_givenAFreshInstanceAndTheRightKey_thenCreatesExactlyOneAccount() {
    // When
    einrichtungMitDemRichtigenSchluessel();

    // Then
    assertThat(konten()).isEqualTo(1);
  }

  @Test
  void initialize_givenAFreshInstanceAndTheRightKey_thenSignsInTheOperator() {
    // When — K3: einrichten und anmelden sind ein Schritt.
    final ResponseEntity<String> antwort = einrichtungMitDemRichtigenSchluessel();

    // Then
    assertThat(antwort.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
        .contains("fbcrm_session=")
        .contains("HttpOnly")
        .contains("SameSite=Strict")
        .contains("Max-Age=86400");
  }

  @Test
  void initialize_givenAFreshInstanceAndTheRightKey_thenTheSessionIsImmediatelyUsable() {
    // Given
    final String gesetzt =
        String.valueOf(
            einrichtungMitDemRichtigenSchluessel().getHeaders().getFirst(HttpHeaders.SET_COOKIE));
    final HttpHeaders kopf = new HttpHeaders();
    kopf.add(HttpHeaders.COOKIE, gesetzt.substring(0, gesetzt.indexOf(';')));

    // When
    final ResponseEntity<String> ich =
        rest.exchange("/api/auth/me", HttpMethod.GET, new HttpEntity<>(kopf), String.class);

    // Then
    assertThat(ich.getBody()).contains("\"email\":\"" + MAIL + "\"");
  }

  @Test
  void initialize_givenASecondCall_thenIsRefused() {
    // Given — K3: der Einmal-Schluessel wirkt genau einmal.
    einrichtungMitDemRichtigenSchluessel();

    // When
    final ResponseEntity<String> zweiter =
        richteEin(rest, rumpf("zweiter@example.org", "zweiter@example.org", PASSWORT, SCHLUESSEL));

    // Then
    assertThat(zweiter.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void initialize_givenASecondCall_thenLeavesTheFirstAccountAlone() {
    // Given
    einrichtungMitDemRichtigenSchluessel();

    // When
    richteEin(rest, rumpf("zweiter@example.org", "zweiter@example.org", PASSWORT, SCHLUESSEL));

    // Then
    assertThat(konten()).isEqualTo(1);
  }

  @Test
  void initialize_givenAWrongKey_thenIsRefusedExactlyLikeASecondCall() {
    // Given
    final ResponseEntity<String> zweiterAufruf = zweiterAufruf();

    // When
    final ResponseEntity<String> falscherSchluessel =
        richteEin(rest, rumpf(MAIL, MAIL, PASSWORT, "falscher-schluessel"));

    // Then — der Unterschied verriete, ob die Instanz schon eingerichtet ist.
    assertThat(falscherSchluessel.getStatusCode()).isEqualTo(zweiterAufruf.getStatusCode());
    assertThat(falscherSchluessel.getBody()).isEqualTo(zweiterAufruf.getBody());
  }

  @Test
  void initialize_givenADifferentEmailRepeat_thenReportsItOnTheRepeatField() {
    // When — E6.
    final ResponseEntity<String> antwort =
        richteEin(rest, rumpf(MAIL, "vertippt@example.org", PASSWORT, SCHLUESSEL));

    // Then
    assertThat(antwort.getBody()).contains("\"emailRepeat\"");
  }

  @Test
  void initialize_givenADifferentEmailRepeat_thenAnswersBadRequest() {
    // When
    final ResponseEntity<String> antwort =
        richteEin(rest, rumpf(MAIL, "vertippt@example.org", PASSWORT, SCHLUESSEL));

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void initialize_givenADifferentEmailRepeat_thenCreatesNoAccount() {
    // When
    richteEin(rest, rumpf(MAIL, "vertippt@example.org", PASSWORT, SCHLUESSEL));

    // Then
    assertThat(konten()).isZero();
  }

  @Test
  void initialize_givenAPasswordOfSevenCharacters_thenAnswersBadRequest() {
    // When — K6, E26.
    final ResponseEntity<String> antwort =
        richteEin(rest, rumpf(MAIL, MAIL, "1234567", SCHLUESSEL));

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void initialize_givenAPasswordOfSevenCharacters_thenTheAnswerNamesTheMinimumLength() {
    // When — K6: die Antwort sagt, woran es lag.
    final ResponseEntity<String> antwort =
        richteEin(rest, rumpf(MAIL, MAIL, "1234567", SCHLUESSEL));

    // Then
    assertThat(antwort.getBody()).contains(PasswordConstraint.MESSAGE);
  }

  @Test
  void initialize_givenAPasswordOfSevenCharacters_thenCreatesNoAccount() {
    // When
    richteEin(rest, rumpf(MAIL, MAIL, "1234567", SCHLUESSEL));

    // Then
    assertThat(konten()).isZero();
  }

  private ResponseEntity<String> zweiterAufruf() {
    einrichtungMitDemRichtigenSchluessel();
    return richteEin(
        rest, rumpf("zweiter@example.org", "zweiter@example.org", PASSWORT, SCHLUESSEL));
  }
}
