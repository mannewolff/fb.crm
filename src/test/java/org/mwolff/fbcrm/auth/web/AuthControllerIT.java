package org.mwolff.fbcrm.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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
import org.springframework.test.context.TestPropertySource;

/**
 * Anmelden, abmelden und das eigene Konto ueber HTTP (K5, K8, K9, E9).
 *
 * <p>Der wichtigste Nachweis steht in {@code login_givenAnUnknownAddress…}: Die Antwort auf eine
 * unbekannte Adresse und die auf ein falsches Passwort duerfen sich in nichts unterscheiden — nicht
 * im Status, nicht im Rumpf, nicht in den Kopfzeilen (K5).
 */
class AuthControllerIT extends AbstractIntegrationTest {

  static final String MAIL = "manne@example.org";
  static final String PASSWORT = "richtiges-passwort";

  private final TestRestTemplate rest;
  private final AccountRepository accounts;
  private final PasswordHasher hasher;
  private final JdbcTemplate jdbc;

  @Autowired
  AuthControllerIT(
      final TestRestTemplate rest,
      final AccountRepository accounts,
      final PasswordHasher hasher,
      final JdbcTemplate jdbc) {
    this.rest = rest;
    this.accounts = accounts;
    this.hasher = hasher;
    this.jdbc = jdbc;
  }

  static Map<String, String> zugang(final String email, final String passwort) {
    return Map.of("email", email, "password", passwort);
  }

  static ResponseEntity<String> melde(
      final TestRestTemplate rest, final String email, final String passwort) {
    return rest.postForEntity("/api/auth/login", zugang(email, passwort), String.class);
  }

  @BeforeEach
  void legeDasAdminKontoAn() {
    jdbc.execute("TRUNCATE outbox_message, password_reset_token, account RESTART IDENTITY CASCADE");
    final Instant jetzt = Instant.parse("2026-09-18T10:00:00Z");
    accounts.save(
        new Account(null, MAIL, "Manne", hasher.hash(PASSWORT), Role.ADMIN, 0, jetzt, jetzt));
  }

  private ResponseEntity<String> anmeldung() {
    return melde(rest, MAIL, PASSWORT);
  }

  private String sessionCookie() {
    final String gesetzt =
        String.valueOf(anmeldung().getHeaders().getFirst(HttpHeaders.SET_COOKIE));
    return gesetzt.substring(0, gesetzt.indexOf(';'));
  }

  private ResponseEntity<String> mitCookie(final String pfad, final HttpMethod methode) {
    final HttpHeaders kopf = new HttpHeaders();
    kopf.add(HttpHeaders.COOKIE, sessionCookie());
    return rest.exchange(pfad, methode, new HttpEntity<>(kopf), String.class);
  }

  @Test
  void login_givenTheRightCredentials_thenAnswersOk() {
    // When
    final ResponseEntity<String> antwort = anmeldung();

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void login_givenTheRightCredentials_thenSetsAHardenedSessionCookie() {
    // When
    final ResponseEntity<String> antwort = anmeldung();

    // Then — K8, E4 und CLAUDE-security.md in einer Kopfzeile.
    assertThat(antwort.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
        .contains("fbcrm_session=")
        .contains("HttpOnly")
        .contains("Secure")
        .contains("SameSite=Strict")
        .contains("Path=/")
        .contains("Max-Age=86400");
  }

  @Test
  void login_givenTheRightCredentials_thenAnswersWithTheAccount() {
    // When
    final ResponseEntity<String> antwort = anmeldung();

    // Then
    assertThat(antwort.getBody()).contains("\"displayName\":\"Manne\"").contains(MAIL);
  }

  @Test
  void login_givenTheRightCredentials_thenNeverLeaksThePasswordHash() {
    // When
    final ResponseEntity<String> antwort = anmeldung();

    // Then
    assertThat(antwort.getBody()).doesNotContain("passwordHash").doesNotContain("$argon2");
  }

  @Test
  void login_givenAWrongPassword_thenAnswersUnauthorized() {
    // When
    final ResponseEntity<String> antwort = melde(rest, MAIL, "falsches-passwort");

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void login_givenAnUnknownAddress_thenAnswersExactlyLikeAWrongPassword() {
    // Given
    final ResponseEntity<String> falschesPasswort = melde(rest, MAIL, "falsches-passwort");

    // When
    final ResponseEntity<String> unbekannteAdresse =
        melde(rest, "fremd@example.org", "falsches-passwort");

    // Then — K5: Status und Rumpf sind nicht unterscheidbar.
    assertThat(unbekannteAdresse.getStatusCode()).isEqualTo(falschesPasswort.getStatusCode());
    assertThat(unbekannteAdresse.getBody()).isEqualTo(falschesPasswort.getBody());
  }

  @Test
  void login_givenAnUnknownAddress_thenCarriesTheSameHeadersAsAWrongPassword() {
    // Given
    final ResponseEntity<String> falschesPasswort = melde(rest, MAIL, "falsches-passwort");

    // When
    final ResponseEntity<String> unbekannteAdresse =
        melde(rest, "fremd@example.org", "falsches-passwort");

    // Then — K5: auch keine Kopfzeile verraet den Unterschied.
    assertThat(ohneZeitstempel(unbekannteAdresse)).isEqualTo(ohneZeitstempel(falschesPasswort));
  }

  @Test
  void login_givenAFailedAttempt_thenSetsNoCookieAtAll() {
    // When
    final ResponseEntity<String> antwort = melde(rest, MAIL, "falsches-passwort");

    // Then
    assertThat(antwort.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();
  }

  @Test
  void login_givenAnAddressThatIsNoAddress_thenAnswersBadRequest() {
    // When — die Form prueft Bean Validation, nicht der Anwendungsfall.
    final ResponseEntity<String> antwort = melde(rest, "keine-adresse", PASSWORT);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void me_givenTheSessionCookie_thenAnswersWithTheAccount() {
    // When
    final ResponseEntity<String> antwort = mitCookie("/api/auth/me", HttpMethod.GET);

    // Then
    assertThat(antwort.getBody()).contains("\"displayName\":\"Manne\"");
  }

  @Test
  void logout_givenTheSessionCookie_thenInvalidatesTheCookie() {
    // When
    final ResponseEntity<String> antwort = mitCookie("/api/auth/logout", HttpMethod.POST);

    // Then — K9: leerer Wert, Max-Age=0.
    assertThat(antwort.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
        .startsWith("fbcrm_session=;")
        .contains("Max-Age=0");
  }

  @Test
  void logout_givenTheSessionCookie_thenAnswersWithoutContent() {
    // When
    final ResponseEntity<String> antwort = mitCookie("/api/auth/logout", HttpMethod.POST);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  private static HttpHeaders ohneZeitstempel(final ResponseEntity<String> antwort) {
    final HttpHeaders kopf = new HttpHeaders();
    kopf.putAll(antwort.getHeaders());
    kopf.remove(HttpHeaders.DATE);
    return kopf;
  }

  /**
   * Die Zaehlbremse (E9) mit eigener, niedriger Schwelle.
   *
   * <p>Eigene Schalter heissen eigener Anwendungskontext — und damit ein eigener Zaehler. Liefe der
   * Nachweis in derselben Bremse wie die uebrigen Proben, haengte sein Ausgang an der Reihenfolge
   * der Testmethoden.
   */
  @Nested
  @TestPropertySource(properties = "fbcrm.auth.login-max-attempts=2")
  class Zaehlbremse {

    private final TestRestTemplate gebremst;

    @Autowired
    Zaehlbremse(final TestRestTemplate gebremst) {
      this.gebremst = gebremst;
    }

    @Test
    void login_givenMoreFailuresThanTheThreshold_thenAnswersTooManyRequests() {
      // Given
      IntStream.range(0, 2).forEach(unbenutzt -> melde(gebremst, MAIL, "falsches-passwort"));

      // When
      final ResponseEntity<String> antwort = melde(gebremst, MAIL, "falsches-passwort");

      // Then
      assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void login_givenMoreFailuresThanTheThreshold_thenEvenTheRightPasswordIsRefused() {
      // Given — die Bremse greift vor der Pruefung, sonst waere sie keine.
      IntStream.range(0, 2).forEach(unbenutzt -> melde(gebremst, MAIL, "falsches-passwort"));

      // When
      final ResponseEntity<String> antwort = melde(gebremst, MAIL, PASSWORT);

      // Then
      assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }
  }
}
