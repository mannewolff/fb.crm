package org.mwolff.fbcrm.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.auth.Anmeldung;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Die Einrichtung ohne Passwort, mit Einmal-Schluessel, Anzeigename, Adresse und Code (Kriterium
 * 6).
 *
 * <p>Mit eingeschaltetem Mailversand steht der Code im Zustellauftrag an die eingegebene Adresse —
 * eine vertippte Adresse bekommt den Code, nicht der Betreiber, und es entsteht kein Konto. Mit
 * ausgeschaltetem Versand ({@link OhneMailversand}) kommt der Code aus dem Protokoll, und die
 * Adresse wird dabei nicht geprueft.
 */
@TestPropertySource(
    properties = {
      "fbcrm.setup.bootstrap-token=" + SetupControllerIT.SCHLUESSEL,
      "fbcrm.mail.enabled=true",
      "fbcrm.auth.login-max-attempts=1000"
    })
class EinrichtungOhnePasswortIT extends AbstractIntegrationTest {

  private static final String MAIL = SetupControllerIT.MAIL;
  private static final String VERTIPPT = "betreiber@exmaple.org";

  private final TestRestTemplate rest;
  private final JdbcTemplate jdbc;

  @Autowired
  EinrichtungOhnePasswortIT(final TestRestTemplate rest, final JdbcTemplate jdbc) {
    this.rest = rest;
    this.jdbc = jdbc;
  }

  @BeforeEach
  void frischeInstanz() {
    SetupControllerIT.leere(jdbc);
  }

  private long konten() {
    return Objects.requireNonNull(jdbc.queryForObject("SELECT count(*) FROM account", Long.class));
  }

  private long auftraege() {
    return Objects.requireNonNull(
        jdbc.queryForObject("SELECT count(*) FROM outbox_message", Long.class));
  }

  private long codes() {
    return Objects.requireNonNull(
        jdbc.queryForObject("SELECT count(*) FROM login_code", Long.class));
  }

  /** Bestaetigt und prueft, dass das Konto steht und die Sitzung traegt. */
  private void bestaetigeUndPruefeDieSitzung(final String code) {
    bestaetigeUndPruefeDieSitzung(rest, code);
  }

  /** Dasselbe ueber den Client eines anderen Anwendungskontexts. */
  private void bestaetigeUndPruefeDieSitzung(final TestRestTemplate client, final String code) {
    final ResponseEntity<String> antwort =
        SetupControllerIT.richteEin(
            client, SetupControllerIT.rumpf(MAIL, code, SetupControllerIT.SCHLUESSEL));
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(konten()).isEqualTo(1);
    final ResponseEntity<String> ich =
        client.exchange(
            "/api/auth/me",
            HttpMethod.GET,
            new HttpEntity<>(Anmeldung.mitCookie(antwort)),
            String.class);
    assertThat(ich.getBody())
        .contains("\"email\":\"" + MAIL + "\"")
        .contains("\"displayName\":\"" + SetupControllerIT.NAME + "\"");
  }

  @Test
  @DisplayName("Szenario: Einrichtung ohne Passwort")
  void szenarioEinrichtungOhnePasswort_thenAccountCreatedAndLoggedIn() {
    // Given — frische Instanz, richtiger Schluessel, Mailversand eingeschaltet.
    final ResponseEntity<String> anforderung =
        SetupControllerIT.fordereCodeAn(rest, MAIL, SetupControllerIT.SCHLUESSEL);
    assertThat(anforderung.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(konten()).isZero();

    // When / Then — der Code aus der Mail legt das Konto an und meldet an.
    bestaetigeUndPruefeDieSitzung(Anmeldung.codeAusDemPostausgang(jdbc, MAIL));
  }

  @Test
  void requestCode_givenAWrongKey_thenSendsNoCodeAndRefusesAsBefore() {
    // When — Kriterium 6: mit falschem Schluessel verschickt fb.crm keinen Code.
    final ResponseEntity<String> antwort =
        SetupControllerIT.fordereCodeAn(rest, MAIL, "falscher-schluessel");

    // Then — die Ablehnung wie heute: 403 mit dem Text von SetupRefused.
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(antwort.getBody()).contains("Die Einrichtung ist nicht moeglich.");
    assertThat(auftraege()).isZero();
    assertThat(codes()).isZero();
  }

  @Test
  void initialize_givenAMistypedAddress_thenNoAccountAndTheSetupSucceedsWithTheRightOne() {
    // Given — der Code ging an die vertippte Adresse; der Betreiber hat ihn nie gesehen.
    SetupControllerIT.fordereCodeAn(rest, VERTIPPT, SetupControllerIT.SCHLUESSEL);
    assertThat(Anmeldung.codeAusDemPostausgang(jdbc, VERTIPPT)).isNotBlank();

    // When — er gibt die richtige Adresse und irgendeinen Code ein.
    final ResponseEntity<String> geraten =
        SetupControllerIT.richteEin(
            rest, SetupControllerIT.rumpf(MAIL, "000000", SetupControllerIT.SCHLUESSEL));

    // Then — kein Konto; mit der richtigen Adresse gelingt die Einrichtung danach.
    assertThat(geraten.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(konten()).isZero();
    SetupControllerIT.fordereCodeAn(rest, MAIL, SetupControllerIT.SCHLUESSEL);
    bestaetigeUndPruefeDieSitzung(Anmeldung.codeAusDemPostausgang(jdbc, MAIL));
  }

  /**
   * Die Einrichtung mit ausgeschaltetem Mailversand: Der Code steht im Protokoll (Kriterium 6,
   * letzter Satz).
   *
   * <p>Eigene Schalter heissen eigener Anwendungskontext; die Schalter der umgebenden Klasse gelten
   * weiter, nur der Versand ist hier aus. Darum spricht die Klasse ueber ihren eigenen Client
   * {@link #ohneVersand}: Der Client der umgebenden Instanz gehoert zu deren Kontext, mit Versand.
   */
  @Nested
  @TestPropertySource(properties = "fbcrm.mail.enabled=false")
  class OhneMailversand {

    private static final Pattern CODE_IM_PROTOKOLL = Pattern.compile("Einmalcode (\\d{6})");

    private final ListAppender<ILoggingEvent> mitgeschrieben = new ListAppender<>();

    private final TestRestTemplate ohneVersand;

    @Autowired
    OhneMailversand(final TestRestTemplate kontext) {
      this.ohneVersand = kontext;
    }

    /** Fordert den Code an und liest ihn aus der Mitschrift des Wurzel-Loggers. */
    private String codeAusDemProtokoll() {
      final ch.qos.logback.classic.Logger wurzel =
          ((LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory())
              .getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
      mitgeschrieben.setContext(wurzel.getLoggerContext());
      mitgeschrieben.start();
      wurzel.addAppender(mitgeschrieben);
      try {
        final ResponseEntity<String> antwort =
            SetupControllerIT.fordereCodeAn(ohneVersand, MAIL, SetupControllerIT.SCHLUESSEL);
        assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
      } finally {
        wurzel.detachAppender(mitgeschrieben);
        mitgeschrieben.stop();
      }
      return mitgeschrieben.list.stream()
          .map(ILoggingEvent::getFormattedMessage)
          .map(CODE_IM_PROTOKOLL::matcher)
          .filter(Matcher::find)
          .map(fund -> fund.group(1))
          .findFirst()
          .orElseThrow();
    }

    @Test
    void initialize_withMailDisabled_thenSucceedsWithTheCodeFromTheLog() {
      // Given — ohne Versand entsteht kein Zustellauftrag.
      final String code = codeAusDemProtokoll();
      assertThat(auftraege()).isZero();

      // When / Then
      bestaetigeUndPruefeDieSitzung(ohneVersand, code);
    }

    @Test
    void requestCode_withMailDisabled_thenAnswersLikeWithMail() {
      // When — die Antwort sagt nicht, ob der Versand laeuft (E17).
      final ResponseEntity<String> antwort =
          ohneVersand.postForEntity(
              "/api/setup/code",
              Map.of(
                  "email",
                  MAIL,
                  "displayName",
                  SetupControllerIT.NAME,
                  "bootstrapToken",
                  SetupControllerIT.SCHLUESSEL),
              String.class);

      // Then
      assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }
  }
}
