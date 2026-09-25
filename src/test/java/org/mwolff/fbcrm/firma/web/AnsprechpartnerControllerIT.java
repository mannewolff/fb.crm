package org.mwolff.fbcrm.firma.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Die Wege des Ansprechpartners ueber HTTP, mit Sitzung und gegen eine echte PostgreSQL-Instanz.
 *
 * <p>Der durchgehende Weg — anlegen, aendern, stilllegen, wieder aktivieren — steht als Kette, und
 * abgelesen wird er jedes Mal an der Detailantwort der Firma: Einen eigenen Leseweg zum
 * Ansprechpartner gibt es nicht (E7), und erst die Detailantwort zeigt, dass der Stand zwischen
 * zwei Aufrufen haelt.
 *
 * <p>Hier steht auch der Nachweis zu Kriterium 16: Das Stilllegen der Firma laesst den Stand ihrer
 * Ansprechpartner unberuehrt.
 */
class AnsprechpartnerControllerIT extends AbstractIntegrationTest {

  private static final String MAIL = "manne@example.org";
  private static final String PASSWORT = "richtiges-passwort";
  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");

  private final TestRestTemplate rest;
  private final AccountRepository accounts;
  private final PasswordHasher hasher;
  private final JdbcTemplate jdbc;

  private HttpHeaders sitzung = new HttpHeaders();

  @Autowired
  AnsprechpartnerControllerIT(
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
  void leereDenBestandUndMeldeAn() {
    jdbc.execute("TRUNCATE ansprechpartner, firma RESTART IDENTITY CASCADE");
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
      final String pfad, final HttpMethod methode, final Object rumpf, final Class<T> typ) {
    return rest.exchange(pfad, methode, new HttpEntity<>(rumpf, sitzung), typ);
  }

  private long legeFirmaAn(final String name) {
    final ResponseEntity<FirmaResponse> antwort =
        ruf("/api/firmen", HttpMethod.POST, Map.of("name", name), FirmaResponse.class);
    return Objects.requireNonNull(antwort.getBody()).id();
  }

  private static Map<String, String> rumpf(final String nachname) {
    return Map.of(
        "vorname", "Max",
        "nachname", nachname,
        "rolle", "Einkauf",
        "email", "max@firma.de",
        "telefonFestnetz", "0421 1234",
        "telefonMobil", "0170 1234");
  }

  private ResponseEntity<AnsprechpartnerResponse> anlegenAntwort(
      final long firmaId, final Object rumpf) {
    return ruf(
        "/api/firmen/" + firmaId + "/ansprechpartner",
        HttpMethod.POST,
        rumpf,
        AnsprechpartnerResponse.class);
  }

  private long legePartnerAn(final long firmaId, final String nachname) {
    return Objects.requireNonNull(anlegenAntwort(firmaId, rumpf(nachname)).getBody()).id();
  }

  private void schaltePartner(final long firmaId, final long id, final String weg) {
    ruf(
        "/api/firmen/" + firmaId + "/ansprechpartner/" + id + "/" + weg,
        HttpMethod.POST,
        null,
        Void.class);
  }

  private FirmaResponse detail(final long firmaId) {
    return Objects.requireNonNull(
        rest.exchange(
                "/api/firmen/" + firmaId,
                HttpMethod.GET,
                new HttpEntity<>(sitzung),
                FirmaResponse.class)
            .getBody());
  }

  @Test
  void anlegen_thenAnswersCreated() {
    // Given
    final long firmaId = legeFirmaAn("Adler AG");

    // When — Kriterium 9.
    final ResponseEntity<AnsprechpartnerResponse> antwort =
        anlegenAntwort(firmaId, rumpf("Mueller"));

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

  @Test
  void anlegen_thenTheFirmaDetailShowsTheContact() {
    // Given
    final long firmaId = legeFirmaAn("Adler AG");
    final long id = legePartnerAn(firmaId, "Mueller");

    // When
    final FirmaResponse gelesen = detail(firmaId);

    // Then
    assertThat(gelesen.ansprechpartner())
        .containsExactly(
            new AnsprechpartnerResponse(
                id, "Max", "Mueller", "Einkauf", "max@firma.de", "0421 1234", "0170 1234", true));
  }

  @Test
  void aendern_thenTheFirmaDetailShowsTheNewValues() {
    // Given
    final long firmaId = legeFirmaAn("Adler AG");
    final long id = legePartnerAn(firmaId, "Mueller");

    // When — Kriterium 11.
    ruf(
        "/api/firmen/" + firmaId + "/ansprechpartner/" + id,
        HttpMethod.PUT,
        rumpf("Maier"),
        Void.class);

    // Then
    assertThat(detail(firmaId).ansprechpartner())
        .extracting(AnsprechpartnerResponse::nachname)
        .containsExactly("Maier");
  }

  @Test
  void stilllegen_thenTheFirmaDetailShowsTheContactAsRetired() {
    // Given
    final long firmaId = legeFirmaAn("Adler AG");
    final long id = legePartnerAn(firmaId, "Mueller");

    // When — Kriterium 15.
    schaltePartner(firmaId, id, "stilllegen");

    // Then
    assertThat(detail(firmaId).ansprechpartner())
        .extracting(AnsprechpartnerResponse::aktiv)
        .containsExactly(false);
  }

  @Test
  void aktivieren_thenTheFirmaDetailShowsTheContactAsActiveAgain() {
    // Given
    final long firmaId = legeFirmaAn("Adler AG");
    final long id = legePartnerAn(firmaId, "Mueller");
    schaltePartner(firmaId, id, "stilllegen");

    // When — Kriterium 15: der Weg zurueck.
    schaltePartner(firmaId, id, "aktivieren");

    // Then
    assertThat(detail(firmaId).ansprechpartner())
        .extracting(AnsprechpartnerResponse::aktiv)
        .containsExactly(true);
  }

  @Test
  void anlegen_givenABodyWithAFirmaId_thenTakesTheFirmaFromThePath() {
    // Given — Kriterium 12, E7: der Rumpf traegt kein Feld fuer die Firma, und ein
    // untergeschobenes wird nicht gelesen.
    final long adler = legeFirmaAn("Adler AG");
    final long baum = legeFirmaAn("Baum GmbH");
    final Map<String, Object> mitFremderFirma = new LinkedHashMap<>(rumpf("Mueller"));
    mitFremderFirma.put("firmaId", baum);

    // When
    anlegenAntwort(adler, mitFremderFirma);

    // Then
    assertThat(detail(baum).ansprechpartner()).isEmpty();
  }

  @Test
  void anlegen_givenABodyWithAFirmaId_thenTheContactBelongsToThePathFirma() {
    // Given
    final long adler = legeFirmaAn("Adler AG");
    final long baum = legeFirmaAn("Baum GmbH");
    final Map<String, Object> mitFremderFirma = new LinkedHashMap<>(rumpf("Mueller"));
    mitFremderFirma.put("firmaId", baum);

    // When
    anlegenAntwort(adler, mitFremderFirma);

    // Then
    assertThat(detail(adler).ansprechpartner())
        .extracting(AnsprechpartnerResponse::nachname)
        .containsExactly("Mueller");
  }

  @Test
  void anlegen_givenAnUnknownFirma_thenAnswersNotFound() {
    // When
    final ResponseEntity<String> antwort =
        ruf("/api/firmen/4711/ansprechpartner", HttpMethod.POST, rumpf("Mueller"), String.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void aendern_givenAnIdOfAnotherFirma_thenAnswersNotFound() {
    // Given — Kriterium 12.
    final long adler = legeFirmaAn("Adler AG");
    final long baum = legeFirmaAn("Baum GmbH");
    final long id = legePartnerAn(adler, "Mueller");

    // When
    final ResponseEntity<String> antwort =
        ruf(
            "/api/firmen/" + baum + "/ansprechpartner/" + id,
            HttpMethod.PUT,
            rumpf("Maier"),
            String.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void anlegen_givenARetiredFirma_thenIsStillPossible() {
    // Given — Kriterium 14.
    final long firmaId = legeFirmaAn("Adler AG");
    ruf("/api/firmen/" + firmaId + "/stilllegen", HttpMethod.POST, null, Void.class);

    // When
    final ResponseEntity<AnsprechpartnerResponse> antwort =
        anlegenAntwort(firmaId, rumpf("Mueller"));

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

  @Test
  void anlegen_givenAMalformedEmail_thenAnswersBadRequest() {
    // Given
    final long firmaId = legeFirmaAn("Adler AG");

    // When — E8: „max@firma" nimmt @Email an, dieses Projekt nicht.
    final ResponseEntity<String> antwort =
        ruf(
            "/api/firmen/" + firmaId + "/ansprechpartner",
            HttpMethod.POST,
            Map.of("nachname", "Mueller", "email", "max@firma"),
            String.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void firmaStilllegenUndAktivieren_thenTheContactsKeepTheirOwnState() {
    // Given — Kriterium 16: ein aktiver und ein stillgelegter Ansprechpartner.
    final long firmaId = legeFirmaAn("Adler AG");
    legePartnerAn(firmaId, "Aktiv");
    final long ruhend = legePartnerAn(firmaId, "Ruhend");
    schaltePartner(firmaId, ruhend, "stilllegen");

    // When — die Firma geht in beide Richtungen.
    ruf("/api/firmen/" + firmaId + "/stilllegen", HttpMethod.POST, null, Void.class);
    ruf("/api/firmen/" + firmaId + "/aktivieren", HttpMethod.POST, null, Void.class);

    // Then — der Stilllegungsstand gehoert dem Ansprechpartner allein (E2).
    assertThat(detail(firmaId).ansprechpartner())
        .extracting(AnsprechpartnerResponse::nachname, AnsprechpartnerResponse::aktiv)
        .containsExactly(tuple("Aktiv", true), tuple("Ruhend", false));
  }

  @Test
  void firmaStilllegen_thenTheContactsAreUntouched() {
    // Given — Kriterium 16, die Zwischenlage: die Firma ruht, die Ansprechpartner nicht.
    final long firmaId = legeFirmaAn("Adler AG");
    legePartnerAn(firmaId, "Aktiv");
    final long ruhend = legePartnerAn(firmaId, "Ruhend");
    schaltePartner(firmaId, ruhend, "stilllegen");

    // When
    ruf("/api/firmen/" + firmaId + "/stilllegen", HttpMethod.POST, null, Void.class);

    // Then
    assertThat(detail(firmaId).ansprechpartner())
        .extracting(AnsprechpartnerResponse::nachname, AnsprechpartnerResponse::aktiv)
        .containsExactly(tuple("Aktiv", true), tuple("Ruhend", false));
  }
}
