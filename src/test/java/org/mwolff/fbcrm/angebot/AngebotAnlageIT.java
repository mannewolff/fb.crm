package org.mwolff.fbcrm.angebot;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.angebot.web.AngebotAnlageResponse;
import org.mwolff.fbcrm.angebot.web.AngebotAnlagenResponse;
import org.mwolff.fbcrm.angebot.web.AngebotResponse;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.PasswordHasher;
import org.mwolff.fbcrm.auth.domain.Role;
import org.mwolff.fbcrm.common.Uploadgrenze;
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
 * Die Wege der Anlagen am Angebot ueber HTTP, mit Sitzung und gegen echte PostgreSQL- und
 * MinIO-Instanzen (Issue #148).
 *
 * <p>Hier steht die <b>Sicherheitsgrenze der Auslieferung</b>, und nur hier ist sie zu belegen:
 * Eine Anlage ist eine Datei von aussen, und ob sie im Browser als etwas anderes ankommt, als die
 * Anwendung festgestellt hat, haengt an echtem Tomcat, echtem Spring Security und einem echten
 * Datenstrom durch MinIO. Hochgeladen wird deshalb unter anderem eine HTML-Datei mit Skript, die
 * sich {@code bericht.pdf} nennt und sich als {@code text/html} ankuendigt — sie muss als {@code
 * application/octet-stream} und als Anhang wieder hinausgehen.
 *
 * <p>Dazu die Grenzen der Annahme: genau 26.214.400 Byte gehen durch, ein Byte mehr nicht, eine
 * leere Datei nicht; 401 ohne Sitzung auf allen vier Wegen; 404 bei unbekanntem Angebot und bei
 * einer Anlage eines anderen Angebots (E10); und 201 auch an einem Angebot im letzten Status
 * (Kriterium 3 der Quelle: der Status schraenkt Anlagen nicht ein).
 */
class AngebotAnlageIT extends AbstractIntegrationTest {

  private static final String MAIL = "manne@example.org";
  private static final String PASSWORT = "richtiges-passwort";
  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");
  private static final String FIRMA = "Adler AG";

  private static final String PDF_NAME = "Bericht.pdf";
  private static final byte[] PDF =
      "%PDF-1.7\nein winziges Dokument\n%%EOF\n".getBytes(StandardCharsets.UTF_8);

  private static final String GETARNT = "bericht.pdf";
  private static final byte[] SEITE =
      "<html><script>alert(document.cookie)</script></html>".getBytes(StandardCharsets.UTF_8);

  private final TestRestTemplate rest;
  private final AccountRepository accounts;
  private final PasswordHasher hasher;
  private final JdbcTemplate jdbc;

  private HttpHeaders sitzung = new HttpHeaders();
  private long firmaId;

  @Autowired
  AngebotAnlageIT(
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
        "TRUNCATE angebot_anlage, angebot_kommentar, angebot_position, angebot, ansprechpartner,"
            + " firma RESTART IDENTITY CASCADE");
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
    return "/api/angebote/" + angebotId + "/anlagen";
  }

  private String inhaltsPfad(final long angebotId, final long anlageId) {
    return pfad(angebotId) + "/" + anlageId + "/inhalt";
  }

  private <T> ResponseEntity<T> ladeHoch(
      final long angebotId,
      final String dateiName,
      final MediaType gemeldeteArt,
      final byte[] bytes,
      final Class<T> typ) {
    final HttpHeaders dateikopf = new HttpHeaders();
    dateikopf.setContentType(gemeldeteArt);
    final MultiValueMap<String, Object> felder = new LinkedMultiValueMap<>();
    felder.add(
        "datei",
        new HttpEntity<>(
            new ByteArrayResource(bytes) {
              @Override
              public String getFilename() {
                return dateiName;
              }
            },
            dateikopf));
    final HttpHeaders kopf = new HttpHeaders();
    kopf.addAll(sitzung);
    kopf.setContentType(MediaType.MULTIPART_FORM_DATA);
    return rest.exchange(pfad(angebotId), HttpMethod.POST, new HttpEntity<>(felder, kopf), typ);
  }

  private AngebotAnlageResponse hochgeladen(
      final long angebotId,
      final String dateiName,
      final MediaType gemeldeteArt,
      final byte[] bytes) {
    final ResponseEntity<AngebotAnlageResponse> antwort =
        ladeHoch(angebotId, dateiName, gemeldeteArt, bytes, AngebotAnlageResponse.class);
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return Objects.requireNonNull(antwort.getBody());
  }

  private List<AngebotAnlageResponse> liste(final long angebotId) {
    return Objects.requireNonNull(
            ruf(pfad(angebotId), HttpMethod.GET, null, AngebotAnlagenResponse.class).getBody())
        .anlagen();
  }

  private ResponseEntity<byte[]> inhalt(final long angebotId, final long anlageId) {
    return ruf(inhaltsPfad(angebotId, anlageId), HttpMethod.GET, null, byte[].class);
  }

  @Test
  void hochladen_thenTheAnlageIsInTheListWithNameSizeKindAndTime() {
    // Given — Kriterien 1, 2, 4.
    final long angebotId = angebot();

    // When
    final AngebotAnlageResponse angelegt =
        hochgeladen(angebotId, PDF_NAME, MediaType.APPLICATION_PDF, PDF);

    // Then
    assertThat(angelegt.dateiName()).isEqualTo(PDF_NAME);
    assertThat(angelegt.groesse()).isEqualTo(PDF.length);
    assertThat(angelegt.createdAt()).isNotNull();
    assertThat(liste(angebotId))
        .extracting(anlage -> Long.valueOf(anlage.id()))
        .containsExactly(Long.valueOf(angelegt.id()));
  }

  @Test
  void liste_withTwoAnlagen_thenTheNewestComesFirst() {
    // Given — Kriterium 4.
    final long angebotId = angebot();
    final long erste = hochgeladen(angebotId, "Erste.pdf", MediaType.APPLICATION_PDF, PDF).id();
    final long zweite = hochgeladen(angebotId, "Zweite.pdf", MediaType.APPLICATION_PDF, PDF).id();

    // When
    final List<AngebotAnlageResponse> anlagen = liste(angebotId);

    // Then
    assertThat(anlagen)
        .extracting(anlage -> Long.valueOf(anlage.id()))
        .containsExactly(Long.valueOf(zweite), Long.valueOf(erste));
  }

  @Test
  void inhalt_thenTheBodyIsTheUploadedFileByteForByte() {
    // Given — Kriterium 9; nur gegen echtes MinIO und echtes Tomcat aussagekraeftig.
    final long angebotId = angebot();
    final long anlageId = hochgeladen(angebotId, PDF_NAME, MediaType.APPLICATION_PDF, PDF).id();

    // When
    final ResponseEntity<byte[]> antwort = inhalt(angebotId, anlageId);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(antwort.getBody()).isEqualTo(PDF);
  }

  @Test
  void inhalt_thenCarriesTheFourDeliveryHeaders() {
    // Given — E5, E6: attachment statt Anzeige, nosniff gegen das Erraten des Typs, sandbox als
    // letzte Schranke, und DENY bleibt scharf — die Vorschau kommt ohne Einbetten aus.
    final long angebotId = angebot();
    final long anlageId = hochgeladen(angebotId, PDF_NAME, MediaType.APPLICATION_PDF, PDF).id();

    // When
    final HttpHeaders kopf = inhalt(angebotId, anlageId).getHeaders();

    // Then
    assertThat(kopf.getFirst(HttpHeaders.CONTENT_DISPOSITION)).startsWith("attachment");
    assertThat(kopf.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
    assertThat(kopf.getFirst("Content-Security-Policy")).isEqualTo("sandbox");
    assertThat(kopf.getFirst("X-Frame-Options")).isEqualTo("DENY");
  }

  @Test
  void hochladen_givenAnHtmlFileNamedPdf_thenNoPreviewKind() {
    // Given — Kriterium 15, E4: erkannt wird am Inhalt, nicht am Namen und nicht an der gemeldeten
    // Art.
    final long angebotId = angebot();

    // When
    final AngebotAnlageResponse angelegt =
        hochgeladen(angebotId, GETARNT, MediaType.TEXT_HTML, SEITE);

    // Then
    assertThat(angelegt.vorschauArt()).isNull();
  }

  @Test
  void inhalt_givenAnHtmlFileNamedPdf_thenItGoesOutAsOctetStream() {
    // Given — die eigentliche Grenze: die Anwendung hat eine Origin und ein Konto mit allen
    // Rechten. Lieferte sie diese Datei so aus, wie sie hereinkam, genuegte ein Klick in der
    // eigenen Angebotsansicht, um fremdes Skript in der Sitzung laufen zu lassen.
    final long angebotId = angebot();
    final long anlageId = hochgeladen(angebotId, GETARNT, MediaType.TEXT_HTML, SEITE).id();

    // When
    final ResponseEntity<byte[]> antwort = inhalt(angebotId, anlageId);

    // Then
    assertThat(antwort.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
    assertThat(antwort.getBody()).isEqualTo(SEITE);
  }

  @Test
  void inhalt_givenAPdf_thenItGoesOutAsApplicationPdf() {
    // Given — E5: der Typ folgt der am Inhalt erkannten Vorschauart.
    final long angebotId = angebot();
    final long anlageId = hochgeladen(angebotId, PDF_NAME, MediaType.APPLICATION_PDF, PDF).id();

    // When
    final ResponseEntity<byte[]> antwort = inhalt(angebotId, anlageId);

    // Then
    assertThat(antwort.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
  }

  @Test
  void hochladen_givenExactlyTheLimit_thenAccepted() {
    // Given — Kriterium 6: die Grenze selbst geht noch durch, und sie geht durch den echten
    // Multipart-Riegel des Containers (E8).
    final long angebotId = angebot();

    // When
    final AngebotAnlageResponse angelegt =
        hochgeladen(
            angebotId,
            "Grenze.bin",
            MediaType.APPLICATION_OCTET_STREAM,
            new byte[(int) Uploadgrenze.MAX_BYTE]);

    // Then
    assertThat(angelegt.groesse()).isEqualTo(Uploadgrenze.MAX_BYTE);
  }

  @Test
  void hochladen_givenOneByteAboveTheLimit_thenRejectedWithTheLimitMessage() {
    // Given — Kriterium 6: der Absender soll die Grenze lesen, nicht einen Netzfehler sehen.
    final long angebotId = angebot();

    // When
    final ResponseEntity<String> antwort =
        ladeHoch(
            angebotId,
            "ZuGross.bin",
            MediaType.APPLICATION_OCTET_STREAM,
            new byte[(int) Uploadgrenze.MAX_BYTE + 1],
            String.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(antwort.getBody()).contains(Uploadgrenze.MELDUNG);
    assertThat(liste(angebotId)).isEmpty();
  }

  @Test
  void hochladen_givenAnEmptyFile_thenAnswers400AndStoresNothing() {
    // Given — Kriterium 5.
    final long angebotId = angebot();

    // When
    final ResponseEntity<String> antwort =
        ladeHoch(angebotId, "Leer.txt", MediaType.TEXT_PLAIN, new byte[0], String.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(liste(angebotId)).isEmpty();
  }

  @Test
  void hochladen_twiceTheSameFile_thenTwoRowsWithDifferentIdsAndBothContents() {
    // Given — Kriterium 8: zwei Anlagen mit demselben Dateinamen sind erlaubt, und die zweite
    // ueberschreibt die erste nicht.
    final long angebotId = angebot();

    // When
    final long erste = hochgeladen(angebotId, PDF_NAME, MediaType.APPLICATION_PDF, PDF).id();
    final long zweite = hochgeladen(angebotId, PDF_NAME, MediaType.APPLICATION_PDF, PDF).id();

    // Then
    assertThat(zweite).isNotEqualTo(erste);
    assertThat(liste(angebotId))
        .extracting(AngebotAnlageResponse::dateiName)
        .containsExactly(PDF_NAME, PDF_NAME);
    assertThat(inhalt(angebotId, erste).getBody()).isEqualTo(PDF);
    assertThat(inhalt(angebotId, zweite).getBody()).isEqualTo(PDF);
  }

  @Test
  void loeschen_thenTheContentRouteAnswers404AndTheListDoesNotNameItAnymore() {
    // Given — Kriterium 10.
    final long angebotId = angebot();
    final long anlageId = hochgeladen(angebotId, PDF_NAME, MediaType.APPLICATION_PDF, PDF).id();

    // When
    final ResponseEntity<Void> antwort =
        ruf(pfad(angebotId) + "/" + anlageId, HttpMethod.DELETE, null, Void.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(liste(angebotId)).isEmpty();
    assertThat(inhalt(angebotId, anlageId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void liste_forAnUnknownAngebot_thenAnswers404() {
    // When / Then — E10: eine unbekannte Kennung im Pfad ist 404 und keine leere Liste.
    assertThat(ruf(pfad(9999L), HttpMethod.GET, null, String.class).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void inhalt_ofAnAnlageOfAnotherAngebot_thenAnswers404() {
    // Given — E10: die Kennung der Anlage allein genuegt nicht.
    final long eigenes = angebot();
    final long fremdes = angebot();
    final long anlageId = hochgeladen(fremdes, PDF_NAME, MediaType.APPLICATION_PDF, PDF).id();

    // When / Then
    assertThat(inhalt(eigenes, anlageId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(inhalt(fremdes, anlageId).getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void hochladen_toAnAngebotInTheLastStatus_thenAnswersCreated() {
    // Given — der Status schraenkt Anlagen nicht ein.
    final long angebotId = angebot();
    jdbc.update("UPDATE angebot SET status = 'ABGERECHNET' WHERE id = ?", Long.valueOf(angebotId));

    // When
    final ResponseEntity<AngebotAnlageResponse> antwort =
        ladeHoch(angebotId, PDF_NAME, MediaType.APPLICATION_PDF, PDF, AngebotAnlageResponse.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

  @Test
  void anlagenwege_withoutASession_thenUnauthorized() {
    // Given — jeder der vier Pfade unter /api ist ohne Sitzung verschlossen (E13).
    final long angebotId = angebot();
    final long anlageId = hochgeladen(angebotId, PDF_NAME, MediaType.APPLICATION_PDF, PDF).id();
    sitzung = new HttpHeaders();

    // When / Then
    assertThat(ruf(pfad(angebotId), HttpMethod.GET, null, String.class).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(
            ladeHoch(angebotId, PDF_NAME, MediaType.APPLICATION_PDF, PDF, String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(inhalt(angebotId, anlageId).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(
            ruf(pfad(angebotId) + "/" + anlageId, HttpMethod.DELETE, null, String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
