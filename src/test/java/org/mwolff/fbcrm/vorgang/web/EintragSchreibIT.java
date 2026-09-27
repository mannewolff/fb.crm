package org.mwolff.fbcrm.vorgang.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
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
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.common.Uploadgrenze;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
import org.mwolff.fbcrm.vorgang.domain.Eintragsart;
import org.mwolff.fbcrm.vorgang.domain.Herkunft;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Die Schreibwege der Historie ueber HTTP, mit Sitzung, gegen echtes PostgreSQL und echtes MinIO.
 *
 * <p>Vier Zusagen sind nur hier pruefbar, weil sie an der echten Strecke aus Container, Tomcat und
 * Objektspeicher haengen.
 *
 * <p>Erstens die drei Groessenfaelle aus E10: 25 MiB gehen durch, ein Byte mehr kommt als
 * Feldmeldung mit der Grenze zurueck, und ein Rumpf jenseits des Container-Riegels liefert trotzdem
 * eine Antwort mit der Grenze im Text — statt eines abgebrochenen Uploads. Nur der letzte Fall
 * zeigt, ob {@code server.tomcat.max-swallow-size} hoch genug steht; mit dem Vorgabewert von 2 MB
 * schliesst Tomcat die Verbindung, und der Browser meldete einen Netzfehler.
 *
 * <p>Zweitens der Weg der Feldmeldungen: Was die klassenweite Constraint an ein Feld haengt, muss
 * als {@code fieldErrors} bis zum Aufrufer durchkommen.
 *
 * <p>Drittens E9 und E13 zusammen: Der Dateiname wird gesaeubert gespeichert, und der
 * Objektschluessel traegt keinen Teil davon. Das ist eine Aussage ueber die Zeile in der Datenbank
 * und wird dort nachgesehen.
 *
 * <p>Viertens die Zugangsregel: Ein neuer Pfad unter {@code /api} ist ohne Sitzung verschlossen.
 */
class EintragSchreibIT extends AbstractIntegrationTest {

  private static final String MAIL = "manne@example.org";
  private static final String PASSWORT = "richtiges-passwort";
  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");
  private static final Instant GESTERN = Instant.parse("2026-09-11T14:30:00Z");

  private final TestRestTemplate rest;
  private final AccountRepository accounts;
  private final PasswordHasher hasher;
  private final FirmaRepository firmen;
  private final JdbcTemplate jdbc;

  private HttpHeaders sitzung = new HttpHeaders();
  private long vorgangId;

  @Autowired
  EintragSchreibIT(
      final TestRestTemplate rest,
      final AccountRepository accounts,
      final PasswordHasher hasher,
      final FirmaRepository firmen,
      final JdbcTemplate jdbc) {
    this.rest = rest;
    this.accounts = accounts;
    this.hasher = hasher;
    this.firmen = firmen;
    this.jdbc = jdbc;
  }

  @BeforeEach
  void leereDenBestandUndLegeEinenVorgangAn() {
    jdbc.execute(
        "TRUNCATE vorgang_eintrag, vorgang, ansprechpartner, firma RESTART IDENTITY CASCADE");
    jdbc.update("UPDATE vorgang_nummernkreis SET naechste = 1");
    jdbc.execute("TRUNCATE outbox_message, password_reset_token, account RESTART IDENTITY CASCADE");
    accounts.save(
        new Account(null, MAIL, "Manne", hasher.hash(PASSWORT), Role.ADMIN, 0, ANGELEGT, ANGELEGT));
    sitzung = angemeldeterKopf();
    vorgangId = neuerVorgang();
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

  private long neuerVorgang() {
    final long firmaId =
        firmen
            .save(
                new Firma(
                    null,
                    "Adler AG",
                    new Anschrift("Am Wall 1", "28195", "Bremen", "Deutschland"),
                    null,
                    null,
                    true,
                    ANGELEGT,
                    ANGELEGT))
            .requireId();
    return Objects.requireNonNull(
            rest.exchange(
                    "/api/vorgaenge",
                    HttpMethod.POST,
                    new HttpEntity<>(
                        Map.of("titel", "Website-Relaunch", "firmaId", Long.valueOf(firmaId)),
                        sitzung),
                    VorgangAngelegtResponse.class)
                .getBody())
        .id();
  }

  private static MultiValueMap<String, Object> teile(final String art, final Instant geschehenAm) {
    final MultiValueMap<String, Object> felder = new LinkedMultiValueMap<>();
    felder.add("art", art);
    felder.add("geschehenAm", geschehenAm.toString());
    return felder;
  }

  private static HttpEntity<ByteArrayResource> dateiTeil(final String name, final byte[] inhalt) {
    final HttpHeaders kopf = new HttpHeaders();
    kopf.setContentType(MediaType.APPLICATION_PDF);
    return new HttpEntity<>(
        new ByteArrayResource(inhalt) {
          @Override
          public String getFilename() {
            return name;
          }
        },
        kopf);
  }

  private <T> ResponseEntity<T> hinzufuegen(
      final MultiValueMap<String, Object> felder, final Class<T> typ) {
    final HttpHeaders kopf = new HttpHeaders();
    kopf.addAll(sitzung);
    kopf.setContentType(MediaType.MULTIPART_FORM_DATA);
    return rest.exchange(
        "/api/vorgaenge/" + vorgangId + "/eintraege",
        HttpMethod.POST,
        new HttpEntity<>(felder, kopf),
        typ);
  }

  private <T> ResponseEntity<T> kommentar(final String text, final Class<T> typ) {
    final MultiValueMap<String, Object> felder = teile("KOMMENTAR", GESTERN);
    felder.add("text", text);
    return hinzufuegen(felder, typ);
  }

  private <T> ResponseEntity<T> anhang(final String name, final int groesse, final Class<T> typ) {
    final MultiValueMap<String, Object> felder = teile("ANHANG", GESTERN);
    felder.add("datei", dateiTeil(name, new byte[groesse]));
    return hinzufuegen(felder, typ);
  }

  private VorgangResponse detail() {
    return Objects.requireNonNull(
        rest.exchange(
                "/api/vorgaenge/" + vorgangId,
                HttpMethod.GET,
                new HttpEntity<>(sitzung),
                VorgangResponse.class)
            .getBody());
  }

  private EintragResponse einzigerEintrag() {
    return detail().historie().getFirst();
  }

  private static JsonNode feldfehler(final ResponseEntity<JsonNode> antwort) {
    return Objects.requireNonNull(antwort.getBody()).path("fieldErrors");
  }

  @Test
  void hinzufuegen_givenAComment_thenAnswersCreated() {
    // When — Kriterium 13.
    final ResponseEntity<Void> antwort = kommentar("Angerufen", Void.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

  @Test
  void hinzufuegen_givenAComment_thenItAppearsInTheHistoryByHand() {
    // Given — Kriterien 15, 16.

    // When
    kommentar("Angerufen", Void.class);

    // Then
    assertThat(einzigerEintrag())
        .extracting(
            EintragResponse::art,
            EintragResponse::text,
            EintragResponse::geschehenAm,
            EintragResponse::herkunft)
        .containsExactly(Eintragsart.KOMMENTAR, "Angerufen", GESTERN, Herkunft.VON_HAND);
  }

  @Test
  void hinzufuegen_givenAnAttachment_thenItAppearsWithNameAndSize() {
    // Given — Kriterium 15.

    // When
    anhang("Angebot.pdf", 4096, Void.class);

    // Then
    assertThat(einzigerEintrag())
        .extracting(EintragResponse::art, EintragResponse::dateiName, EintragResponse::dateiGroesse)
        .containsExactly(Eintragsart.ANHANG, "Angebot.pdf", Long.valueOf(4096L));
  }

  @Test
  void hinzufuegen_givenAFileNameWithAPath_thenStoresOnlyTheName() {
    // Given — E13: der Name kommt von aussen.

    // When
    anhang("../../x.pdf", 16, Void.class);

    // Then
    assertThat(einzigerEintrag().dateiName()).isEqualTo("x.pdf");
  }

  @Test
  void hinzufuegen_givenAFileNameWithAPath_thenTheObjectKeyCarriesNoPartOfIt() {
    // Given — E9: der Schluessel ist die interne Adresse und traegt keinen Dateinamen.

    // When
    anhang("../../x.pdf", 16, Void.class);

    // Then
    assertThat(jdbc.queryForObject("SELECT objekt_schluessel FROM vorgang_eintrag", String.class))
        .matches("vorgang/" + vorgangId + "/[0-9a-f-]{36}");
  }

  @Test
  void hinzufuegen_givenExactlyTheMaximumSize_thenAnswersCreated() {
    // Given — E10: 25 MiB gehen durch.

    // When
    final ResponseEntity<Void> antwort =
        anhang("gross.pdf", (int) Uploadgrenze.MAX_BYTE, Void.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

  @Test
  void hinzufuegen_givenOneByteBeyondTheMaximum_thenNamesTheLimitOnTheFileField() {
    // Given — Kriterium 18.

    // When
    final ResponseEntity<JsonNode> antwort =
        anhang("gross.pdf", (int) Uploadgrenze.MAX_BYTE + 1, JsonNode.class);

    // Then
    assertThat(feldfehler(antwort).path(EintragConstraint.FELD_DATEI).path(0).asText())
        .isEqualTo(Uploadgrenze.MELDUNG);
  }

  @Test
  void hinzufuegen_givenABodyBeyondTheContainerLimit_thenAnswersBadRequestWithTheLimit() {
    // Given — E10, Fund 5: der Riegel des Containers greift, und die Antwort erreicht den
    // Aufrufer trotzdem. 30 MB liegen ueber max-request-size (27MB) und unter max-swallow-size
    // (32MB) — genau das Fenster, in dem Tomcat den Rumpf noch wegliest, statt die Verbindung
    // abzuschneiden.

    // When
    final ResponseEntity<JsonNode> antwort = anhang("riesig.pdf", 30_000_000, JsonNode.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(Objects.requireNonNull(antwort.getBody()).path("detail").asText())
        .isEqualTo(Uploadgrenze.MELDUNG);
  }

  @Test
  void hinzufuegen_givenACommentWithoutText_thenNamesTheTextField() {
    // Given — Kriterium 13.

    // When
    final ResponseEntity<JsonNode> antwort = kommentar("   ", JsonNode.class);

    // Then
    assertThat(feldfehler(antwort).path(EintragConstraint.FELD_TEXT).path(0).asText())
        .isEqualTo(EintragConstraint.TEXT_FEHLT);
  }

  @Test
  void hinzufuegen_givenAnAttachmentWithoutAFile_thenNamesTheFileField() {
    // Given — Kriterium 13.

    // When
    final ResponseEntity<JsonNode> antwort = hinzufuegen(teile("ANHANG", GESTERN), JsonNode.class);

    // Then
    assertThat(feldfehler(antwort).path(EintragConstraint.FELD_DATEI).path(0).asText())
        .isEqualTo(EintragConstraint.DATEI_FEHLT);
  }

  @Test
  void hinzufuegen_givenAMomentInTheFuture_thenNamesTheMomentField() {
    // Given — Kriterium 14.
    final MultiValueMap<String, Object> felder =
        teile("KOMMENTAR", Instant.now().plusSeconds(3600));
    felder.add("text", "Angerufen");

    // When
    final ResponseEntity<JsonNode> antwort = hinzufuegen(felder, JsonNode.class);

    // Then
    assertThat(feldfehler(antwort).path(EintragConstraint.FELD_ZEITPUNKT).path(0).asText())
        .isEqualTo(EintragConstraint.ZUKUNFT);
  }

  @Test
  void hinzufuegen_givenAnUnknownVorgang_thenAnswersNotFound() {
    // Given
    vorgangId = 4711L;

    // When
    final ResponseEntity<String> antwort = kommentar("Angerufen", String.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void aendern_thenAnswersNoContent() {
    // Given — Kriterium 18.
    kommentar("Angerufen", Void.class);

    // When
    final ResponseEntity<Void> antwort =
        aendern(einzigerEintrag().id(), "Doch geschrieben", GESTERN, Void.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  void aendern_thenTheHistoryShowsTheNewTextAndTheChangeMark() {
    // Given — Kriterium 19: der Vermerk „geaendert" haengt an geaendertAm.
    kommentar("Angerufen", Void.class);

    // When
    aendern(einzigerEintrag().id(), "Doch geschrieben", GESTERN, Void.class);

    // Then
    assertThat(einzigerEintrag())
        .extracting(EintragResponse::text, e -> e.geaendertAm() != null)
        .containsExactly("Doch geschrieben", true);
  }

  @Test
  void aendern_givenAnAttachment_thenLeavesTheFileUntouched() {
    // Given — geaendert werden Text und Zeitpunkt, nicht die Datei.
    anhang("Angebot.pdf", 4096, Void.class);
    final String schluessel =
        jdbc.queryForObject("SELECT objekt_schluessel FROM vorgang_eintrag", String.class);

    // When
    aendern(einzigerEintrag().id(), "Das Angebot", GESTERN, Void.class);

    // Then
    assertThat(jdbc.queryForObject("SELECT objekt_schluessel FROM vorgang_eintrag", String.class))
        .isEqualTo(schluessel);
  }

  @Test
  void aendern_givenNoText_thenNamesTheTextField() {
    // Given — die Meldung steht am Feld.
    kommentar("Angerufen", Void.class);

    // When
    final ResponseEntity<JsonNode> antwort =
        aendern(einzigerEintrag().id(), "   ", GESTERN, JsonNode.class);

    // Then
    assertThat(feldfehler(antwort).has(EintragConstraint.FELD_TEXT)).isTrue();
  }

  @Test
  void aendern_givenAMomentInTheFuture_thenNamesTheMomentField() {
    // Given — Kriterium 14 gilt auch beim Aendern.
    kommentar("Angerufen", Void.class);

    // When
    final ResponseEntity<JsonNode> antwort =
        aendern(
            einzigerEintrag().id(),
            "Doch geschrieben",
            Instant.now().plusSeconds(3600),
            JsonNode.class);

    // Then
    assertThat(feldfehler(antwort).path(EintragConstraint.FELD_ZEITPUNKT).path(0).asText())
        .isEqualTo(EintragConstraint.ZUKUNFT);
  }

  @Test
  void aendern_givenAnEintragOfAnotherVorgang_thenAnswersNotFound() {
    // Given — der Eintrag haengt am ersten Vorgang, angefragt wird er unter einem zweiten.
    kommentar("Angerufen", Void.class);
    final long fremder = einzigerEintrag().id();
    vorgangId = neuerVorgang();

    // When
    final ResponseEntity<String> antwort =
        aendern(fremder, "Doch geschrieben", GESTERN, String.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  private <T> ResponseEntity<T> aendern(
      final long eintragId, final String text, final Instant geschehenAm, final Class<T> typ) {
    return rest.exchange(
        "/api/vorgaenge/" + vorgangId + "/eintraege/" + eintragId,
        HttpMethod.PUT,
        new HttpEntity<>(Map.of("text", text, "geschehenAm", geschehenAm.toString()), sitzung),
        typ);
  }

  @ParameterizedTest(name = "{0} {1} ohne Sitzung")
  @CsvSource({"POST,/api/vorgaenge/1/eintraege", "PUT,/api/vorgaenge/1/eintraege/1"})
  void jederSchreibweg_givenNoSession_thenAnswersUnauthorized(
      final String methode, final String pfad) {
    // When — ohne Sitzungs-Cookie.
    final ResponseEntity<String> antwort =
        rest.exchange(
            pfad,
            HttpMethod.valueOf(methode),
            new HttpEntity<>(Map.of("text", "Angerufen", "geschehenAm", GESTERN.toString())),
            String.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
