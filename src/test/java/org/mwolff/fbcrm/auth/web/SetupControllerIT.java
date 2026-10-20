package org.mwolff.fbcrm.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.auth.Anmeldung;
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
 * Die Einrichtung ueber HTTP in zwei Schritten: Code anfordern, mit dem Code bestaetigen (K3, E9).
 *
 * <p>Der Einmal-Schluessel steht hier als Test-Property und nicht in der {@code application.yml}:
 * Eine Instanz mit gesetztem Schluessel ist eine Instanz, die noch jemand einrichten kann — das
 * soll keine Vorgabe des Repositories sein, sondern eine Entscheidung des Betreibers.
 *
 * <p>Der Mailversand ist eingeschaltet: Der Code steht dann im Zustellauftrag an genau die Adresse,
 * fuer die er ausgestellt wurde. Die Bremse je Herkunft ist ausser Kraft — alle Proben dieses
 * Kontexts kommen von derselben Adresse, und ihre Grenze weisen {@code CodeEinloesungTest} und
 * {@code AuthControllerIT} nach.
 */
@TestPropertySource(
    properties = {
      "fbcrm.setup.bootstrap-token=" + SetupControllerIT.SCHLUESSEL,
      "fbcrm.mail.enabled=true",
      "fbcrm.auth.login-max-attempts=1000"
    })
class SetupControllerIT extends AbstractIntegrationTest {

  /**
   * Auch von {@code SetupRaceIT}, {@code SetupStatusIT}, {@code EinrichtungOhnePasswortIT} und
   * {@code AnmeldungPerCodeIT} genutzt — gleiche Schalter, ein Anwendungskontext fuer alle.
   */
  static final String SCHLUESSEL = "einmal-schluessel-der-einrichtung";

  static final String MAIL = "betreiber@example.org";
  static final String NAME = "Manne";

  private final TestRestTemplate rest;
  private final JdbcTemplate jdbc;

  @Autowired
  SetupControllerIT(final TestRestTemplate rest, final JdbcTemplate jdbc) {
    this.rest = rest;
    this.jdbc = jdbc;
  }

  /** Fordert den Code der Einrichtung an (erster Schritt). */
  static ResponseEntity<String> fordereCodeAn(
      final TestRestTemplate rest, final String email, final String schluessel) {
    return rest.postForEntity(
        "/api/setup/code",
        Map.of("email", email, "displayName", NAME, "bootstrapToken", schluessel),
        String.class);
  }

  /** Der Rumpf der Bestaetigung (zweiter Schritt); {@code null}-Werte bleiben erhalten. */
  static Map<String, String> rumpf(
      final String email, final @Nullable String code, final String schluessel) {
    final Map<String, String> felder = new LinkedHashMap<>();
    felder.put("bootstrapToken", schluessel);
    felder.put("displayName", NAME);
    felder.put("email", email);
    felder.put("code", code);
    return felder;
  }

  static ResponseEntity<String> richteEin(
      final TestRestTemplate rest, final Map<String, String> rumpf) {
    return rest.postForEntity("/api/setup", rumpf, String.class);
  }

  /** Beide Schritte mit dem richtigen Schluessel und dem Code aus dem Zustellauftrag. */
  static ResponseEntity<String> richteVollstaendigEin(
      final TestRestTemplate rest, final JdbcTemplate jdbc, final String email) {
    fordereCodeAn(rest, email, SCHLUESSEL);
    return richteEin(rest, rumpf(email, Anmeldung.codeAusDemPostausgang(jdbc, email), SCHLUESSEL));
  }

  /** Leert alles, was eine Einrichtung hinterlaesst. */
  static void leere(final JdbcTemplate jdbc) {
    jdbc.execute("TRUNCATE outbox_message, login_code, account RESTART IDENTITY CASCADE");
  }

  @BeforeEach
  void leereDieDatenbank() {
    leere(jdbc);
  }

  private ResponseEntity<String> einrichtungMitDemRichtigenSchluessel() {
    return richteVollstaendigEin(rest, jdbc, MAIL);
  }

  private long konten() {
    return Objects.requireNonNull(jdbc.queryForObject("SELECT count(*) FROM account", Long.class));
  }

  @Test
  void requestCode_givenAFreshInstanceAndTheRightKey_thenAnswersAcceptedAndCreatesNoAccountYet() {
    // When
    final ResponseEntity<String> antwort = fordereCodeAn(rest, MAIL, SCHLUESSEL);

    // Then — erst der richtige Code legt das Konto an (Kriterium 6).
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(konten()).isZero();
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
        .contains("Max-Age=2592000");
  }

  @Test
  void initialize_givenAFreshInstanceAndTheRightKey_thenTheSessionIsImmediatelyUsable() {
    // Given
    final HttpHeaders kopf = Anmeldung.mitCookie(einrichtungMitDemRichtigenSchluessel());

    // When
    final ResponseEntity<String> ich =
        rest.exchange("/api/auth/me", HttpMethod.GET, new HttpEntity<>(kopf), String.class);

    // Then
    assertThat(ich.getBody()).contains("\"email\":\"" + MAIL + "\"");
  }

  @Test
  void initialize_givenTheCodeOnceMore_thenIsRefused() {
    // Given — der Code der Einrichtung gilt nur einmal.
    fordereCodeAn(rest, MAIL, SCHLUESSEL);
    final String code = Anmeldung.codeAusDemPostausgang(jdbc, MAIL);
    richteEin(rest, rumpf(MAIL, code, SCHLUESSEL));

    // When
    final ResponseEntity<String> nochmal = richteEin(rest, rumpf(MAIL, code, SCHLUESSEL));

    // Then
    assertThat(nochmal.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(konten()).isEqualTo(1);
  }

  @Test
  void initialize_givenAWrongCode_thenAnswersUnauthorizedAndCreatesNoAccount() {
    // Given
    fordereCodeAn(rest, MAIL, SCHLUESSEL);

    // When
    final ResponseEntity<String> antwort = richteEin(rest, rumpf(MAIL, "999999", SCHLUESSEL));

    // Then — fuer den falschen Code hilft derselbe Schritt wie bei der Anmeldung.
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(konten()).isZero();
  }

  @Test
  void initialize_givenASecondCall_thenIsRefused() {
    // Given — K3: der Einmal-Schluessel wirkt genau einmal.
    einrichtungMitDemRichtigenSchluessel();

    // When
    final ResponseEntity<String> zweiter =
        richteEin(rest, rumpf("zweiter@example.org", "000000", SCHLUESSEL));

    // Then
    assertThat(zweiter.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void requestCode_givenAnInstanceThatIsSetUp_thenIsRefusedAndSendsNoCode() {
    // Given
    einrichtungMitDemRichtigenSchluessel();

    // When
    final ResponseEntity<String> zweiter = fordereCodeAn(rest, "zweiter@example.org", SCHLUESSEL);

    // Then
    assertThat(zweiter.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM outbox_message WHERE recipient = ?",
                Long.class,
                "zweiter@example.org"))
        .isZero();
  }

  @Test
  void initialize_givenASecondCall_thenLeavesTheFirstAccountAlone() {
    // Given
    einrichtungMitDemRichtigenSchluessel();

    // When
    richteEin(rest, rumpf("zweiter@example.org", "000000", SCHLUESSEL));

    // Then
    assertThat(konten()).isEqualTo(1);
  }

  @Test
  void initialize_givenAWrongKey_thenIsRefusedExactlyLikeASecondCall() {
    // Given
    einrichtungMitDemRichtigenSchluessel();
    final ResponseEntity<String> zweiterAufruf =
        richteEin(rest, rumpf("zweiter@example.org", "000000", SCHLUESSEL));
    leere(jdbc);

    // When
    final ResponseEntity<String> falscherSchluessel =
        richteEin(rest, rumpf(MAIL, "000000", "falscher-schluessel"));

    // Then — der Unterschied verriete, ob die Instanz schon eingerichtet ist.
    assertThat(falscherSchluessel.getStatusCode()).isEqualTo(zweiterAufruf.getStatusCode());
    assertThat(falscherSchluessel.getBody()).isEqualTo(zweiterAufruf.getBody());
  }

  @Test
  void initialize_givenNoCode_thenAnswersBadRequestAndCreatesNoAccount() {
    // When — die Form prueft Bean Validation, nicht der Anwendungsfall.
    final ResponseEntity<String> antwort = richteEin(rest, rumpf(MAIL, null, SCHLUESSEL));

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(antwort.getBody()).contains("\"code\"");
    assertThat(konten()).isZero();
  }
}
