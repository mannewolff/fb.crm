package org.mwolff.fbcrm.vorgang.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.PasswordHasher;
import org.mwolff.fbcrm.auth.domain.Role;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
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
 * Die Auslieferung eines Anhangs ueber HTTP — die Sicherheitsgrenze des Stands (Kriterium 17, E11).
 *
 * <p>Hochgeladen wird eine HTML-Datei <b>mit</b> {@code Content-Type: text/html} und einem Skript
 * darin. Das ist der einzige Fall, der wirklich etwas beweist: Die Anwendung hat eine Origin und
 * ein Konto mit allen Rechten. Lieferte sie diese Datei so aus, wie sie hereinkam, genuegte ein
 * Klick in der eigenen Historie, um fremdes Skript in der Sitzung des Benutzers laufen zu lassen.
 *
 * <p>Geprueft wird deshalb die ganze Kette der Kopfzeilen: {@code attachment} statt Anzeige, {@code
 * application/octet-stream} statt des hochgeladenen Typs, {@code nosniff} gegen das Erraten des
 * Typs durch den Browser und {@code sandbox} als letzte Schranke, falls doch etwas gerendert wird.
 * {@code nosniff} kommt aus den Spring-Security-Defaults und wird hier nur mitgeprueft — dass es
 * <b>auch auf diesem Weg</b> ankommt, steht nirgends sonst.
 *
 * <p>Nur gegen echtes MinIO und echtes Tomcat aussagekraeftig: Ob der Rumpf Byte fuer Byte ankommt,
 * haengt am Datenstrom durch beide hindurch.
 */
class AnhangAuslieferungIT extends AbstractIntegrationTest {

  private static final String MAIL = "manne@example.org";
  private static final String PASSWORT = "richtiges-passwort";
  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");
  private static final Instant GESTERN = Instant.parse("2026-09-11T14:30:00Z");
  private static final String DATEINAME = "seite.html";
  private static final byte[] SEITE =
      "<html><script>alert(document.cookie)</script></html>".getBytes(StandardCharsets.UTF_8);

  private final TestRestTemplate rest;
  private final AccountRepository accounts;
  private final PasswordHasher hasher;
  private final FirmaRepository firmen;
  private final JdbcTemplate jdbc;

  private HttpHeaders sitzung = new HttpHeaders();
  private String pfad = "";

  @Autowired
  AnhangAuslieferungIT(
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
  void ladeEineHtmlDateiHoch() {
    jdbc.execute(
        "TRUNCATE vorgang_eintrag, vorgang, ansprechpartner, firma RESTART IDENTITY CASCADE");
    jdbc.update("UPDATE vorgang_nummernkreis SET naechste = 1");
    jdbc.execute("TRUNCATE outbox_message, password_reset_token, account RESTART IDENTITY CASCADE");
    accounts.save(
        new Account(null, MAIL, "Manne", hasher.hash(PASSWORT), Role.ADMIN, 0, ANGELEGT, ANGELEGT));
    sitzung = angemeldeterKopf();
    final long vorgangId = neuerVorgang();
    hochladen(vorgangId);
    pfad = "/api/vorgaenge/" + vorgangId + "/eintraege/" + eintragId(vorgangId) + "/datei";
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

  private void hochladen(final long vorgangId) {
    final HttpHeaders dateikopf = new HttpHeaders();
    dateikopf.setContentType(MediaType.TEXT_HTML);
    final MultiValueMap<String, Object> felder = new LinkedMultiValueMap<>();
    felder.add("art", "ANHANG");
    felder.add("geschehenAm", GESTERN.toString());
    felder.add(
        "datei",
        new HttpEntity<>(
            new ByteArrayResource(SEITE) {
              @Override
              public String getFilename() {
                return DATEINAME;
              }
            },
            dateikopf));
    final HttpHeaders kopf = new HttpHeaders();
    kopf.addAll(sitzung);
    kopf.setContentType(MediaType.MULTIPART_FORM_DATA);
    rest.exchange(
        "/api/vorgaenge/" + vorgangId + "/eintraege",
        HttpMethod.POST,
        new HttpEntity<>(felder, kopf),
        Void.class);
  }

  private long eintragId(final long vorgangId) {
    return Objects.requireNonNull(
            rest.exchange(
                    "/api/vorgaenge/" + vorgangId,
                    HttpMethod.GET,
                    new HttpEntity<>(sitzung),
                    VorgangResponse.class)
                .getBody())
        .historie()
        .getFirst()
        .id();
  }

  private ResponseEntity<byte[]> herunterladen(final HttpHeaders kopf) {
    return rest.exchange(pfad, HttpMethod.GET, new HttpEntity<>(kopf), byte[].class);
  }

  @Test
  void datei_thenAnswersOk() {
    // When — Kriterium 17.
    final ResponseEntity<byte[]> antwort = herunterladen(sitzung);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void datei_thenOffersItForSavingInsteadOfShowingIt() {
    // Given — E11.

    // When
    final ResponseEntity<byte[]> antwort = herunterladen(sitzung);

    // Then
    assertThat(antwort.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
        .startsWith("attachment");
  }

  @Test
  void datei_givenAnUploadedHtmlFile_thenAnswersOctetStream() {
    // Given — E11: der hochgeladene Typ geht nie wieder hinaus.

    // When
    final ResponseEntity<byte[]> antwort = herunterladen(sitzung);

    // Then
    assertThat(antwort.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
  }

  @Test
  void datei_thenForbidsTypeSniffing() {
    // Given — aus den Spring-Security-Defaults, hier nur mitgeprueft (Plan-Review Fund 12).

    // When
    final ResponseEntity<byte[]> antwort = herunterladen(sitzung);

    // Then
    assertThat(antwort.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
  }

  @Test
  void datei_thenCarriesTheSandboxPolicy() {
    // Given — E11: die letzte Schranke, falls ein Browser den Inhalt doch rendert.

    // When
    final ResponseEntity<byte[]> antwort = herunterladen(sitzung);

    // Then
    assertThat(antwort.getHeaders().getFirst(Anhangkopf.INHALTSREGEL))
        .contains(Anhangkopf.SANDKASTEN);
  }

  @Test
  void datei_thenTheBodyIsTheUploadedFileByteForByte() {
    // Given — Kriterium 17.

    // When
    final ResponseEntity<byte[]> antwort = herunterladen(sitzung);

    // Then
    assertThat(antwort.getBody()).isEqualTo(SEITE);
  }

  @Test
  void datei_givenNoSession_thenAnswersUnauthorized() {
    // When — ohne Sitzungs-Cookie.
    final ResponseEntity<byte[]> antwort = herunterladen(new HttpHeaders());

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
