package org.mwolff.fbcrm.vorgang.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.PasswordHasher;
import org.mwolff.fbcrm.auth.domain.Role;
import org.mwolff.fbcrm.firma.domain.Anschrift;
import org.mwolff.fbcrm.firma.domain.Ansprechpartner;
import org.mwolff.fbcrm.firma.domain.AnsprechpartnerRepository;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Die Schreibwege des Vorgangs ueber HTTP, mit Sitzung und gegen eine echte PostgreSQL-Instanz.
 *
 * <p>Drei Zusagen sind nur hier pruefbar, weil sie an der echten Transaktion haengen. Erstens
 * Kriterium 8: Ein abgewiesenes Anlegen verbraucht keine Nummer — der Nachweis laeuft als Kette
 * (abgewiesenes POST, danach gueltiges POST, Nummer 1) und nicht als Zusicherung an einem Doppel.
 * Zweitens E19 samt der Ausnahme aus Kriterium 23: Eine stillgelegte Zuordnung wird abgewiesen,
 * <b>dieselbe</b> bereits zugeordnete aber gespeichert. Drittens Kriterium 20: Der Abschlussstand
 * wirkt sichtbar auf {@code GET /api/vorgaenge}.
 *
 * <p>Der Nummernkreis wird vor jeder Methode auf 1 zurueckgesetzt: {@code TRUNCATE … RESTART
 * IDENTITY} fasst ihn nicht an, weil er eine eigene Zaehlertabelle ist (E3) — ohne den Ruecksetzer
 * saehe „Nummer 1" von der zweiten Testmethode an anders aus.
 *
 * <p>Der Nachweis der Zugangsregel steht hier und nicht nur in {@code AccessRuleIT}: Ein neuer Pfad
 * unter {@code /api} ist ohne Sitzung verschlossen, und das gehoert zu jedem neuen Weg dazu.
 */
class VorgangSchreibIT extends AbstractIntegrationTest {

  private static final String MAIL = "manne@example.org";
  private static final String PASSWORT = "richtiges-passwort";
  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");
  private static final Instant SPAET = Instant.parse("2026-09-20T08:00:00Z");

  private final TestRestTemplate rest;
  private final AccountRepository accounts;
  private final PasswordHasher hasher;
  private final FirmaRepository firmen;
  private final AnsprechpartnerRepository ansprechpartner;
  private final JdbcTemplate jdbc;

  private HttpHeaders sitzung = new HttpHeaders();

  @Autowired
  VorgangSchreibIT(
      final TestRestTemplate rest,
      final AccountRepository accounts,
      final PasswordHasher hasher,
      final FirmaRepository firmen,
      final AnsprechpartnerRepository ansprechpartner,
      final JdbcTemplate jdbc) {
    this.rest = rest;
    this.accounts = accounts;
    this.hasher = hasher;
    this.firmen = firmen;
    this.ansprechpartner = ansprechpartner;
    this.jdbc = jdbc;
  }

  @BeforeEach
  void leereDenBestandUndMeldeAn() {
    jdbc.execute(
        "TRUNCATE vorgang_eintrag, vorgang, ansprechpartner, firma RESTART IDENTITY CASCADE");
    jdbc.update("UPDATE vorgang_nummernkreis SET naechste = 1");
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

  /* Jackson schreibt ein nicht gesetztes Feld als null-Wert — genau die Lage „Angabe fehlt". */
  private static Map<String, Object> rumpf(
      final String titel, final Long firmaId, final Long ansprechpartnerId) {
    final Map<String, Object> felder = new LinkedHashMap<>();
    felder.put("titel", titel);
    felder.put("firmaId", firmaId);
    felder.put("ansprechpartnerId", ansprechpartnerId);
    return felder;
  }

  private long firma(final String name, final boolean aktiv) {
    return firmen
        .save(
            new Firma(
                null,
                name,
                new Anschrift("Am Wall 1", "28195", "Bremen", "Deutschland"),
                null,
                null,
                aktiv,
                ANGELEGT,
                ANGELEGT))
        .requireId();
  }

  private long partner(final long firmaId, final boolean aktiv) {
    return ansprechpartner
        .save(
            new Ansprechpartner(
                null, firmaId, "Max", "Mueller", null, null, null, null, aktiv, ANGELEGT, ANGELEGT))
        .requireId();
  }

  private void legeDieFirmaStill(final long firmaId) {
    firmen.save(firmen.findById(firmaId).orElseThrow().stillgelegt(SPAET));
  }

  private ResponseEntity<VorgangAngelegtResponse> anlegen(
      final String titel, final Long firmaId, final Long partnerId) {
    return ruf(
        "/api/vorgaenge",
        HttpMethod.POST,
        rumpf(titel, firmaId, partnerId),
        VorgangAngelegtResponse.class);
  }

  private VorgangAngelegtResponse angelegt(final String titel, final long firmaId) {
    return Objects.requireNonNull(anlegen(titel, Long.valueOf(firmaId), null).getBody());
  }

  private VorgangResponse detail(final long id) {
    return Objects.requireNonNull(
        ruf("/api/vorgaenge/" + id, HttpMethod.GET, null, VorgangResponse.class).getBody());
  }

  private VorgaengeUebersichtResponse uebersicht(final boolean auchAbgeschlossene) {
    return Objects.requireNonNull(
        ruf(
                "/api/vorgaenge?auchAbgeschlossene=" + auchAbgeschlossene,
                HttpMethod.GET,
                null,
                VorgaengeUebersichtResponse.class)
            .getBody());
  }

  @Test
  void anlegen_thenAnswersCreated() {
    // Given — Kriterium 5.
    final long firmaId = firma("Adler AG", true);

    // When
    final ResponseEntity<VorgangAngelegtResponse> antwort =
        anlegen("Website-Relaunch", Long.valueOf(firmaId), null);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

  @Test
  void anlegen_thenAnswersWithIdAndTheFirstFreeNumber() {
    // Given — Kriterien 8, 9: die Oberflaeche braucht beides.
    final long firmaId = firma("Adler AG", true);

    // When
    final VorgangAngelegtResponse antwort = angelegt("Website-Relaunch", firmaId);

    // Then
    assertThat(antwort)
        .extracting(VorgangAngelegtResponse::id, VorgangAngelegtResponse::nummer)
        .containsExactly(Long.valueOf(1L), Long.valueOf(1L));
  }

  @Test
  void anlegen_thenTheDetailShowsTitleFirmaAndAnsprechpartner() {
    // Given — Kriterium 9.
    final long firmaId = firma("Adler AG", true);
    final long partnerId = partner(firmaId, true);

    // When
    final VorgangAngelegtResponse antwort =
        Objects.requireNonNull(
            anlegen("Website-Relaunch", Long.valueOf(firmaId), Long.valueOf(partnerId)).getBody());

    // Then
    assertThat(detail(antwort.id()))
        .extracting(
            VorgangResponse::titel, VorgangResponse::firma, VorgangResponse::ansprechpartner)
        .containsExactly(
            "Website-Relaunch",
            new ZuordnungResponse(firmaId, "Adler AG", true),
            new ZuordnungResponse(partnerId, "Max Mueller", true));
  }

  @Test
  void anlegen_givenNoTitle_thenNamesTheFieldInTheProblemDetail() {
    // Given — Kriterium 5: fehlt der Titel, nennt eine Meldung am Feld den Grund.
    final long firmaId = firma("Adler AG", true);

    // When
    final JsonNode problem =
        Objects.requireNonNull(
            ruf(
                    "/api/vorgaenge",
                    HttpMethod.POST,
                    rumpf(null, Long.valueOf(firmaId), null),
                    JsonNode.class)
                .getBody());

    // Then
    assertThat(problem.path("fieldErrors").has("titel")).isTrue();
  }

  @Test
  void anlegen_givenNoFirma_thenNamesTheFieldInTheProblemDetail() {
    // When — Kriterium 5: die Firma ist Pflicht.
    final JsonNode problem =
        Objects.requireNonNull(
            ruf(
                    "/api/vorgaenge",
                    HttpMethod.POST,
                    rumpf("Website-Relaunch", null, null),
                    JsonNode.class)
                .getBody());

    // Then
    assertThat(problem.path("fieldErrors").has("firmaId")).isTrue();
  }

  @Test
  void anlegen_givenNoTitle_thenStoresNothing() {
    // Given — Kriterium 5: „nichts wird gespeichert".
    final long firmaId = firma("Adler AG", true);

    // When
    ruf("/api/vorgaenge", HttpMethod.POST, rumpf(null, Long.valueOf(firmaId), null), String.class);

    // Then
    assertThat(uebersicht(true).gesamt()).isZero();
  }

  @Test
  void anlegen_afterARefusedAttempt_thenTheNextOneGetsNumberOne() {
    // Given — Kriterium 8: ein abgewiesenes Anlegen verbraucht keine Nummer.
    final long firmaId = firma("Adler AG", true);
    final ResponseEntity<String> abgewiesen =
        ruf(
            "/api/vorgaenge",
            HttpMethod.POST,
            rumpf(null, Long.valueOf(firmaId), null),
            String.class);
    assertThat(abgewiesen.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    // When
    final VorgangAngelegtResponse antwort = angelegt("Website-Relaunch", firmaId);

    // Then
    assertThat(antwort.nummer()).isEqualTo(1L);
  }

  @Test
  void anlegen_givenARetiredFirma_thenAnswersBadRequest() {
    // Given — E19: die Wahlregel steht auf dem Server, nicht nur in der Maske.
    final long firmaId = firma("Adler AG", false);

    // When
    final ResponseEntity<String> antwort =
        ruf(
            "/api/vorgaenge",
            HttpMethod.POST,
            rumpf("Website-Relaunch", Long.valueOf(firmaId), null),
            String.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void anlegen_givenARetiredAnsprechpartner_thenAnswersBadRequest() {
    // Given — E19.
    final long firmaId = firma("Adler AG", true);
    final long partnerId = partner(firmaId, false);

    // When
    final ResponseEntity<String> antwort =
        ruf(
            "/api/vorgaenge",
            HttpMethod.POST,
            rumpf("Website-Relaunch", Long.valueOf(firmaId), Long.valueOf(partnerId)),
            String.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void anlegen_givenAnAnsprechpartnerOfAnotherFirma_thenAnswersBadRequest() {
    // Given — Kriterium 6: zur Wahl stehen nur die Ansprechpartner der gewaehlten Firma.
    final long firmaId = firma("Adler AG", true);
    final long partnerId = partner(firma("Baum GmbH", true), true);

    // When
    final ResponseEntity<String> antwort =
        ruf(
            "/api/vorgaenge",
            HttpMethod.POST,
            rumpf("Website-Relaunch", Long.valueOf(firmaId), Long.valueOf(partnerId)),
            String.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void anlegen_givenAnUnknownFirma_thenAnswersBadRequest() {
    // When — die Firma steht im Rumpf und nicht im Pfad: eine ungueltige Eingabe, keine
    // fehlende Ressource.
    final ResponseEntity<String> antwort =
        ruf(
            "/api/vorgaenge",
            HttpMethod.POST,
            rumpf("Website-Relaunch", Long.valueOf(4711L), null),
            String.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void aendern_thenAnswersNoContent() {
    // Given — Kriterium 10.
    final long firmaId = firma("Adler AG", true);
    final long id = angelegt("Website-Relaunch", firmaId).id();

    // When
    final ResponseEntity<Void> antwort =
        ruf(
            "/api/vorgaenge/" + id,
            HttpMethod.PUT,
            rumpf("Neuer Titel", Long.valueOf(firmaId), null),
            Void.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  void aendern_thenTheDetailShowsTheNewValues() {
    // Given — Kriterium 10.
    final long firmaId = firma("Adler AG", true);
    final long andereFirma = firma("Baum GmbH", true);
    final long id = angelegt("Website-Relaunch", firmaId).id();

    // When
    ruf(
        "/api/vorgaenge/" + id,
        HttpMethod.PUT,
        rumpf("Neuer Titel", Long.valueOf(andereFirma), null),
        Void.class);

    // Then
    assertThat(detail(id))
        .extracting(VorgangResponse::titel, a -> a.firma().name())
        .containsExactly("Neuer Titel", "Baum GmbH");
  }

  @Test
  void aendern_thenKeepsTheNumber() {
    // Given — Kriterium 8: die Nummer aendert sich nie.
    final long firmaId = firma("Adler AG", true);
    final long id = angelegt("Website-Relaunch", firmaId).id();

    // When
    ruf(
        "/api/vorgaenge/" + id,
        HttpMethod.PUT,
        rumpf("Neuer Titel", Long.valueOf(firmaId), null),
        Void.class);

    // Then
    assertThat(detail(id).nummer()).isEqualTo(1L);
  }

  @Test
  void aendern_givenTheRetiredFirmaUnchanged_thenAnswersNoContent() {
    // Given — Kriterium 23, E19: die bestehende Zuordnung bleibt speicherbar.
    final long firmaId = firma("Adler AG", true);
    final long id = angelegt("Website-Relaunch", firmaId).id();
    legeDieFirmaStill(firmaId);

    // When
    final ResponseEntity<Void> antwort =
        ruf(
            "/api/vorgaenge/" + id,
            HttpMethod.PUT,
            rumpf("Neuer Titel", Long.valueOf(firmaId), null),
            Void.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  void aendern_givenAnotherRetiredFirma_thenAnswersBadRequest() {
    // Given — E19: die Ausnahme gilt nur fuer die bereits zugeordnete Firma.
    final long firmaId = firma("Adler AG", true);
    final long stillgelegte = firma("Baum GmbH", false);
    final long id = angelegt("Website-Relaunch", firmaId).id();

    // When
    final ResponseEntity<String> antwort =
        ruf(
            "/api/vorgaenge/" + id,
            HttpMethod.PUT,
            rumpf("Neuer Titel", Long.valueOf(stillgelegte), null),
            String.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void aendern_givenAnUnknownId_thenAnswersNotFound() {
    // Given
    final long firmaId = firma("Adler AG", true);

    // When
    final ResponseEntity<String> antwort =
        ruf(
            "/api/vorgaenge/4711",
            HttpMethod.PUT,
            rumpf("Neuer Titel", Long.valueOf(firmaId), null),
            String.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void abschliessen_thenAnswersNoContent() {
    // Given — Kriterium 20.
    final long id = angelegt("Website-Relaunch", firma("Adler AG", true)).id();

    // When
    final ResponseEntity<Void> antwort =
        ruf("/api/vorgaenge/" + id + "/abschliessen", HttpMethod.POST, null, Void.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  void abschliessen_thenTheVorgangLeavesTheDefaultUebersicht() {
    // Given — Kriterium 20.
    final long id = angelegt("Website-Relaunch", firma("Adler AG", true)).id();

    // When
    ruf("/api/vorgaenge/" + id + "/abschliessen", HttpMethod.POST, null, Void.class);

    // Then
    assertThat(uebersicht(false).vorgaenge()).isEmpty();
  }

  @Test
  void abschliessen_thenTheVorgangAppearsWithTheSwitch() {
    // Given — Kriterium 20: mit Schalter ist er da, gekennzeichnet als abgeschlossen.
    final long id = angelegt("Website-Relaunch", firma("Adler AG", true)).id();

    // When
    ruf("/api/vorgaenge/" + id + "/abschliessen", HttpMethod.POST, null, Void.class);

    // Then
    assertThat(uebersicht(true).vorgaenge())
        .singleElement()
        .extracting(VorgangZeileResponse::id, VorgangZeileResponse::abgeschlossen)
        .containsExactly(id, true);
  }

  @Test
  void wiederEroeffnen_thenTheVorgangIsBackInTheDefaultUebersicht() {
    // Given — Kriterium 20: der Weg zurueck.
    final long id = angelegt("Website-Relaunch", firma("Adler AG", true)).id();
    ruf("/api/vorgaenge/" + id + "/abschliessen", HttpMethod.POST, null, Void.class);

    // When
    final ResponseEntity<Void> antwort =
        ruf("/api/vorgaenge/" + id + "/wiedereroeffnen", HttpMethod.POST, null, Void.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(uebersicht(false).vorgaenge())
        .extracting(VorgangZeileResponse::id)
        .containsExactly(Long.valueOf(id));
  }

  @Test
  void abschliessen_givenAnUnknownId_thenAnswersNotFound() {
    // When
    final ResponseEntity<String> antwort =
        ruf("/api/vorgaenge/4711/abschliessen", HttpMethod.POST, null, String.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void wiederEroeffnen_givenAnUnknownId_thenAnswersNotFound() {
    // When
    final ResponseEntity<String> antwort =
        ruf("/api/vorgaenge/4711/wiedereroeffnen", HttpMethod.POST, null, String.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @ParameterizedTest(name = "{0} {1} ohne Sitzung")
  @CsvSource({
    "POST,/api/vorgaenge",
    "PUT,/api/vorgaenge/1",
    "POST,/api/vorgaenge/1/abschliessen",
    "POST,/api/vorgaenge/1/wiedereroeffnen"
  })
  void jederSchreibweg_givenNoSession_thenAnswersUnauthorized(
      final String methode, final String pfad) {
    // When — ohne Sitzungs-Cookie.
    final ResponseEntity<String> antwort =
        rest.exchange(
            pfad,
            HttpMethod.valueOf(methode),
            new HttpEntity<>(rumpf("Website-Relaunch", Long.valueOf(1L), null)),
            String.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
