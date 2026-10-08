package org.mwolff.fbcrm.jahresabschluss.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
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
 * Die beiden Wege des Jahresabschlusses ueber HTTP, mit Sitzung und gegen eine echte
 * PostgreSQL-Instanz (Issue #294; Plan #288, E2, E3).
 *
 * <p>Hier stehen die Zusagen, die an der echten Verdrahtung haengen: Beide Wege fallen unter das
 * bestehende {@code /api/**} und sind ohne Sitzung 401; ein Jahr ohne Daten ist 404, und ein
 * gestelltes Jahr kommt mit allen Teilen seines Abschlusses und dem Namen seiner Firma ueber die
 * echte Verknuepfung Rechnung → Angebot → Firma zurueck. Dazu die Textform, die nur der
 * ObjectMapper von Spring Boot festlegt: das Jahr als {@code "JJJJ"}.
 *
 * <p>Die Zahlen im Einzelnen stehen nicht hier, sondern in {@code JahresabschlussUseCaseTest}:
 * Gegenstand dieses Pakets ist der Weg, nicht die Rechnung.
 */
class JahresabschlussControllerIT extends AbstractIntegrationTest {

  private static final String MAIL = "manne@example.org";
  private static final String PASSWORT = "richtiges-passwort";
  private static final Instant ANGELEGT = Instant.parse("2026-11-01T08:00:00Z");
  private static final String PFAD = "/api/jahresabschluesse";

  private final TestRestTemplate rest;
  private final AccountRepository accounts;
  private final PasswordHasher hasher;
  private final JdbcTemplate jdbc;

  private HttpHeaders sitzung = new HttpHeaders();

  @Autowired
  JahresabschlussControllerIT(
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
  void jahre_withoutASession_thenAnswers401() {
    // Given — der Weg faellt unter /api/**; eine eigene Regel in SecurityConfig gibt es nicht.
    sitzung = new HttpHeaders();

    // When / Then
    assertThat(ruf(PFAD, String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void abschluss_withoutASession_thenAnswers401() {
    // Given
    sitzung = new HttpHeaders();

    // When / Then
    assertThat(ruf(PFAD + "/2025", String.class).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void jahre_givenAnEmptyStock_thenAnswers200WithAnEmptyList() {
    // Given — #287, Kriterium 2: ohne Jahr mit Daten eine leere Liste.
    // When
    final ResponseEntity<JahresuebersichtResponse[]> gelesen =
        ruf(PFAD, JahresuebersichtResponse[].class);

    // Then
    assertThat(gelesen.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(gelesen.getBody()).isEmpty();
  }

  @Test
  void abschluss_givenAYearWithoutData_thenAnswers404() {
    // Given — E3: ein gestelltes Vorjahr, aber 1999 kennt weder Rechnung noch Angebot.
    gestellteRechnungAm(vorjahr().atDay(1), "RE-1");

    // When / Then
    assertThat(ruf(PFAD + "/1999", String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void jahre_givenInvoicesInTwoYears_thenOneLinePerYearNewestFirstWithTheYearAsText() {
    // Given — eine gestellte Rechnung im laufenden und eine im Vorjahr, je 100,00 netto.
    final Year laufend = Year.now(Geschaeftszone.ZONE);
    gestellteRechnungAm(vorjahr().atDay(1), "RE-1");
    gestellteRechnungAm(laufend.atDay(1), "RE-2");

    // When
    final ResponseEntity<String> roh = ruf(PFAD, String.class);
    final JahresuebersichtResponse[] jahre =
        Objects.requireNonNull(ruf(PFAD, JahresuebersichtResponse[].class).getBody());

    // Then
    assertThat(roh.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(roh.getBody()).contains("\"jahr\":\"" + laufend + "\"");
    assertThat(jahre).hasSize(2);
    assertThat(jahre[0].jahr()).isEqualTo(laufend.toString());
    assertThat(jahre[0].laeuftNoch()).isTrue();
    assertThat(jahre[1].jahr()).isEqualTo(vorjahr().toString());
    assertThat(jahre[1].laeuftNoch()).isFalse();
    assertThat(jahre[1].netto()).isEqualByComparingTo("100.00");
    assertThat(jahre[1].anzahl()).isEqualTo(1);
    assertThat(jahre[1].annahmequote()).isEqualByComparingTo("100.0");
  }

  @Test
  void abschluss_givenAnInvoicedYear_thenAnswers200WithAllParts() {
    // Given — eine gestellte Rechnung im Vorjahr ueber 100,00 netto zu 19 %, ihr Angebot bestellt.
    final Year jahr = vorjahr();
    gestellteRechnungAm(jahr.atDay(1), "RE-1");

    // When
    final ResponseEntity<JahresabschlussResponse> gelesen =
        ruf(PFAD + "/" + jahr, JahresabschlussResponse.class);

    // Then
    assertThat(gelesen.getStatusCode()).isEqualTo(HttpStatus.OK);
    final JahresabschlussResponse abschluss = Objects.requireNonNull(gelesen.getBody());
    assertThat(abschluss.jahr()).isEqualTo(jahr.toString());
    assertThat(abschluss.laeuftNoch()).isFalse();
    assertThat(abschluss.einnahmen().netto()).isEqualByComparingTo("100.00");
    assertThat(abschluss.einnahmen().brutto()).isEqualByComparingTo("119.00");
    assertThat(abschluss.einnahmen().umsatzsteuer()).isEqualByComparingTo("19.00");
    assertThat(abschluss.rechnungsstand().anzahl()).isEqualTo(1);
    assertThat(abschluss.rechnungsstand().offenAnzahl()).isEqualTo(1);
    assertThat(abschluss.steuerzeilen()).isNotNull();
    assertThat(abschluss.angebotsbilanz().abgegeben()).isEqualTo(1);
    assertThat(abschluss.angebotsbilanz().angenommen()).isEqualTo(1);
    assertThat(abschluss.kunden())
        .extracting(JahresabschlussResponse.Kundenzeilenantwort::firmaName)
        .containsExactly("Adler AG");
    assertThat(abschluss.arbeitszeit().kundenStunden()).isEqualByComparingTo("0");
    assertThat(abschluss.arbeitszeit().erloesJeStunde()).isNull();
  }

  private static Year vorjahr() {
    return Year.now(Geschaeftszone.ZONE).minusYears(1);
  }

  /**
   * Legt eine gestellte Rechnung am genannten Tag an: eine Position ueber eine Stunde zu 100,00
   * netto, Steuersatz 19 %, an ein bestelltes Angebot der Firma „Adler AG" vom selben Tag.
   *
   * <p>Direkt in der Datenbank wie in {@code StartseiteControllerIT}: Hier geht es nur darum,
   * <b>dass</b> das Jahr eine gestellte Rechnung mit bekanntem Betrag traegt.
   */
  private void gestellteRechnungAm(final LocalDate tag, final String nummer) {
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
            tag);
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
            tag,
            nummer);
    jdbc.update(
        """
        INSERT INTO rechnung_position (rechnung_id, angebot_position_id, position, bezeichnung,
                                       einheit, menge, einzelpreis)
        VALUES (?, ?, 1, 'Beratung', 'STUNDE', 1, 100.00)
        """,
        rechnungId,
        angebotPositionId);
  }
}
