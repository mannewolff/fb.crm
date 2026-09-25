package org.mwolff.fbcrm.firma.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.PasswordHasher;
import org.mwolff.fbcrm.auth.domain.Role;
import org.mwolff.fbcrm.firma.domain.Ansprechpartner;
import org.mwolff.fbcrm.firma.domain.AnsprechpartnerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Die Wege der Firma ueber HTTP, mit Sitzung und gegen eine echte PostgreSQL-Instanz.
 *
 * <p>Der durchgehende Weg — anlegen, lesen, aendern, stilllegen, in der Uebersicht suchen, wieder
 * aktivieren — steht hier als Kette und nicht als Sammlung von Einzelproben: Erst die Kette zeigt,
 * dass der Stilllegungsstand zwischen zwei Aufrufen haelt (Kriterium 13).
 */
class FirmaControllerIT extends AbstractIntegrationTest {

  private static final String MAIL = "manne@example.org";
  private static final String PASSWORT = "richtiges-passwort";
  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");

  private final TestRestTemplate rest;
  private final AccountRepository accounts;
  private final PasswordHasher hasher;
  private final AnsprechpartnerRepository ansprechpartner;
  private final JdbcTemplate jdbc;

  private HttpHeaders sitzung = new HttpHeaders();

  @Autowired
  FirmaControllerIT(
      final TestRestTemplate rest,
      final AccountRepository accounts,
      final PasswordHasher hasher,
      final AnsprechpartnerRepository ansprechpartner,
      final JdbcTemplate jdbc) {
    this.rest = rest;
    this.accounts = accounts;
    this.hasher = hasher;
    this.ansprechpartner = ansprechpartner;
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

  private <T> ResponseEntity<T> hole(final String pfad, final Class<T> typ) {
    return rest.exchange(pfad, HttpMethod.GET, new HttpEntity<>(sitzung), typ);
  }

  private static Map<String, String> rumpf(final String name, final String ort) {
    return Map.of(
        "name", name,
        "strasse", "Am Wall 1",
        "plz", "28195",
        "ort", ort,
        "land", "Deutschland",
        "steuernummer", "75/123/45678",
        "umsatzsteuerId", "DE123456789");
  }

  private long legeAn(final String name, final String ort) {
    final ResponseEntity<FirmaResponse> antwort =
        ruf("/api/firmen", HttpMethod.POST, rumpf(name, ort), FirmaResponse.class);
    return Objects.requireNonNull(antwort.getBody()).id();
  }

  private void schalte(final long id, final String weg) {
    ruf("/api/firmen/" + id + "/" + weg, HttpMethod.POST, null, Void.class);
  }

  private FirmaResponse detail(final long id) {
    return Objects.requireNonNull(hole("/api/firmen/" + id, FirmaResponse.class).getBody());
  }

  private FirmenUebersichtResponse uebersicht(final String suche, final boolean auchStillgelegte) {
    return Objects.requireNonNull(
        hole(
                "/api/firmen?suche=" + suche + "&auchStillgelegte=" + auchStillgelegte,
                FirmenUebersichtResponse.class)
            .getBody());
  }

  private static Ansprechpartner partner(
      final long firmaId, final String nachname, final boolean aktiv) {
    return new Ansprechpartner(
        null, firmaId, "Max", nachname, null, null, null, null, aktiv, ANGELEGT, ANGELEGT);
  }

  @Test
  void anlegen_thenAnswersCreated() {
    // When
    final ResponseEntity<FirmaResponse> antwort =
        ruf("/api/firmen", HttpMethod.POST, rumpf("Adler AG", "Bremen"), FirmaResponse.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

  @Test
  void detail_afterAnlegen_thenShowsEveryStoredValue() {
    // Given
    final long id = legeAn("Adler AG", "Bremen");

    // When
    final FirmaResponse gelesen = detail(id);

    // Then
    assertThat(gelesen)
        .isEqualTo(
            new FirmaResponse(
                id,
                "Adler AG",
                "Am Wall 1",
                "28195",
                "Bremen",
                "Deutschland",
                "75/123/45678",
                "DE123456789",
                true,
                List.of()));
  }

  @Test
  void aendern_thenAnswersWithoutContent() {
    // Given
    final long id = legeAn("Adler AG", "Bremen");

    // When — Kriterium 8.
    final ResponseEntity<Void> antwort =
        ruf("/api/firmen/" + id, HttpMethod.PUT, rumpf("Adler GmbH", "Hamburg"), Void.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  void aendern_thenTheDetailShowsTheNewValues() {
    // Given
    final long id = legeAn("Adler AG", "Bremen");

    // When
    ruf("/api/firmen/" + id, HttpMethod.PUT, rumpf("Adler GmbH", "Hamburg"), Void.class);

    // Then
    assertThat(detail(id))
        .extracting(FirmaResponse::name, FirmaResponse::ort)
        .containsExactly("Adler GmbH", "Hamburg");
  }

  @Test
  void stilllegen_thenTheDetailShowsTheFirmaAsRetired() {
    // Given
    final long id = legeAn("Adler AG", "Bremen");

    // When — Kriterium 13.
    schalte(id, "stilllegen");

    // Then
    assertThat(detail(id).aktiv()).isFalse();
  }

  @Test
  void uebersicht_givenARetiredFirma_thenLeavesItOutByDefault() {
    // Given
    final long id = legeAn("Adler AG", "Bremen");
    schalte(id, "stilllegen");

    // When
    final FirmenUebersichtResponse liste = uebersicht("", false);

    // Then
    assertThat(liste.firmen()).isEmpty();
  }

  @Test
  void uebersicht_givenARetiredFirmaAndTheSwitch_thenShowsIt() {
    // Given
    final long id = legeAn("Adler AG", "Bremen");
    schalte(id, "stilllegen");

    // When
    final FirmenUebersichtResponse liste = uebersicht("", true);

    // Then
    assertThat(liste.firmen())
        .containsExactly(new FirmaZeileResponse(id, "Adler AG", "Bremen", 0L, false));
  }

  @Test
  void uebersicht_givenOnlyRetiredFirmen_thenTheTotalStillCountsThem() {
    // Given — „noch keine Firma" und „alle stillgelegt" sind zwei Lagen, und nur gesamt trennt
    // sie (E5, Kriterium 2).
    final long id = legeAn("Adler AG", "Bremen");
    schalte(id, "stilllegen");

    // When
    final FirmenUebersichtResponse liste = uebersicht("", false);

    // Then
    assertThat(liste.gesamt()).isEqualTo(1L);
  }

  @Test
  void aktivieren_thenTheFirmaIsBackInTheDefaultUebersicht() {
    // Given
    final long id = legeAn("Adler AG", "Bremen");
    schalte(id, "stilllegen");

    // When — Kriterium 13: der Weg zurueck.
    schalte(id, "aktivieren");

    // Then
    assertThat(uebersicht("", false).firmen())
        .extracting(FirmaZeileResponse::id)
        .containsExactly(id);
  }

  @Test
  void uebersicht_givenASearchText_thenShowsOnlyTheMatchingFirma() {
    // Given
    legeAn("Adler AG", "Bremen");
    legeAn("Baum GmbH", "Hamburg");

    // When — Kriterium 3.
    final FirmenUebersichtResponse liste = uebersicht("adler", false);

    // Then
    assertThat(liste.firmen()).extracting(FirmaZeileResponse::name).containsExactly("Adler AG");
  }

  @Test
  void uebersicht_givenASearchText_thenTheTotalIgnoresTheFilter() {
    // Given
    legeAn("Adler AG", "Bremen");
    legeAn("Baum GmbH", "Hamburg");

    // When
    final FirmenUebersichtResponse liste = uebersicht("adler", false);

    // Then — E5: gesamt zaehlt den Bestand, nicht das Ergebnis.
    assertThat(liste.gesamt()).isEqualTo(2L);
  }

  @Test
  void anlegen_givenAnExistingName_thenIsAcceptedAsWell() {
    // Given — Kriterium 6: zwei Firmen duerfen gleich heissen.
    legeAn("Adler AG", "Bremen");

    // When
    final ResponseEntity<FirmaResponse> antwort =
        ruf("/api/firmen", HttpMethod.POST, rumpf("Adler AG", "Bremen"), FirmaResponse.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

  @Test
  void detail_thenCarriesActiveAndRetiredContactsSortedByLastName() {
    // Given
    final long id = legeAn("Adler AG", "Bremen");
    ansprechpartner.save(partner(id, "wagner", true));
    ansprechpartner.save(partner(id, "Adler", false));

    // When — Kriterium 10: beide Staende kommen gemeinsam und sortiert.
    final FirmaResponse gelesen = detail(id);

    // Then
    assertThat(gelesen.ansprechpartner())
        .extracting(AnsprechpartnerResponse::nachname, AnsprechpartnerResponse::aktiv)
        .containsExactly(tuple("Adler", false), tuple("wagner", true));
  }

  @Test
  void uebersicht_thenCountsOnlyActiveContacts() {
    // Given
    final long id = legeAn("Adler AG", "Bremen");
    ansprechpartner.save(partner(id, "Adler", true));
    ansprechpartner.save(partner(id, "Bohnsack", false));

    // When
    final FirmenUebersichtResponse liste = uebersicht("", false);

    // Then
    assertThat(liste.firmen())
        .singleElement()
        .extracting(FirmaZeileResponse::aktiveAnsprechpartner)
        .isEqualTo(1L);
  }

  @Test
  void detail_givenAnUnknownId_thenAnswersNotFound() {
    // When
    final ResponseEntity<String> antwort = hole("/api/firmen/4711", String.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void anlegen_givenABlankName_thenAnswersBadRequest() {
    // When
    final ResponseEntity<String> antwort =
        ruf("/api/firmen", HttpMethod.POST, rumpf("   ", "Bremen"), String.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }
}
