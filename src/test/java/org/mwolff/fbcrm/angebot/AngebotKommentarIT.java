package org.mwolff.fbcrm.angebot;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.angebot.web.AngebotKommentarResponse;
import org.mwolff.fbcrm.angebot.web.AngebotKommentareResponse;
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
 * Die Wege der Kommentare am Angebot ueber HTTP, mit Sitzung und gegen eine echte
 * PostgreSQL-Instanz (Issue #140).
 *
 * <p>Hier stehen die Zusagen, die an der echten Transaktion und am echten Schema haengen: Ein
 * geschriebener Kommentar steht anschliessend in der Liste, zwei kommen neuester zuerst zurueck
 * (Kriterium 4), ein {@code DELETE} nimmt die Zeile wirklich fort (Kriterium 10, E6), und ein
 * {@code PUT} laesst {@code createdAt} stehen (Kriterium 9). Dazu die Grenzen: 401 ohne Sitzung,
 * 404 bei unbekanntem Angebot und bei einem Kommentar eines anderen Angebots (E5), und 201 auch an
 * einem Angebot im Status ABGERECHNET (Kriterium 8).
 */
class AngebotKommentarIT extends AbstractIntegrationTest {

  private static final String MAIL = "manne@example.org";
  private static final String PASSWORT = "richtiges-passwort";
  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");
  private static final String FIRMA = "Adler AG";

  private final TestRestTemplate rest;
  private final AccountRepository accounts;
  private final PasswordHasher hasher;
  private final JdbcTemplate jdbc;

  private HttpHeaders sitzung = new HttpHeaders();
  private long firmaId;

  @Autowired
  AngebotKommentarIT(
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
    jdbc.execute(
        "TRUNCATE angebot_kommentar, angebot_position, angebot, ansprechpartner, firma"
            + " RESTART IDENTITY CASCADE");
    jdbc.update("INSERT INTO firma (name) VALUES (?)", FIRMA);
    firmaId =
        Objects.requireNonNull(
                jdbc.queryForObject("SELECT id FROM firma WHERE name = ?", Long.class, FIRMA))
            .longValue();
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

  private long angebot() {
    return Objects.requireNonNull(
            ruf(
                    "/api/firmen/" + firmaId + "/angebote",
                    HttpMethod.POST,
                    null,
                    AngebotResponse.class)
                .getBody())
        .id();
  }

  private String pfad(final long angebotId) {
    return "/api/angebote/" + angebotId + "/kommentare";
  }

  private ResponseEntity<AngebotKommentarResponse> schreibe(
      final long angebotId, final String text) {
    return ruf(
        pfad(angebotId), HttpMethod.POST, Map.of("text", text), AngebotKommentarResponse.class);
  }

  private List<AngebotKommentarResponse> liste(final long angebotId) {
    return Objects.requireNonNull(
            ruf(pfad(angebotId), HttpMethod.GET, null, AngebotKommentareResponse.class).getBody())
        .kommentare();
  }

  @Test
  void schreiben_thenTheKommentarIsInTheListWithItsCreatedAt() {
    // Given — Kriterien 2 und 3.
    final long angebotId = angebot();

    // When
    final ResponseEntity<AngebotKommentarResponse> geschrieben =
        schreibe(angebotId, "  Bitte melden.  ");

    // Then
    assertThat(geschrieben.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    final AngebotKommentarResponse angelegt = Objects.requireNonNull(geschrieben.getBody());
    assertThat(angelegt.text()).isEqualTo("Bitte melden.");
    assertThat(angelegt.createdAt()).isNotNull();
    assertThat(liste(angebotId))
        .extracting(kommentar -> Long.valueOf(kommentar.id()))
        .containsExactly(Long.valueOf(angelegt.id()));
  }

  @Test
  void schreiben_thenAnswersWithCreatedAtAsAnIsoTimestampText() {
    // Given — Paket #145 liest den Zeitpunkt mit `new Date(...)`: Er muss als Text herauskommen,
    // nicht als Zahl. Die Form entscheidet der von Spring Boot gebaute ObjectMapper, und nur dieser
    // Weg laeuft mit ihm.
    final long angebotId = angebot();

    // When
    final ResponseEntity<String> antwort =
        ruf(pfad(angebotId), HttpMethod.POST, Map.of("text", "Bitte melden."), String.class);

    // Then
    assertThat(antwort.getBody())
        .containsPattern("\"createdAt\":\"\\d{4}-\\d{2}-\\d{2}T[0-9:.]+Z\"");
  }

  @Test
  void liste_withTwoKommentare_thenTheNewestComesFirst() {
    // Given — Kriterium 4.
    final long angebotId = angebot();
    final long erster = Objects.requireNonNull(schreibe(angebotId, "Erster").getBody()).id();
    final long zweiter = Objects.requireNonNull(schreibe(angebotId, "Zweiter").getBody()).id();

    // When
    final List<AngebotKommentarResponse> kommentare = liste(angebotId);

    // Then
    assertThat(kommentare)
        .extracting(kommentar -> Long.valueOf(kommentar.id()))
        .containsExactly(Long.valueOf(zweiter), Long.valueOf(erster));
  }

  @Test
  void schreiben_toAnUnknownAngebot_thenAnswers404() {
    // When
    final ResponseEntity<String> antwort =
        ruf(pfad(9999L), HttpMethod.POST, Map.of("text", "Bitte melden."), String.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void aendern_aKommentarOfAnotherAngebot_thenAnswers404() {
    // Given — E5: die Kennung des Kommentars allein genuegt nicht.
    final long eigenes = angebot();
    final long fremdes = angebot();
    final long kommentarId =
        Objects.requireNonNull(schreibe(fremdes, "Beim anderen Angebot").getBody()).id();

    // When
    final ResponseEntity<String> antwort =
        ruf(
            pfad(eigenes) + "/" + kommentarId,
            HttpMethod.PUT,
            Map.of("text", "Uebernahme"),
            String.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(liste(fremdes))
        .extracting(AngebotKommentarResponse::text)
        .containsExactly("Beim anderen Angebot");
  }

  @Test
  void schreiben_toAnAngebotInTheLastStatus_thenAnswersCreated() {
    // Given — Kriterium 8: der Status schraenkt Kommentare nicht ein.
    final long angebotId = angebot();
    jdbc.update("UPDATE angebot SET status = 'ABGERECHNET' WHERE id = ?", Long.valueOf(angebotId));

    // When
    final ResponseEntity<AngebotKommentarResponse> antwort = schreibe(angebotId, "Auch hier noch");

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

  @Test
  void loeschen_thenTheKommentarIsGoneFromTheListAndFromTheTable() {
    // Given — Kriterium 10, E6.
    final long angebotId = angebot();
    final long kommentarId =
        Objects.requireNonNull(schreibe(angebotId, "Weg damit").getBody()).id();

    // When
    final ResponseEntity<Void> antwort =
        ruf(pfad(angebotId) + "/" + kommentarId, HttpMethod.DELETE, null, Void.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(liste(angebotId)).isEmpty();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM angebot_kommentar WHERE id = ?",
                Long.class,
                Long.valueOf(kommentarId)))
        .isEqualTo(Long.valueOf(0L));
  }

  @Test
  void aendern_thenCarriesTheNewTextAndTheOldCreatedAt() {
    // Given — Kriterium 9: der Zeitpunkt bleibt der der Anlage.
    final long angebotId = angebot();
    final AngebotKommentarResponse angelegt =
        Objects.requireNonNull(schreibe(angebotId, "Erster Stand").getBody());

    // When
    final ResponseEntity<AngebotKommentarResponse> antwort =
        ruf(
            pfad(angebotId) + "/" + angelegt.id(),
            HttpMethod.PUT,
            Map.of("text", "Zweiter Stand"),
            AngebotKommentarResponse.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
    final AngebotKommentarResponse geaendert = Objects.requireNonNull(antwort.getBody());
    assertThat(geaendert.text()).isEqualTo("Zweiter Stand");
    assertThat(geaendert.createdAt()).isEqualTo(angelegt.createdAt());
  }

  @Test
  void kommentarwege_withoutASession_thenUnauthorized() {
    // Given — jeder neue Pfad unter /api ist ohne Sitzung verschlossen (E11).
    final long angebotId = angebot();
    sitzung = new HttpHeaders();

    // When / Then
    assertThat(ruf(pfad(angebotId), HttpMethod.GET, null, String.class).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(
            ruf(pfad(angebotId), HttpMethod.POST, Map.of("text", "Ohne Sitzung"), String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
