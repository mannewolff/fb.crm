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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Der Passwort-Reset ueber HTTP (K6, K7, E24).
 *
 * <p>Der wichtigste Nachweis steht in {@code request_givenAnUnknownAddress_…}: Die Antwort auf eine
 * bekannte und die auf eine unbekannte Adresse duerfen sich in nichts unterscheiden — nicht im
 * Status, nicht im Rumpf, nicht in den Kopfzeilen (K7). Der zweitwichtigste in {@code
 * checkToken_…thenChangesNothing}: Die Voranfrage, die entscheidet, ob ein Formular erscheint, darf
 * den Link nicht verbrauchen, den sie prueft (E24).
 *
 * <p>Das Token selbst steht nirgends in der Datenbank — dort liegt nur sein Hash. Der Test holt es
 * deshalb aus dem Rumpf der Nachricht im Postausgangsfach, genau wie der Empfaenger es aus seiner
 * Mail holt.
 */
class PasswordResetControllerIT extends AbstractIntegrationTest {

  private static final String MAIL = "manne@example.org";
  private static final String ALTES_PASSWORT = "altes-passwort";
  private static final String NEUES_PASSWORT = "neues-sicheres-passwort";
  private static final String LINK_ANFANG = "/passwort-neu?token=";
  private static final String ANFORDERUNG = "/api/auth/password-reset";
  private static final String EINLOESUNG = "/api/auth/password-reset/confirm";

  private final TestRestTemplate rest;
  private final AccountRepository accounts;
  private final PasswordHasher hasher;
  private final JdbcTemplate jdbc;

  @Autowired
  PasswordResetControllerIT(
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
        new Account(null, MAIL, "Manne", hasher.hash(ALTES_PASSWORT), Role.ADMIN, 0, jetzt, jetzt));
  }

  private ResponseEntity<String> fordereAn(final String adresse) {
    return rest.postForEntity(ANFORDERUNG, Map.of("email", adresse), String.class);
  }

  /** Das Token, wie es im Link der zugestellten Nachricht steht. */
  private String tokenAusDemPostausgangsfach() {
    final String rumpf = jdbc.queryForObject("SELECT body FROM outbox_message", String.class);
    final int anfang = String.valueOf(rumpf).indexOf(LINK_ANFANG) + LINK_ANFANG.length();
    return String.valueOf(rumpf).substring(anfang, String.valueOf(rumpf).indexOf('\n', anfang));
  }

  private String frischesToken() {
    fordereAn(MAIL);
    return tokenAusDemPostausgangsfach();
  }

  private ResponseEntity<String> pruefe(final String token) {
    return rest.getForEntity(ANFORDERUNG + "/" + token, String.class);
  }

  private ResponseEntity<String> loeseEin(final String token, final String passwort) {
    return rest.postForEntity(
        EINLOESUNG, Map.of("token", token, "password", passwort), String.class);
  }

  private ResponseEntity<String> melde(final String passwort) {
    return rest.postForEntity(
        "/api/auth/login", Map.of("email", MAIL, "password", passwort), String.class);
  }

  private String sessionCookie() {
    final String gesetzt =
        String.valueOf(melde(ALTES_PASSWORT).getHeaders().getFirst(HttpHeaders.SET_COOKIE));
    return gesetzt.substring(0, gesetzt.indexOf(';'));
  }

  private ResponseEntity<String> frageDasEigeneKontoAb(final String cookie) {
    final HttpHeaders kopf = new HttpHeaders();
    kopf.add(HttpHeaders.COOKIE, cookie);
    return rest.exchange("/api/auth/me", HttpMethod.GET, new HttpEntity<>(kopf), String.class);
  }

  private static HttpHeaders ohneZeitstempel(final ResponseEntity<String> antwort) {
    final HttpHeaders kopf = new HttpHeaders();
    kopf.putAll(antwort.getHeaders());
    kopf.remove(HttpHeaders.DATE);
    return kopf;
  }

  private Long anzahl(final String tabelle) {
    return jdbc.queryForObject("SELECT count(*) FROM " + tabelle, Long.class);
  }

  @Test
  void request_givenAKnownAddress_thenAnswersAccepted() {
    // When
    final ResponseEntity<String> antwort = fordereAn(MAIL);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
  }

  @Test
  void request_givenAnUnknownAddress_thenAnswersExactlyLikeAKnownOne() {
    // Given
    final ResponseEntity<String> bekannt = fordereAn(MAIL);

    // When
    final ResponseEntity<String> unbekannt = fordereAn("fremd@example.org");

    // Then — K7: Status und Rumpf sind nicht unterscheidbar.
    assertThat(unbekannt.getStatusCode()).isEqualTo(bekannt.getStatusCode());
    assertThat(unbekannt.getBody()).isEqualTo(bekannt.getBody());
  }

  @Test
  void request_givenAnUnknownAddress_thenCarriesTheSameHeadersAsAKnownOne() {
    // Given
    final ResponseEntity<String> bekannt = fordereAn(MAIL);

    // When
    final ResponseEntity<String> unbekannt = fordereAn("fremd@example.org");

    // Then — K7: auch keine Kopfzeile verraet den Unterschied.
    assertThat(ohneZeitstempel(unbekannt)).isEqualTo(ohneZeitstempel(bekannt));
  }

  @Test
  void request_givenAKnownAddress_thenLeavesExactlyOneOrderInTheOutbox() {
    // When
    fordereAn(MAIL);

    // Then
    assertThat(anzahl("outbox_message")).isEqualTo(1L);
  }

  @Test
  void request_givenAnUnknownAddress_thenLeavesNoOrderInTheOutbox() {
    // When
    fordereAn("fremd@example.org");

    // Then — der einzige Unterschied zwischen beiden Faellen steht hier, nicht in der Antwort.
    assertThat(anzahl("outbox_message")).isZero();
  }

  @Test
  void request_givenAnAddressThatIsNoAddress_thenAnswersBadRequest() {
    // When — die Form prueft Bean Validation, nicht der Anwendungsfall.
    final ResponseEntity<String> antwort = fordereAn("keine-adresse");

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void request_thenIsReachableWithoutASession() {
    // When / Then — K1: der Weg zurueck steht offen, sonst waere er keiner.
    assertThat(fordereAn(MAIL).getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void checkToken_givenAFreshToken_thenAnswersNoContent() {
    // When
    final ResponseEntity<String> antwort = pruefe(frischesToken());

    // Then — gueltig heisst: die Seite darf ein Formular zeigen (K7).
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  void checkToken_givenAFreshToken_thenChangesNothing() {
    // Given
    final String token = frischesToken();

    // When
    pruefe(token);

    // Then — E24: used_at bleibt leer.
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM password_reset_token WHERE used_at IS NULL", Long.class))
        .isEqualTo(1L);
  }

  @Test
  void checkToken_givenAFreshToken_thenTheTokenIsStillRedeemableAfterwards() {
    // Given
    final String token = frischesToken();
    pruefe(token);

    // When
    final ResponseEntity<String> antwort = loeseEin(token, NEUES_PASSWORT);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  void checkToken_givenAnUnknownToken_thenAnswersGone() {
    // When
    final ResponseEntity<String> antwort = pruefe("ein-token-das-es-nie-gab");

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.GONE);
  }

  @Test
  void checkToken_givenAnExpiredToken_thenAnswersGone() {
    // Given
    final String token = frischesToken();
    jdbc.execute("UPDATE password_reset_token SET expires_at = now() - interval '1 minute'");

    // When
    final ResponseEntity<String> antwort = pruefe(token);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.GONE);
  }

  @Test
  void checkToken_givenAnAlreadyUsedToken_thenAnswersGone() {
    // Given
    final String token = frischesToken();
    loeseEin(token, NEUES_PASSWORT);

    // When
    final ResponseEntity<String> antwort = pruefe(token);

    // Then — K7: ein benutzter Link zeigt eine Meldung statt eines Formulars.
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.GONE);
  }

  @Test
  void confirm_givenAFreshToken_thenAnswersNoContent() {
    // When
    final ResponseEntity<String> antwort = loeseEin(frischesToken(), NEUES_PASSWORT);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  void confirm_givenAFreshToken_thenOnlyTheNewPasswordWorks() {
    // Given
    loeseEin(frischesToken(), NEUES_PASSWORT);

    // When / Then — K7: nach dem Setzen gilt nur noch das neue Passwort.
    assertThat(melde(NEUES_PASSWORT).getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(melde(ALTES_PASSWORT).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void confirm_givenASessionIssuedBeforeTheReset_thenThatSessionIsOver() {
    // Given — ein Cookie aus der Zeit vor dem Reset.
    final String vorher = sessionCookie();

    // When
    loeseEin(frischesToken(), NEUES_PASSWORT);

    // Then — K7: bestehende Anmeldungen auf anderen Geraeten enden (Sitzungs-Generation).
    assertThat(frageDasEigeneKontoAb(vorher).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void confirm_givenTheSameTokenTwice_thenAnswersGoneOnTheSecondTry() {
    // Given
    final String token = frischesToken();
    loeseEin(token, NEUES_PASSWORT);

    // When
    final ResponseEntity<String> antwort = loeseEin(token, "noch-ein-passwort");

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.GONE);
  }

  @Test
  void confirm_givenAnExpiredToken_thenAnswersGone() {
    // Given
    final String token = frischesToken();
    jdbc.execute("UPDATE password_reset_token SET expires_at = now() - interval '1 minute'");

    // When
    final ResponseEntity<String> antwort = loeseEin(token, NEUES_PASSWORT);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.GONE);
  }

  @Test
  void confirm_givenAPasswordOfSevenCharacters_thenAnswersBadRequest() {
    // When — K6: die Mindestlaenge gilt auch beim Neusetzen.
    final ResponseEntity<String> antwort = loeseEin(frischesToken(), "1234567");

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void confirm_givenAPasswordOfSevenCharacters_thenNamesTheMinimumLength() {
    // When — eine Antwort, die nur „ist ungueltig" sagt, laesst den Aufrufer raten (K6).
    final ResponseEntity<String> antwort = loeseEin(frischesToken(), "1234567");

    // Then
    assertThat(antwort.getBody()).contains("mindestens 8 Zeichen");
  }

  @Test
  void confirm_givenAPasswordOfSevenCharacters_thenLeavesTheTokenUntouched() {
    // Given
    final String token = frischesToken();

    // When
    loeseEin(token, "1234567");

    // Then — eine abgewiesene Form verbraucht den Link nicht.
    assertThat(loeseEin(token, NEUES_PASSWORT).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }
}
