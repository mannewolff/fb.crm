package org.mwolff.fbcrm.startseite.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.Year;
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
 * bestehende {@code /api/**} und ist ohne Sitzung 401, ein Wert, der kein Zeitraum ist, ist eine
 * fehlerhafte Anfrage — ueber den {@link ZeitraumConverter} als Bean (Plan #274, E3) —, und
 * angemeldet antwortet er mit den waehlbaren Jahren und Monaten, unter denen der geltende Zeitraum
 * steht (#273, Kriterien 1 und 2). Dazu die Textform, die nur der ObjectMapper von Spring Boot
 * festlegt: Monat als {@code JJJJ-MM}, Jahr als {@code JJJJ}, die Jahre als Text (E10).
 *
 * <p>Die Zahlen im Einzelnen stehen nicht hier, sondern in {@code StartseiteUseCaseTest}:
 * Gegenstand dieses Pakets ist der Weg, nicht die Rechnung. Darum genuegt meist der leere Bestand —
 * er zeigt, dass die Startseite auch ohne einen einzigen Datensatz antwortet und nicht auf eine
 * leere Liste faellt. Nur wo ein Monat waehlbar sein muss oder die Jahreswahl ihre Monate zeigen
 * soll, liegen gestellte Rechnungen darin.
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
  void stand_givenAValueThatIsNoZeitraum_thenAnswers400() {
    // Given — „unsinn" ist kein Zeitraum; das ist eine fehlerhafte Anfrage und kein Serverfehler.
    // When / Then
    assertThat(ruf(PFAD + "?zeitraum=unsinn", String.class).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void stand_whenSignedIn_thenAnswers200WithTheLaufenderMonthAsTheOnlySelectableOne() {
    // Given — ein leerer Bestand: zur Wahl stehen allein der laufende Monat und das laufende Jahr
    // (#273, Kriterien 1 und 2).
    final YearMonth laufend = YearMonth.now(Geschaeftszone.ZONE);

    // When
    final ResponseEntity<StartseiteResponse> gelesen = ruf(PFAD, StartseiteResponse.class);

    // Then
    assertThat(gelesen.getStatusCode()).isEqualTo(HttpStatus.OK);
    final StartseiteResponse stand = Objects.requireNonNull(gelesen.getBody());
    assertThat(stand.zeitraum())
        .isEqualTo(new StartseiteResponse.ZeitraumResponse("MONAT", laufend.toString()));
    assertThat(stand.waehlbar().monate()).containsExactly(laufend);
    assertThat(stand.waehlbar().jahre()).containsExactly(String.valueOf(laufend.getYear()));
  }

  @Test
  void stand_givenAnEmptyStock_thenAllFiguresAreZeroAndNotAbsent() {
    // Given — ohne einen einzigen Datensatz antwortet die Startseite trotzdem vollstaendig.
    // When
    final StartseiteResponse stand =
        Objects.requireNonNull(ruf(PFAD, StartseiteResponse.class).getBody());

    // Then
    assertThat(stand.inArbeit()).isEmpty();
    assertThat(stand.nichtAbgerechnet().angebote()).isEmpty();
    assertThat(stand.nichtAbgerechnet().netto()).isEqualByComparingTo("0.00");
    assertThat(stand.nichtAbgerechnet().erfasstImZeitraum()).isEqualByComparingTo("0.00");
    assertThat(stand.abgerechnet().netto()).isEqualByComparingTo("0.00");
    assertThat(stand.abgerechnet().brutto()).isEqualByComparingTo("0.00");
    assertThat(stand.abgerechnet().anzahl()).isZero();
    assertThat(stand.abgerechnet().monate()).isEmpty();
    assertThat(stand.interneStundenImZeitraum()).isEqualByComparingTo("0.00");
  }

  @Test
  void stand_givenASelectableMonth_thenThatMonthIsInForce() {
    // Given — eine gestellte Rechnung im Vormonat macht ihn waehlbar (#273, Kriterium 2; Plan
    // #208, E18).
    final YearMonth laufend = YearMonth.now(Geschaeftszone.ZONE);
    final YearMonth vormonat = laufend.minusMonths(1);
    gestellteRechnungAm(vormonat, "RE-1", "1");

    // When
    final StartseiteResponse stand =
        Objects.requireNonNull(
            ruf(PFAD + "?zeitraum=" + vormonat, StartseiteResponse.class).getBody());

    // Then
    assertThat(stand.zeitraum().wert()).isEqualTo(vormonat.toString());
    assertThat(stand.waehlbar().monate()).containsExactly(laufend, vormonat);
  }

  @Test
  void stand_givenAMonthWithoutAnything_thenTheLaufenderMonthIsInForce() {
    // Given — im Vormonat geschah nichts; er steht nicht zur Wahl (#273, Kriterium 2).
    final YearMonth laufend = YearMonth.now(Geschaeftszone.ZONE);

    // When
    final StartseiteResponse stand =
        Objects.requireNonNull(
            ruf(PFAD + "?zeitraum=" + laufend.minusMonths(1), StartseiteResponse.class).getBody());

    // Then
    assertThat(stand.zeitraum().wert()).isEqualTo(laufend.toString());
    assertThat(stand.waehlbar().monate()).containsExactly(laufend);
  }

  @Test
  void stand_givenAMonat_thenTheZeitraumIsWrittenAsMonatAndJjjjMm() {
    // Given — E10: Art und Wert als Text, wie die Ansicht sie in Adresse und option value setzt.
    final YearMonth laufend = YearMonth.now(Geschaeftszone.ZONE);

    // When
    final ResponseEntity<String> gelesen = ruf(PFAD + "?zeitraum=" + laufend, String.class);

    // Then
    assertThat(gelesen.getBody())
        .contains("\"zeitraum\":{\"art\":\"MONAT\",\"wert\":\"" + laufend + "\"}");
  }

  @Test
  void stand_givenAJahr_thenTheZeitraumIsWrittenAsJahrAndJjjj() {
    // Given — das laufende Jahr steht immer zur Wahl (#273, Kriterium 1).
    final Year jahr = Year.now(Geschaeftszone.ZONE);

    // When
    final ResponseEntity<String> gelesen = ruf(PFAD + "?zeitraum=" + jahr, String.class);

    // Then
    assertThat(gelesen.getBody())
        .contains("\"zeitraum\":{\"art\":\"JAHR\",\"wert\":\"" + jahr + "\"}");
  }

  @Test
  void stand_thenTheSelectableOnesAreWrittenAsTextNewestFirst() {
    // Given — die Oberflaeche liest Monat und Jahr als Text; ein Year schriebe Jackson als Zahl
    // (E10). Welche Form der ObjectMapper von Spring Boot schreibt, laesst sich nur hier belegen.
    final YearMonth laufend = YearMonth.now(Geschaeftszone.ZONE);
    final YearMonth vormonat = laufend.minusMonths(1);
    gestellteRechnungAm(vormonat, "RE-1", "1");

    // When
    final ResponseEntity<String> gelesen = ruf(PFAD, String.class);

    // Then
    assertThat(gelesen.getBody())
        .contains("\"jahre\":[\"" + laufend.getYear() + "\"")
        .contains("\"monate\":[\"" + laufend + "\",\"" + vormonat + "\"]");
  }

  @Test
  void stand_givenAJahrWithInvoicesInTwoMonths_thenTheSumAndOneLinePerMonthOldestFirst() {
    // Given — #273, Kriterien 4 und 7: gestellt im Januar 1 h und im Februar 2 h zu je 100,00.
    final Year jahr = Year.now(Geschaeftszone.ZONE);
    final YearMonth januar = jahr.atMonth(1);
    final YearMonth februar = jahr.atMonth(2);
    gestellteRechnungAm(februar, "RE-2", "2");
    gestellteRechnungAm(januar, "RE-1", "1");

    // When
    final StartseiteResponse stand =
        Objects.requireNonNull(ruf(PFAD + "?zeitraum=" + jahr, StartseiteResponse.class).getBody());

    // Then
    assertThat(stand.zeitraum().art()).isEqualTo("JAHR");
    assertThat(stand.abgerechnet().netto()).isEqualByComparingTo("300.00");
    assertThat(stand.abgerechnet().brutto()).isEqualByComparingTo("357.00");
    assertThat(stand.abgerechnet().anzahl()).isEqualTo(2);
    assertThat(stand.abgerechnet().monate())
        .extracting(StartseiteResponse.Monatszeile::monat)
        .containsExactly(januar, februar);
    assertThat(stand.abgerechnet().monate().get(0).netto()).isEqualByComparingTo("100.00");
    assertThat(stand.abgerechnet().monate().get(0).brutto()).isEqualByComparingTo("119.00");
    assertThat(stand.abgerechnet().monate().get(0).anzahl()).isEqualTo(1);
    assertThat(stand.abgerechnet().monate().get(1).netto()).isEqualByComparingTo("200.00");
    assertThat(stand.abgerechnet().monate().get(1).brutto()).isEqualByComparingTo("238.00");
  }

  @Test
  void stand_givenAMonatWithAnInvoice_thenTheMonthLinesAreEmpty() {
    // Given — E11: bei Monatswahl steht die leere Liste und nicht null.
    final YearMonth januar = Year.now(Geschaeftszone.ZONE).atMonth(1);
    gestellteRechnungAm(januar, "RE-1", "1");

    // When
    final ResponseEntity<String> gelesen = ruf(PFAD + "?zeitraum=" + januar, String.class);

    // Then
    assertThat(gelesen.getBody())
        .contains("\"zeitraum\":{\"art\":\"MONAT\",\"wert\":\"" + januar + "\"}")
        .contains("\"monate\":[]");
  }

  /**
   * Legt eine gestellte Rechnung mit Rechnungsdatum im genannten Monat an: eine Position ueber die
   * genannten Stunden zu 100,00 netto, Steuersatz 19 %.
   *
   * <p>Direkt in der Datenbank und nicht ueber den Weg des Stellens, wie in {@code
   * RechnungseinstellungenIT}: Hier geht es nur darum, <b>dass</b> der Monat eine gestellte
   * Rechnung mit bekanntem Betrag traegt. Der Weg des Stellens braeuchte eigene Angaben und
   * erzeugte ein Dokument im Objektspeicher, das niemand liest.
   */
  private void gestellteRechnungAm(
      final YearMonth monat, final String nummer, final String stunden) {
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
    final Long angebotPositionId =
        jdbc.queryForObject(
            """
            INSERT INTO angebot_position
                (angebot_id, position, bezeichnung, abrechnungsmodus, menge, einheit, einzelpreis)
            VALUES (?, 1, 'Beratung', 'AUFWAND', 10, 'STUNDE', 100.00) RETURNING id
            """,
            Long.class,
            angebotId);
    final Long rechnungId =
        jdbc.queryForObject(
            """
            INSERT INTO rechnung (angebot_id, zustand, rechnung_datum, nummer, steuersatz,
                                  zahlungsziel_tage, gestellt_am, empfaenger_firma, absender_name)
            VALUES (?, 'GESTELLT', ?, ?, 19.00, 10, now(), 'Adler AG', 'Manfred Wolff')
            RETURNING id
            """,
            Long.class,
            angebotId,
            monat.atDay(1),
            nummer);
    jdbc.update(
        """
        INSERT INTO rechnung_position (rechnung_id, angebot_position_id, position, bezeichnung,
                                       einheit, menge, einzelpreis)
        VALUES (?, ?, 1, 'Beratung', 'STUNDE', CAST(? AS numeric), 100.00)
        """,
        rechnungId,
        angebotPositionId,
        stunden);
  }
}
