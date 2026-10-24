package org.mwolff.fbcrm.eigeneangaben;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.Stand;
import org.mwolff.fbcrm.auth.Anmeldung;
import org.mwolff.fbcrm.auth.Konten;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.SecretHasher;
import org.mwolff.fbcrm.eigeneangaben.web.EigeneAngabenResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Die beiden Wege der Selbstauskunft ueber HTTP, mit Sitzung und gegen eine echte
 * PostgreSQL-Instanz (Kriterium 1).
 *
 * <p>Drei Zusagen sind nur hier pruefbar. Erstens: Auf der frischen Datenbank antwortet {@code GET}
 * ohne Sonderzweig, alle Felder leer — die Migration hat die eine Zeile schon angelegt. Zweitens:
 * Ein per {@code PUT} gesendeter Leerstring landet als {@code NULL} in der Spalte und nicht als
 * Leerstring (E9) — der Nachweis liest die Spalte direkt, weil beides an der Schnittstelle gleich
 * aussaehe. Drittens: Beide Wege liegen unter {@code /api} und sind ohne Sitzung verschlossen.
 *
 * <p>Dazu der Schutz gegen stilles Ueberschreiben (#411, Kriterium 4; Plan #415, E1, E3): ohne
 * {@code If-Match} 428, auf altem Stand 409, und der Stand des Ersten bleibt.
 *
 * <p>Der Ausgangszustand wird vor jeder Methode wiederhergestellt, wie ihn die Migration
 * hinterlaesst; die Datenbank der Suite ist geteilt.
 */
class EigeneAngabenPersistenceIT extends AbstractIntegrationTest {

  private static final String PFAD = "/api/eigene-angaben";
  private static final String MAIL = "manne@example.org";
  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");
  private static final String BERUFSBEZEICHNUNG = "Freiberuflicher Softwareentwickler";
  private static final String WEBADRESSE = "https://mwolff.org";

  private final TestRestTemplate rest;
  private final AccountRepository accounts;
  private final SecretHasher hasher;
  private final JdbcTemplate jdbc;

  private HttpHeaders sitzung = new HttpHeaders();

  @Autowired
  EigeneAngabenPersistenceIT(
      final TestRestTemplate rest,
      final AccountRepository accounts,
      final SecretHasher hasher,
      final JdbcTemplate jdbc) {
    this.rest = rest;
    this.accounts = accounts;
    this.hasher = hasher;
    this.jdbc = jdbc;
  }

  @BeforeEach
  void stelleDenStandDerMigrationHerUndMeldeAn() {
    jdbc.execute("DELETE FROM eigene_angaben");
    jdbc.execute("INSERT INTO eigene_angaben DEFAULT VALUES");
    leereKonten(jdbc);
    accounts.save(Konten.neu(MAIL, ANGELEGT));
    sitzung = Anmeldung.perCode(rest, jdbc, hasher, MAIL);
  }

  private <T> ResponseEntity<T> ruf(
      final HttpMethod methode, final Object rumpf, final Class<T> typ) {
    return rest.exchange(PFAD, methode, new HttpEntity<>(rumpf, sitzung), typ);
  }

  /* Jackson schreibt ein nicht gesetztes Feld als null-Wert — genau die Lage „Angabe fehlt". */
  private static Map<String, Object> rumpf(final String wert) {
    final Map<String, Object> felder = new LinkedHashMap<>();
    felder.put("name", wert);
    felder.put("berufsbezeichnung", wert);
    felder.put("strasse", wert);
    felder.put("plz", wert);
    felder.put("ort", wert);
    felder.put("land", wert);
    felder.put("email", wert);
    felder.put("telefon", wert);
    felder.put("webadresse", wert);
    felder.put("steuernummer", wert);
    felder.put("umsatzsteuerId", wert);
    felder.put("bankverbindung", wert);
    return felder;
  }

  private EigeneAngabenResponse gelesen() {
    return Objects.requireNonNull(ruf(HttpMethod.GET, null, EigeneAngabenResponse.class).getBody());
  }

  /** Speichert auf dem aktuellen Stand, wie die Maske (Plan #415, E2). */
  private ResponseEntity<EigeneAngabenResponse> gepflegt(final Map<String, Object> felder) {
    return rest.exchange(
        PFAD,
        HttpMethod.PUT,
        new HttpEntity<>(felder, Stand.aktuell(sitzung, jdbc, "eigene_angaben", 1L)),
        EigeneAngabenResponse.class);
  }

  /** Speichert auf dem genannten Stand. */
  private ResponseEntity<String> gepflegtAuf(final Map<String, Object> felder, final long stand) {
    final HttpHeaders kopf = new HttpHeaders();
    kopf.putAll(sitzung);
    kopf.setIfMatch("\"" + stand + "\"");
    return rest.exchange(PFAD, HttpMethod.PUT, new HttpEntity<>(felder, kopf), String.class);
  }

  private Integer gesetzteSpalten() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM eigene_angaben WHERE name IS NOT NULL OR strasse IS NOT NULL"
            + " OR plz IS NOT NULL OR ort IS NOT NULL OR land IS NOT NULL"
            + " OR email IS NOT NULL OR telefon IS NOT NULL OR steuernummer IS NOT NULL"
            + " OR umsatzsteuer_id IS NOT NULL OR bankverbindung IS NOT NULL"
            + " OR berufsbezeichnung IS NOT NULL OR webadresse IS NOT NULL",
        Integer.class);
  }

  @Test
  void lesen_givenTheFreshDatabase_thenAnswersWithEveryFieldAbsent() {
    // When — Kriterium 1: der Bereich ist von Anfang an da.
    final EigeneAngabenResponse antwort = gelesen();

    // Then
    assertThat(antwort)
        .isEqualTo(
            new EigeneAngabenResponse(
                null, null, null, null, null, null, null, null, null, null, null, null, 0L));
  }

  @Test
  void pflegen_thenAnswersWithTheRaisedVersion() {
    // When
    final ResponseEntity<EigeneAngabenResponse> antwort = gepflegt(rumpf("Wert"));

    // Then — Plan #415, E1: das Speichern erhoeht den Stand, und der Urheber steht an der Zeile.
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(Objects.requireNonNull(antwort.getBody()).version()).isEqualTo(1L);
    assertThat(gelesen().version()).isEqualTo(1L);
    assertThat(jdbc.queryForObject("SELECT geaendert_von FROM eigene_angaben", Long.class))
        .isEqualTo(accounts.findByEmail(MAIL).orElseThrow().id());
  }

  @Test
  void pflegen_withoutIfMatch_thenPreconditionRequiredAndNothingChanges() {
    // When — E3.
    final ResponseEntity<String> antwort = ruf(HttpMethod.PUT, rumpf("Wert"), String.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.PRECONDITION_REQUIRED);
    assertThat(gesetzteSpalten()).isZero();
  }

  @Test
  void pflegen_onAnOutdatedStand_thenConflictAndTheFirstStandStays() {
    // Given — #411, Kriterium 1: der Erste hat auf Stand 0 gespeichert.
    gepflegt(rumpf("Erster"));

    // When — der Zweite speichert auf dem Stand, den er beim Oeffnen sah.
    final ResponseEntity<String> antwort = gepflegtAuf(rumpf("Zweiter"), 0L);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(antwort.getBody()).contains("\"konflikt\"");
    assertThat(gelesen().name()).isEqualTo("Erster");
  }

  @Test
  void lesen_afterPflegen_thenAnswersWithTheStoredValues() {
    // Given
    final Map<String, Object> felder = rumpf(null);
    felder.put("name", "Manfred Wolff");
    felder.put("ort", "Bremen");
    felder.put("berufsbezeichnung", BERUFSBEZEICHNUNG);
    felder.put("webadresse", WEBADRESSE);
    gepflegt(felder);

    // When
    final EigeneAngabenResponse antwort = gelesen();

    // Then
    assertThat(antwort)
        .extracting(
            EigeneAngabenResponse::name,
            EigeneAngabenResponse::ort,
            EigeneAngabenResponse::berufsbezeichnung,
            EigeneAngabenResponse::webadresse)
        .containsExactly("Manfred Wolff", "Bremen", BERUFSBEZEICHNUNG, WEBADRESSE);
  }

  @Test
  void pflegen_givenEmptyStrings_thenTheColumnsHoldNullAndNotTheEmptyString() {
    // Given — E9: zwei Schreibweisen fuer „nicht angegeben" gibt es nicht.
    gepflegt(rumpf("Wert"));

    // When
    gepflegt(rumpf(""));

    // Then
    assertThat(gesetzteSpalten()).isZero();
  }

  @Test
  void pflegen_givenEmptyStrings_thenLesenAnswersWithEveryFieldAbsent() {
    // Given
    gepflegt(rumpf("Wert"));

    // When — E9.
    gepflegt(rumpf(""));

    // Then
    assertThat(gelesen())
        .isEqualTo(
            new EigeneAngabenResponse(
                null, null, null, null, null, null, null, null, null, null, null, null, 2L));
  }

  @Test
  void lesen_withoutASession_thenUnauthorized() {
    // When — beide Wege liegen unter /api und stehen in keiner Ausnahmeliste.
    sitzung = new HttpHeaders();

    // Then
    assertThat(ruf(HttpMethod.GET, null, String.class).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void pflegen_withoutASession_thenUnauthorized() {
    // When
    sitzung = new HttpHeaders();

    // Then
    assertThat(ruf(HttpMethod.PUT, rumpf("Wert"), String.class).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void pflegen_givenPaddedNewFields_thenTheColumnsHoldThemTrimmed() {
    // Given — E9: Leerraum am Rand gehoert nicht zur Angabe. Der Nachweis liest die Spalten
    // direkt, weil die Schnittstelle den beschnittenen und den unbeschnittenen Wert gleich
    // zurueckgaebe.
    final Map<String, Object> felder = rumpf(null);
    felder.put("berufsbezeichnung", "  " + BERUFSBEZEICHNUNG + "  ");
    felder.put("webadresse", "  " + WEBADRESSE + "  ");

    // When
    gepflegt(felder);

    // Then
    assertThat(spalte("berufsbezeichnung")).isEqualTo(BERUFSBEZEICHNUNG);
    assertThat(spalte("webadresse")).isEqualTo(WEBADRESSE);
  }

  private @Nullable String spalte(final String name) {
    return jdbc.queryForObject("SELECT " + name + " FROM eigene_angaben", String.class);
  }
}
