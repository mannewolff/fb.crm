package org.mwolff.fbcrm.startseite.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.YearMonth;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.PasswordHasher;
import org.mwolff.fbcrm.auth.domain.Role;
import org.mwolff.fbcrm.common.Geschaeftszone;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Der eine Weg der Startseite ueber HTTP, mit Sitzung und gegen eine echte PostgreSQL-Instanz
 * (Issue #214).
 *
 * <p>Hier stehen die Zusagen, die an der echten Verdrahtung haengen: Der Weg faellt unter das
 * bestehende {@code /api/**} und ist ohne Sitzung 401, ein Monatswert, der kein Monat ist, ist eine
 * fehlerhafte Anfrage, und angemeldet antwortet er mit den waehlbaren Monaten, unter denen der
 * geltende steht (Plan #208, E17; #273, Kriterium 2).
 *
 * <p>Die Zahlen selbst stehen nicht hier, sondern in {@code StartseiteUseCaseTest}: Gegenstand
 * dieses Pakets ist der Weg, nicht die Rechnung. Darum genuegt meist der leere Bestand — er zeigt,
 * dass die Startseite auch ohne einen einzigen Datensatz antwortet und nicht auf eine leere Liste
 * faellt. Nur wo ein Monat waehlbar sein muss, liegt eine gestellte Rechnung darin.
 */
class StartseiteControllerIT extends AbstractIntegrationTest {

  private static final String MAIL = "manne@example.org";
  private static final String PASSWORT = "richtiges-passwort";
  private static final Instant ANGELEGT = Instant.parse("2026-11-01T08:00:00Z");
  private static final String PFAD = "/api/startseite";

  private final TestRestTemplate rest;
  private final AccountRepository accounts;
  private final PasswordHasher hasher;
  private final JdbcTemplate jdbc;

  private HttpHeaders sitzung = new HttpHeaders();

  @Autowired
  StartseiteControllerIT(
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
    jdbc.execute(
        "TRUNCATE arbeitszeit, rechnung_position, rechnung, angebot_position, angebot,"
            + " ansprechpartner, firma RESTART IDENTITY CASCADE");
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

  private <T> ResponseEntity<T> ruf(final String pfad, final Class<T> typ) {
    return rest.exchange(pfad, HttpMethod.GET, new HttpEntity<>(null, sitzung), typ);
  }

  @Test
  void stand_withoutASession_thenAnswers401() {
    // Given — der Weg faellt unter /api/**; eine eigene Regel in SecurityConfig gibt es nicht.
    sitzung = new HttpHeaders();

    // When / Then
    assertThat(ruf(PFAD, String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void stand_givenAMonthThatIsNoMonth_thenAnswers400() {
    // Given — „unsinn" ist kein Monat; das ist eine fehlerhafte Anfrage und kein Serverfehler.
    // When / Then
    assertThat(ruf(PFAD + "?monat=unsinn", String.class).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void stand_whenSignedIn_thenAnswers200WithTheLaufenderMonthAsTheOnlySelectableOne() {
    // Given — ein leerer Bestand: zur Wahl steht allein der laufende Monat (#273, Kriterium 2).
    // When
    final ResponseEntity<StartseiteResponse> gelesen = ruf(PFAD, StartseiteResponse.class);

    // Then
    assertThat(gelesen.getStatusCode()).isEqualTo(HttpStatus.OK);
    final StartseiteResponse stand = Objects.requireNonNull(gelesen.getBody());
    assertThat(stand.monat()).isEqualTo(YearMonth.now(Geschaeftszone.ZONE));
    assertThat(stand.monate()).containsExactly(stand.monat());
  }

  @Test
  void stand_givenAnEmptyStock_thenAllThreeFiguresAreEmptyAndNotAbsent() {
    // Given — ohne einen einzigen Datensatz antwortet die Startseite trotzdem vollstaendig.
    // When
    final StartseiteResponse stand =
        Objects.requireNonNull(ruf(PFAD, StartseiteResponse.class).getBody());

    // Then
    assertThat(stand.inArbeit()).isEmpty();
    assertThat(stand.nichtAbgerechnet().angebote()).isEmpty();
    assertThat(stand.nichtAbgerechnet().netto()).isEqualByComparingTo("0.00");
    assertThat(stand.nichtAbgerechnet().erfasstImMonat()).isEqualByComparingTo("0.00");
    assertThat(stand.abgerechnet().anzahl()).isZero();
    assertThat(stand.interneStundenImMonat()).isEqualByComparingTo("0");
  }

  @Test
  void stand_givenASelectableMonth_thenThatMonthIsInForce() {
    // Given — eine gestellte Rechnung im Vormonat macht ihn waehlbar (#273, Kriterium 2; Plan
    // #208, E18).
    final YearMonth laufend = YearMonth.now(Geschaeftszone.ZONE);
    final YearMonth vormonat = laufend.minusMonths(1);
    gestellteRechnungAm(vormonat);

    // When
    final StartseiteResponse stand =
        Objects.requireNonNull(
            ruf(PFAD + "?monat=" + vormonat, StartseiteResponse.class).getBody());

    // Then
    assertThat(stand.monat()).isEqualTo(vormonat);
    assertThat(stand.monate()).containsExactly(laufend, vormonat);
  }

  @Test
  void stand_givenAMonthWithoutAnything_thenTheLaufenderMonthIsInForce() {
    // Given — im Vormonat geschah nichts; er steht nicht zur Wahl (#273, Kriterium 2).
    final YearMonth laufend = YearMonth.now(Geschaeftszone.ZONE);

    // When
    final StartseiteResponse stand =
        Objects.requireNonNull(
            ruf(PFAD + "?monat=" + laufend.minusMonths(1), StartseiteResponse.class).getBody());

    // Then
    assertThat(stand.monat()).isEqualTo(laufend);
    assertThat(stand.monate()).containsExactly(laufend);
  }

  @Test
  void stand_thenTheMonthsAreWrittenAsTextNewestFirst() {
    // Given — die Oberflaeche liest den Monat als Text „JJJJ-MM". Welche Form der ObjectMapper von
    // Spring Boot schreibt, laesst sich nur hier belegen.
    final YearMonth laufend = YearMonth.now(Geschaeftszone.ZONE);
    final YearMonth vormonat = laufend.minusMonths(1);
    gestellteRechnungAm(vormonat);

    // When
    final ResponseEntity<String> gelesen = ruf(PFAD, String.class);

    // Then
    assertThat(gelesen.getBody())
        .contains("\"monat\":\"" + laufend + "\"")
        .contains("\"monate\":[\"" + laufend + "\",\"" + vormonat + "\"]");
  }

  /**
   * Legt eine gestellte Rechnung mit Rechnungsdatum im genannten Monat an.
   *
   * <p>Direkt in der Datenbank und nicht ueber den Weg des Stellens, wie in {@code
   * RechnungseinstellungenIT}: Hier geht es nur darum, <b>dass</b> der Monat eine gestellte
   * Rechnung traegt. Der Weg des Stellens braeuchte Positionen und eigene Angaben und erzeugte ein
   * Dokument im Objektspeicher, das niemand liest.
   */
  private void gestellteRechnungAm(final YearMonth monat) {
    final Long firmaId =
        jdbc.queryForObject(
            "INSERT INTO firma (name) VALUES ('Adler AG') RETURNING id", Long.class);
    final Long angebotId =
        jdbc.queryForObject(
            """
            INSERT INTO angebot (firma_id, status, angebot_datum)
            VALUES (?, 'BESTELLT', ?) RETURNING id
            """,
            Long.class,
            firmaId,
            monat.atDay(1));
    jdbc.update(
        """
        INSERT INTO rechnung (angebot_id, zustand, rechnung_datum, nummer, steuersatz,
                              zahlungsziel_tage, gestellt_am, empfaenger_firma, absender_name)
        VALUES (?, 'GESTELLT', ?, 'RE-1', 19.00, 10, now(), 'Adler AG', 'Manfred Wolff')
        """,
        angebotId,
        monat.atDay(1));
  }
}
