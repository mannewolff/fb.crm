package org.mwolff.fbcrm.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.auth.Anmeldung;
import org.mwolff.fbcrm.auth.Konten;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * Anmelden per Code ueber HTTP, von der Anforderung bis zur Sitzung (Kriterium 2 bis 4, 7).
 *
 * <p>Der Mailversand ist eingeschaltet, der Code kommt aus dem Zustellauftrag — so, wie ihn der
 * Mensch aus der Mail abliest. Mitgeschrieben wird am <b>Wurzel-Logger</b>: So faellt auch eine
 * Zeile auf, die ein anderer Baustein unterwegs schreibt.
 *
 * <p>Dieselben Schalter wie {@code SetupControllerIT}, damit der Anwendungskontext geteilt wird;
 * der gesetzte Einmal-Schluessel spielt hier keine Rolle, weil es das Konto schon gibt.
 */
@TestPropertySource(
    properties = {
      "fbcrm.setup.bootstrap-token=" + SetupControllerIT.SCHLUESSEL,
      "fbcrm.mail.enabled=true",
      "fbcrm.auth.login-max-attempts=1000"
    })
class AnmeldungPerCodeIT extends AbstractIntegrationTest {

  private static final String MAIL = "manne@example.org";
  private static final String FREMD = "fremd@example.org";

  private final TestRestTemplate rest;
  private final AccountRepository accounts;
  private final JdbcTemplate jdbc;

  private final ListAppender<ILoggingEvent> mitgeschrieben = new ListAppender<>();
  private final ch.qos.logback.classic.Logger wurzel;

  @Autowired
  AnmeldungPerCodeIT(
      final TestRestTemplate rest, final AccountRepository accounts, final JdbcTemplate jdbc) {
    this.rest = rest;
    this.accounts = accounts;
    this.jdbc = jdbc;
    this.wurzel =
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger(Logger.ROOT_LOGGER_NAME);
  }

  @BeforeEach
  void legeDasKontoAnUndSchreibMit() {
    SetupControllerIT.leere(jdbc);
    accounts.save(Konten.neu(MAIL, Instant.parse("2026-10-20T08:00:00Z")));
    mitgeschrieben.setContext(wurzel.getLoggerContext());
    mitgeschrieben.start();
    wurzel.addAppender(mitgeschrieben);
  }

  @AfterEach
  void loeseDichWiederAb() {
    wurzel.detachAppender(mitgeschrieben);
    mitgeschrieben.stop();
  }

  private ResponseEntity<String> fordereAn(final String email) {
    return rest.postForEntity("/api/auth/code", Map.of("email", email), String.class);
  }

  private ResponseEntity<String> gibEin(final String email, final String code) {
    return rest.postForEntity(
        "/api/auth/login", Map.of("email", email, "code", code), String.class);
  }

  /** Ein Code, der sicher nicht der richtige ist. */
  private static String daneben(final String richtig) {
    return richtig.equals("000000") ? "111111" : "000000";
  }

  private String protokoll() {
    return mitgeschrieben.list.stream()
        .map(ILoggingEvent::getFormattedMessage)
        .reduce("", (links, rechts) -> links + '\n' + rechts);
  }

  @Test
  @DisplayName("Szenario: Anmelden per Code")
  void szenarioAnmeldenPerCode_thenSessionAndNoCodeInTheLog() {
    // Given — das Konto ist eingerichtet, der Mailversand eingeschaltet.
    assertThat(fordereAn(MAIL).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    final String code = Anmeldung.codeAusDemPostausgang(jdbc, MAIL);

    // When — die Adresse des Kontos und der Code aus der Mail.
    final ResponseEntity<String> antwort = gibEin(MAIL, code);

    // Then — angemeldet: Die Sitzung traegt, und im Protokoll steht kein Code.
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
    final HttpHeaders sitzung = Anmeldung.mitCookie(antwort);
    final ResponseEntity<String> ich =
        rest.exchange("/api/auth/me", HttpMethod.GET, new HttpEntity<>(sitzung), String.class);
    assertThat(ich.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(ich.getBody()).contains("\"email\":\"" + MAIL + "\"");
    assertThat(protokoll()).doesNotContain(code);
  }

  @Test
  @DisplayName("Szenario: Fünf falsche Eingaben")
  void szenarioFuenfFalscheEingaben_thenEvenTheRightCodeIsRejected() {
    // Given
    fordereAn(MAIL);
    final String code = Anmeldung.codeAusDemPostausgang(jdbc, MAIL);

    // When — fuenfmal ein falscher Code.
    for (int versuch = 1; versuch <= 5; versuch++) {
      assertThat(gibEin(MAIL, daneben(code)).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // Then — der Code ist verfallen, auch der richtige wird abgewiesen.
    assertThat(gibEin(MAIL, code).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void login_givenAnUnknownAddress_thenAnswersLikeAWrongCodeEvenAfterTheFifthInput() {
    // Given — zu beiden Adressen ist ein Code angefordert; nur das Konto bekommt einen.
    fordereAn(MAIL);
    fordereAn(FREMD);
    final String code = Anmeldung.codeAusDemPostausgang(jdbc, MAIL);

    // When / Then — Kriterium 3: Status und Rumpf gleich, auch nach der fuenften Eingabe.
    for (int versuch = 1; versuch <= 6; versuch++) {
      final ResponseEntity<String> falsch = gibEin(MAIL, daneben(code));
      final ResponseEntity<String> unbekannt = gibEin(FREMD, daneben(code));
      assertThat(unbekannt.getStatusCode()).isEqualTo(falsch.getStatusCode());
      assertThat(unbekannt.getBody()).isEqualTo(falsch.getBody());
    }
  }

  @Test
  void requestCode_givenAnUnknownAddress_thenAnswersLikeTheAccountAndSendsNoMail() {
    // When
    final ResponseEntity<String> fuerDasKonto = fordereAn(MAIL);
    final ResponseEntity<String> ohneKonto = fordereAn(FREMD);

    // Then — Kriterium 3.
    assertThat(ohneKonto.getStatusCode()).isEqualTo(fuerDasKonto.getStatusCode());
    assertThat(ohneKonto.getBody()).isEqualTo(fuerDasKonto.getBody());
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM outbox_message WHERE recipient = ?", Long.class, FREMD))
        .isZero();
  }

  @Test
  void login_givenTheCodeASecondTime_thenIsRejected() {
    // Given — Kriterium 4: ein Code gilt nur einmal.
    fordereAn(MAIL);
    final String code = Anmeldung.codeAusDemPostausgang(jdbc, MAIL);
    gibEin(MAIL, code);

    // When / Then
    assertThat(gibEin(MAIL, code).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void login_givenTheOlderOfTwoCodes_thenIsRejected() {
    // Given — Kriterium 4: ein neuer Code macht den alten ungueltig.
    fordereAn(MAIL);
    final String alt = Anmeldung.codeAusDemPostausgang(jdbc, MAIL);
    fordereAn(MAIL);
    final String neu = Anmeldung.codeAusDemPostausgang(jdbc, MAIL);

    // When / Then — der alte nur dann, wenn der Zufall nicht zweimal denselben gezogen hat.
    if (!alt.equals(neu)) {
      assertThat(gibEin(MAIL, alt).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
    assertThat(gibEin(MAIL, neu).getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void login_givenAWrongCode_thenTheAnswerSaysToRequestANewOne() {
    // Given
    fordereAn(MAIL);

    // When
    final ResponseEntity<String> antwort =
        gibEin(MAIL, daneben(Anmeldung.codeAusDemPostausgang(jdbc, MAIL)));

    // Then — Kriterium 4: die Meldung sagt, was zu tun ist.
    assertThat(antwort.getBody()).contains("neuen Code anfordern");
  }
}
