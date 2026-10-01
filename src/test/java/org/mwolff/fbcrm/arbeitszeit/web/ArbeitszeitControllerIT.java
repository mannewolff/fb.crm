package org.mwolff.fbcrm.arbeitszeit.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.angebot.web.AngebotPositionResponse;
import org.mwolff.fbcrm.angebot.web.AngebotResponse;
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
 * Die Schreibwege der Arbeitszeit ueber HTTP, mit Sitzung und gegen eine echte PostgreSQL-Instanz
 * (Issue #193, Kriterien 1, 3, 4 und 6).
 *
 * <p>Hier stehen die Zusagen, die an der echten Verdrahtung und am echten Schema haengen: Ein
 * erfasster Eintrag kommt mit 201 und seiner gerechneten Dauer zurueck, ein {@code PUT} antwortet
 * 200, ein {@code DELETE} nimmt die Zeile wirklich fort, und eine unbekannte Kennung ist 404. Dazu
 * die beiden Abweisungen, die der Anwendungsfall als Meldung am Feld liefert (Plan #194, A19, A8):
 * 9:10 liegt nicht im Raster, und ein zweiter Eintrag zur selben Zeit nennt den ersten mit Uhrzeit,
 * Position und Firma.
 *
 * <p>Der Ausgangspunkt ist ein bestelltes Angebot mit zwei Positionen: „Konzeption" nach Aufwand in
 * Stunden, auf die gebucht werden darf, und daneben eine Pauschale, auf die nicht gebucht werden
 * darf. Die Namen sind die des Beispiels aus #193.
 */
class ArbeitszeitControllerIT extends AbstractIntegrationTest {

  private static final String MAIL = "manne@example.org";
  private static final String PASSWORT = "richtiges-passwort";
  private static final Instant ANGELEGT = Instant.parse("2026-11-01T08:00:00Z");

  private static final String FIRMA = "IT Bildungshaus";
  private static final String TAG = "2026-11-12";
  private static final String PFAD = "/api/arbeitszeit";

  private final TestRestTemplate rest;
  private final AccountRepository accounts;
  private final PasswordHasher hasher;
  private final JdbcTemplate jdbc;

  private HttpHeaders sitzung = new HttpHeaders();
  private long angebotId;
  private long konzeptionId;
  private long schulungId;

  @Autowired
  ArbeitszeitControllerIT(
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
  void legeDenAusgangspunktAn() {
    jdbc.execute(
        "TRUNCATE arbeitszeit, rechnung_position, rechnung, angebot_position, angebot,"
            + " ansprechpartner, firma RESTART IDENTITY CASCADE");
    jdbc.execute("TRUNCATE outbox_message, password_reset_token, account RESTART IDENTITY CASCADE");
    accounts.save(
        new Account(null, MAIL, "Manne", hasher.hash(PASSWORT), Role.ADMIN, 0, ANGELEGT, ANGELEGT));
    sitzung = angemeldeterKopf();
    jdbc.update("INSERT INTO firma (name) VALUES (?)", FIRMA);
    final long firmaId =
        Objects.requireNonNull(
                jdbc.queryForObject("SELECT id FROM firma WHERE name = ?", Long.class, FIRMA))
            .longValue();
    angebotId =
        Objects.requireNonNull(
                ruf(
                        "/api/firmen/" + firmaId + "/angebote",
                        HttpMethod.POST,
                        null,
                        AngebotResponse.class)
                    .getBody())
            .id();
    final AngebotResponse mitPositionen =
        Objects.requireNonNull(
            aendereDasAngebot(
                    List.of(
                        position("Konzeption", "AUFWAND", "20.00", "STUNDE", "120.00"),
                        position("Schulungstag", "FESTPREIS", "1", "PAUSCHAL", "1200.00")))
                .getBody());
    konzeptionId = kennung(mitPositionen.positionen().get(0));
    schulungId = kennung(mitPositionen.positionen().get(1));
    ruf("/api/angebote/" + angebotId + "/status/weiter", HttpMethod.POST, null, String.class);
    ruf("/api/angebote/" + angebotId + "/status/weiter", HttpMethod.POST, null, String.class);
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

  private static long kennung(final AngebotPositionResponse position) {
    return Objects.requireNonNull(position.id()).longValue();
  }

  private static Map<String, Object> position(
      final String bezeichnung,
      final String abrechnungsmodus,
      final String menge,
      final String einheit,
      final String einzelpreis) {
    final Map<String, Object> felder = new LinkedHashMap<>();
    felder.put("id", null);
    felder.put("bezeichnung", bezeichnung);
    felder.put("abrechnungsmodus", abrechnungsmodus);
    felder.put("menge", menge);
    felder.put("einheit", einheit);
    felder.put("einzelpreis", einzelpreis);
    return felder;
  }

  private ResponseEntity<AngebotResponse> aendereDasAngebot(
      final List<Map<String, Object>> positionen) {
    final Map<String, Object> rumpf = new LinkedHashMap<>();
    rumpf.put("angebotDatum", "2026-11-01");
    rumpf.put("ansprechpartnerId", null);
    rumpf.put("beschreibung", "Neugestaltung der Website");
    rumpf.put("positionen", positionen);
    return ruf("/api/angebote/" + angebotId, HttpMethod.PUT, rumpf, AngebotResponse.class);
  }

  private static Map<String, Object> eintrag(
      final long angebotPositionId, final String von, final String bis) {
    final Map<String, Object> rumpf = new LinkedHashMap<>();
    rumpf.put("angebotPositionId", Long.valueOf(angebotPositionId));
    rumpf.put("tag", TAG);
    rumpf.put("von", von);
    rumpf.put("bis", bis);
    return rumpf;
  }

  private <T> ResponseEntity<T> erfasse(final String von, final String bis, final Class<T> typ) {
    return ruf(PFAD, HttpMethod.POST, eintrag(konzeptionId, von, bis), typ);
  }

  private long erfassteKennung(final String von, final String bis) {
    return Objects.requireNonNull(erfasse(von, bis, ZeiteintragResponse.class).getBody()).id();
  }

  @Test
  void anlegen_thenAnswers201WithTheEntryAndItsCalculatedHours() {
    // Given — Kriterium 1: Tag, von, bis und die Position; die Dauer rechnet die Anwendung.
    // When
    final ResponseEntity<ZeiteintragResponse> erfasst =
        erfasse("09:00", "10:45", ZeiteintragResponse.class);

    // Then
    assertThat(erfasst.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    final ZeiteintragResponse angelegt = Objects.requireNonNull(erfasst.getBody());
    assertThat(angelegt.stunden()).isEqualByComparingTo("1.75");
  }

  @Test
  void anlegen_thenTheAnswerCarriesDayAndTimeAsText() {
    // Given — die Oberflaeche liest Tag und Uhrzeit als Text. Welche Form der ObjectMapper von
    // Spring Boot schreibt, laesst sich nur hier belegen: standaloneSetup kennt ihn nicht.
    // When
    final ResponseEntity<String> erfasst = erfasse("09:00", "10:45", String.class);

    // Then
    assertThat(erfasst.getBody())
        .contains("\"tag\":\"" + TAG + "\"")
        .contains("\"von\":\"09:00:00\"")
        .contains("\"bis\":\"10:45:00\"");
  }

  @Test
  void anlegen_withoutASession_thenAnswers401() {
    // Given — die Wege fallen unter /api/** und sind nur angemeldet erreichbar (E11).
    sitzung = new HttpHeaders();

    // When / Then
    assertThat(erfasse("09:00", "10:45", String.class).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void anlegen_givenABeginnOutsideTheGrid_thenAnswers422WithAFieldErrorAtVon() {
    // Given — A19: 9:10 ist keine Viertelstunde, und Kriterium 3 verlangt die Meldung am Feld.
    // When
    final ResponseEntity<String> abgewiesen = erfasse("09:10", "11:00", String.class);

    // Then
    assertThat(abgewiesen.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(abgewiesen.getBody()).contains("\"fieldErrors\"").contains("\"von\"");
  }

  @Test
  void anlegen_givenAnOverlappingEntry_thenNamesTheOtherEntryInTheFieldMessage() {
    // Given — A8, Kriterium 4: der erste Eintrag belegt 9:00 bis 11:00.
    erfasse("09:00", "11:00", ZeiteintragResponse.class);

    // When — der zweite Eintrag greift hinein, auf derselben Position.
    final ResponseEntity<String> abgewiesen = erfasse("10:00", "12:00", String.class);

    // Then — die Meldung nennt den anderen Eintrag mit Uhrzeit, Position und Firma.
    assertThat(abgewiesen.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(abgewiesen.getBody())
        .contains("\"fieldErrors\"")
        .contains("9:00 bis 11:00")
        .contains("Konzeption")
        .contains(FIRMA);
  }

  @Test
  void anlegen_onAFlatRatePosition_thenAnswers422WithAFieldErrorAtThePosition() {
    // Given — Antworten 3 und 5: auf eine Pauschale wird keine Zeit gebucht.
    // When
    final ResponseEntity<String> abgewiesen =
        ruf(PFAD, HttpMethod.POST, eintrag(schulungId, "09:00", "11:00"), String.class);

    // Then
    assertThat(abgewiesen.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(abgewiesen.getBody()).contains("\"fieldErrors\"").contains("\"angebotPositionId\"");
  }

  @Test
  void aendern_thenAnswers200WithTheChangedEntry() {
    // Given — Kriterium 6.
    final long id = erfassteKennung("09:00", "11:00");

    // When
    final ResponseEntity<ZeiteintragResponse> geaendert =
        ruf(
            PFAD + "/" + id,
            HttpMethod.PUT,
            eintrag(konzeptionId, "09:00", "12:00"),
            ZeiteintragResponse.class);

    // Then
    assertThat(geaendert.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(Objects.requireNonNull(geaendert.getBody()).stunden()).isEqualByComparingTo("3.00");
  }

  @Test
  void aendern_whenTheEntryIsUnknown_thenAnswers404() {
    // Given — die Kennung steht im Pfad.
    // When / Then
    assertThat(
            ruf(
                    PFAD + "/999",
                    HttpMethod.PUT,
                    eintrag(konzeptionId, "09:00", "12:00"),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void loeschen_thenTheRowIsGone() {
    // Given — A5, Antwort 4: geloescht wird wirklich.
    final long id = erfassteKennung("09:00", "11:00");

    // When
    final ResponseEntity<String> geloescht =
        ruf(PFAD + "/" + id, HttpMethod.DELETE, null, String.class);

    // Then — derselbe Eintrag ist danach nicht mehr aenderbar.
    assertThat(geloescht.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(
            ruf(
                    PFAD + "/" + id,
                    HttpMethod.PUT,
                    eintrag(konzeptionId, "09:00", "12:00"),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void loeschen_whenTheEntryIsUnknown_thenAnswers404() {
    // Given
    // When / Then
    assertThat(ruf(PFAD + "/999", HttpMethod.DELETE, null, String.class).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }
}
