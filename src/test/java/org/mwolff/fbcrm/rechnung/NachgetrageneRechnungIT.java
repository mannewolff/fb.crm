package org.mwolff.fbcrm.rechnung;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.PasswordHasher;
import org.mwolff.fbcrm.auth.domain.Role;
import org.mwolff.fbcrm.common.Geschaeftszone;
import org.mwolff.fbcrm.rechnung.domain.DokumentSpeicher;
import org.mwolff.fbcrm.rechnung.web.NachtragResponse;
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
 * Die Wege der nachgetragenen Rechnung ueber HTTP, gegen eine echte PostgreSQL- und MinIO-Instanz
 * (#254; Plan #259, E11, E21, E26, E27; Issue #268).
 *
 * <p>Nur hier zu sehen: dass ein hochgeladenes Original Byte fuer Byte wieder herauskommt, dass die
 * Kopfzeilen der gestellten Rechnung samt den beiden Vorgaben aus Spring Security auch auf diesem
 * Weg ankommen, und dass das Loeschen das Objekt im Speicher nach dem Commit mitnimmt.
 *
 * <p>Der Nachweis der Zugangsregel steht auch hier: Der neue Pfad faellt ohne Aenderung an {@code
 * SecurityConfig} unter {@code /api/**} und ist ohne Sitzung verschlossen.
 */
class NachgetrageneRechnungIT extends AbstractIntegrationTest {

  private static final String MAIL = "manne@example.org";
  private static final String PASSWORT = "richtiges-passwort";
  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");
  private static final String FIRMA = "Adler AG";
  private static final String PFAD = "/api/nachgetragene-rechnungen";

  private static final byte[] PDF =
      "%PDF-1.7\nein frueheres Original\n%%EOF\n".getBytes(StandardCharsets.UTF_8);
  private static final byte[] SEITE =
      "<html><script>alert(document.cookie)</script></html>".getBytes(StandardCharsets.UTF_8);

  private final TestRestTemplate rest;
  private final AccountRepository accounts;
  private final PasswordHasher hasher;
  private final JdbcTemplate jdbc;
  private final DokumentSpeicher speicher;

  /** Das Rechnungsdatum muss im laufenden Geschaeftsjahr und nicht in der Zukunft liegen. */
  private final LocalDate heute = LocalDate.now(Geschaeftszone.ZONE);

  private HttpHeaders sitzung = new HttpHeaders();
  private long firmaId;

  @Autowired
  NachgetrageneRechnungIT(
      final TestRestTemplate rest,
      final AccountRepository accounts,
      final PasswordHasher hasher,
      final JdbcTemplate jdbc,
      final DokumentSpeicher speicher) {
    this.rest = rest;
    this.accounts = accounts;
    this.hasher = hasher;
    this.jdbc = jdbc;
    this.speicher = speicher;
  }

  @BeforeEach
  void leereDenBestandUndMeldeAn() {
    jdbc.execute("TRUNCATE rechnung_nachgetragen, ansprechpartner, firma RESTART IDENTITY CASCADE");
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
      final String pfad,
      final HttpMethod methode,
      final @Nullable Object rumpf,
      final Class<T> typ) {
    return rest.exchange(pfad, methode, new HttpEntity<>(rumpf, sitzung), typ);
  }

  private Map<String, Object> rumpf(final String nummer, final String netto, final String brutto) {
    final Map<String, Object> rumpf = new LinkedHashMap<>();
    rumpf.put("firmaId", Long.valueOf(firmaId));
    rumpf.put("nummer", nummer);
    rumpf.put("rechnungDatum", heute.toString());
    rumpf.put("netto", netto);
    rumpf.put("brutto", brutto);
    return rumpf;
  }

  private ResponseEntity<NachtragResponse> angelegt(final String nummer) {
    return ruf(PFAD, HttpMethod.POST, rumpf(nummer, "1000.00", "1190.00"), NachtragResponse.class);
  }

  private long nachgetragen(final String nummer) {
    return Objects.requireNonNull(angelegt(nummer).getBody()).id();
  }

  private <T> ResponseEntity<T> ladeHoch(final long id, final byte[] bytes, final Class<T> typ) {
    final HttpHeaders dateikopf = new HttpHeaders();
    dateikopf.setContentType(MediaType.APPLICATION_PDF);
    final MultiValueMap<String, Object> felder = new LinkedMultiValueMap<>();
    felder.add(
        "datei",
        new HttpEntity<>(
            new ByteArrayResource(bytes) {
              @Override
              public String getFilename() {
                return "rechnung.pdf";
              }
            },
            dateikopf));
    final HttpHeaders kopf = new HttpHeaders();
    kopf.addAll(sitzung);
    kopf.setContentType(MediaType.MULTIPART_FORM_DATA);
    return rest.exchange(
        PFAD + "/" + id + "/dokument", HttpMethod.POST, new HttpEntity<>(felder, kopf), typ);
  }

  private ResponseEntity<byte[]> dokument(final long id) {
    return ruf(PFAD + "/" + id + "/dokument", HttpMethod.GET, null, byte[].class);
  }

  private @Nullable String schluessel(final long id) {
    return jdbc.queryForObject(
        "SELECT pdf_schluessel FROM rechnung_nachgetragen WHERE id = ?", String.class, id);
  }

  @Test
  void anlegen_withoutPdf_thenCreatedAtItsLocationAndReadableThere() {
    // When
    final ResponseEntity<NachtragResponse> antwort = angelegt("  RE-2026-1  ");

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    final NachtragResponse rechnung = Objects.requireNonNull(antwort.getBody());
    assertThat(antwort.getHeaders().getLocation())
        .isEqualTo(URI.create(PFAD + "/" + rechnung.id()));
    assertThat(rechnung.nummer()).isEqualTo("RE-2026-1");
    assertThat(rechnung.rechnungDatum()).isEqualTo(heute);
    assertThat(rechnung.firmaName()).isEqualTo(FIRMA);
    assertThat(rechnung.zustand().name()).isEqualTo("GESTELLT");
    assertThat(rechnung.dokument()).isFalse();
    assertThat(
            ruf(PFAD + "/" + rechnung.id(), HttpMethod.GET, null, NachtragResponse.class).getBody())
        .isEqualTo(rechnung);
    assertThat(dokument(rechnung.id()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void anlegen_withPdf_thenTheDownloadCarriesTheSameBytesAndTheStrictHeaders() {
    // Given
    final long id = nachgetragen("RE-2026/2");

    // When
    final ResponseEntity<NachtragResponse> abgelegt = ladeHoch(id, PDF, NachtragResponse.class);
    final ResponseEntity<byte[]> antwort = dokument(id);

    // Then
    assertThat(abgelegt.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(Objects.requireNonNull(abgelegt.getBody()).dokument()).isTrue();
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(antwort.getBody()).isEqualTo(PDF);
    final HttpHeaders kopf = antwort.getHeaders();
    assertThat(kopf.getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
    assertThat(kopf.getFirst(HttpHeaders.CONTENT_DISPOSITION))
        .startsWith("attachment; filename=\"Rechnung-RE-2026-2.pdf\"");
    assertThat(kopf.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
    assertThat(kopf.getFirst("X-Frame-Options")).isEqualTo("DENY");
  }

  @Test
  void dokumentAblegen_withAFileThatIsNoPdf_thenUnprocessableAtDateiAndNothingStored() {
    // Given
    final long id = nachgetragen("RE-2026-3");

    // When
    final ResponseEntity<String> antwort = ladeHoch(id, SEITE, String.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(antwort.getBody()).contains("\"fieldErrors\"").contains("\"datei\"");
    assertThat(schluessel(id)).isNull();
  }

  @Test
  void zustand_toBezahltAndBack_thenTheAnswerCarriesEachNewStand() {
    // Given
    final long id = nachgetragen("RE-2026-4");

    // When
    final ResponseEntity<NachtragResponse> bezahlt =
        ruf(
            PFAD + "/" + id + "/zustand",
            HttpMethod.PUT,
            Map.of("zustand", "BEZAHLT"),
            NachtragResponse.class);
    final ResponseEntity<NachtragResponse> zurueck =
        ruf(
            PFAD + "/" + id + "/zustand",
            HttpMethod.PUT,
            Map.of("zustand", "GESTELLT"),
            NachtragResponse.class);

    // Then
    assertThat(bezahlt.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(Objects.requireNonNull(bezahlt.getBody()).zustand().name()).isEqualTo("BEZAHLT");
    assertThat(Objects.requireNonNull(zurueck.getBody()).zustand().name()).isEqualTo("GESTELLT");
  }

  @Test
  void aendern_thenTheNewEckdatenAreStored() {
    // Given
    final long id = nachgetragen("RE-2026-5");

    // When
    final ResponseEntity<NachtragResponse> antwort =
        ruf(
            PFAD + "/" + id,
            HttpMethod.PUT,
            rumpf("RE-2026-5a", "200.00", "238.00"),
            NachtragResponse.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
    final NachtragResponse geaendert = Objects.requireNonNull(antwort.getBody());
    assertThat(geaendert.nummer()).isEqualTo("RE-2026-5a");
    assertThat(geaendert.netto()).isEqualByComparingTo("200.00");
    assertThat(geaendert.brutto()).isEqualByComparingTo("238.00");
  }

  @Test
  void anlegen_withANummerAlreadyTakenInAnotherCase_thenUnprocessableAtNummer() {
    // Given
    nachgetragen("RE-2026-6");

    // When
    final ResponseEntity<String> antwort =
        ruf(PFAD, HttpMethod.POST, rumpf("re-2026-6", "1.00", "1.00"), String.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(antwort.getBody()).contains("\"nummer\"");
  }

  @Test
  void loeschen_thenTheRechnungAndItsObjectAreGone() {
    // Given
    final long id = nachgetragen("RE-2026-7");
    ladeHoch(id, PDF, NachtragResponse.class);
    final String objekt = Objects.requireNonNull(schluessel(id));

    // When
    final ResponseEntity<Void> antwort = ruf(PFAD + "/" + id, HttpMethod.DELETE, null, Void.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(ruf(PFAD + "/" + id, HttpMethod.GET, null, String.class).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThatThrownBy(() -> speicher.lies(objekt)).isInstanceOf(RuntimeException.class);
  }

  @Test
  void dokumentEntfernen_thenTheDownloadIsGoneAndTheRechnungStays() {
    // Given
    final long id = nachgetragen("RE-2026-8");
    ladeHoch(id, PDF, NachtragResponse.class);

    // When
    final ResponseEntity<Void> antwort =
        ruf(PFAD + "/" + id + "/dokument", HttpMethod.DELETE, null, Void.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(dokument(id).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(
            Objects.requireNonNull(
                    ruf(PFAD + "/" + id, HttpMethod.GET, null, NachtragResponse.class).getBody())
                .dokument())
        .isFalse();
  }

  @Test
  void withoutSitzung_thenTheWayIsClosed() {
    // Given
    final long id = nachgetragen("RE-2026-9");
    ladeHoch(id, PDF, NachtragResponse.class);
    sitzung = new HttpHeaders();

    // When / Then
    assertThat(dokument(id).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
