package org.mwolff.fbcrm.rechnung;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
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
import org.mwolff.fbcrm.common.Geschaeftszone;
import org.mwolff.fbcrm.jahresabschluss.web.JahresabschlussResponse;
import org.mwolff.fbcrm.rechnung.domain.DokumentSpeicher;
import org.mwolff.fbcrm.rechnung.web.NachtragResponse;
import org.mwolff.fbcrm.rechnung.web.RechnungenUebersichtResponse;
import org.mwolff.fbcrm.startseite.web.StartseiteResponse;
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
 * <p><b>Geloescht wird mit Kennzeichen</b> (#411, Kriterium 6; Plan #415, E16, E17): Je
 * Filterstelle steht hier ein Test, dass eine geloeschte nachgetragene Rechnung nicht mitzaehlt —
 * Liste, Einzelabruf, Nummer, Startseite und Jahresabschluss. Ihr Original ist nach dem Commit aus
 * dem Speicher fort, und jeder schreibende Weg an ihr antwortet 409.
 *
 * <p>Der Nachweis der Zugangsregel steht auch hier: Der neue Pfad faellt ohne Aenderung an {@code
 * SecurityConfig} unter {@code /api/**} und ist ohne Sitzung verschlossen.
 */
class NachgetrageneRechnungIT extends AbstractIntegrationTest {

  private static final String MAIL = "manne@example.org";
  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");
  private static final String FIRMA = "Adler AG";
  private static final String PFAD = "/api/nachgetragene-rechnungen";
  private static final String TABELLE = "rechnung_nachgetragen";

  private static final byte[] PDF =
      "%PDF-1.7\nein frueheres Original\n%%EOF\n".getBytes(StandardCharsets.UTF_8);
  private static final byte[] SEITE =
      "<html><script>alert(document.cookie)</script></html>".getBytes(StandardCharsets.UTF_8);

  private final TestRestTemplate rest;
  private final AccountRepository accounts;
  private final SecretHasher hasher;
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
      final SecretHasher hasher,
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
    leereKonten(jdbc);
    accounts.save(Konten.neu(MAIL, ANGELEGT));
    sitzung = Anmeldung.perCode(rest, jdbc, hasher, MAIL);
  }

  private <T> ResponseEntity<T> ruf(
      final String pfad,
      final HttpMethod methode,
      final @Nullable Object rumpf,
      final Class<T> typ) {
    return rest.exchange(pfad, methode, new HttpEntity<>(rumpf, sitzung), typ);
  }

  /** Ein geschuetzter Weg, auf dem aktuellen Stand der Zeile vorgelegt (Plan #415, E3). */
  private <T> ResponseEntity<T> rufAufStand(
      final long id,
      final String pfad,
      final HttpMethod methode,
      final @Nullable Object rumpf,
      final Class<T> typ) {
    return rest.exchange(
        pfad, methode, new HttpEntity<>(rumpf, Stand.aktuell(sitzung, jdbc, TABELLE, id)), typ);
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
    final HttpHeaders kopf = Stand.aktuell(sitzung, jdbc, TABELLE, id);
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
        rufAufStand(
            id,
            PFAD + "/" + id + "/zustand",
            HttpMethod.PUT,
            Map.of("zustand", "BEZAHLT"),
            NachtragResponse.class);
    final ResponseEntity<NachtragResponse> zurueck =
        rufAufStand(
            id,
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
        rufAufStand(
            id,
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
  void aendern_onAnOutdatedStand_thenConflictAndTheFirstStandStays() {
    // Given — #411, Kriterium 1: ein anderes Fenster hat inzwischen gespeichert
    final long id = nachgetragen("RE-2026-5");
    final HttpHeaders veraltet = Stand.aktuell(sitzung, jdbc, TABELLE, id);
    final NachtragResponse erster =
        Objects.requireNonNull(
            rufAufStand(
                    id,
                    PFAD + "/" + id,
                    HttpMethod.PUT,
                    rumpf("RE-2026-5a", "200.00", "238.00"),
                    NachtragResponse.class)
                .getBody());

    // When
    final ResponseEntity<String> zweiter =
        rest.exchange(
            PFAD + "/" + id,
            HttpMethod.PUT,
            new HttpEntity<>(rumpf("RE-2026-5b", "300.00", "357.00"), veraltet),
            String.class);

    // Then
    assertThat(erster.version()).isEqualTo(1L);
    assertThat(zweiter.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(zweiter.getBody()).contains("\"konflikt\"").contains("\"veraltet\"");
    assertThat(ruf(PFAD + "/" + id, HttpMethod.GET, null, NachtragResponse.class).getBody())
        .isEqualTo(erster);
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

  /** Loescht die Rechnung auf ihrem aktuellen Stand, so wie die Maske es tut. */
  private ResponseEntity<Void> loesche(final long id) {
    return rufAufStand(id, PFAD + "/" + id, HttpMethod.DELETE, null, Void.class);
  }

  private long gekennzeichnet(final long id) {
    return Objects.requireNonNull(
            jdbc.queryForObject(
                "SELECT count(*) FROM rechnung_nachgetragen"
                    + " WHERE id = ? AND geloescht_am IS NOT NULL AND geloescht_von IS NOT NULL",
                Long.class,
                Long.valueOf(id)))
        .longValue();
  }

  private long zeilen() {
    return Objects.requireNonNull(
            jdbc.queryForObject("SELECT count(*) FROM rechnung_nachgetragen", Long.class))
        .longValue();
  }

  /** Die Kennungen der nachgetragenen Rechnungen in der Liste. */
  private List<Long> nachgetrageneInDerListe() {
    return Objects.requireNonNull(
            ruf("/api/rechnungen", HttpMethod.GET, null, RechnungenUebersichtResponse.class)
                .getBody())
        .rechnungen()
        .stream()
        .filter(RechnungenUebersichtResponse.Zeile::nachgetragen)
        .map(RechnungenUebersichtResponse.Zeile::id)
        .toList();
  }

  /** Die Zahl der offenen Rechnungen auf der Startseite. */
  private int offeneRechnungen() {
    return Objects.requireNonNull(
            ruf("/api/startseite", HttpMethod.GET, null, StartseiteResponse.class).getBody())
        .offeneRechnungen()
        .anzahl();
  }

  /** Die Netto-Einnahmen des laufenden Jahres im Jahresabschluss. */
  private BigDecimal einnahmenImJahr() {
    return Objects.requireNonNull(
            ruf(
                    "/api/jahresabschluesse/" + heute.getYear(),
                    HttpMethod.GET,
                    null,
                    JahresabschlussResponse.class)
                .getBody())
        .einnahmen()
        .netto();
  }

  @Test
  void loeschen_thenTheRowStaysMarkedAndItsObjectIsGone() {
    // Given
    final long id = nachgetragen("RE-2026-7");
    ladeHoch(id, PDF, NachtragResponse.class);
    final String objekt = Objects.requireNonNull(schluessel(id));

    // When
    final ResponseEntity<Void> antwort = loesche(id);

    // Then — Einzelabruf und Download: fuer jeden Leseweg fort.
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(ruf(PFAD + "/" + id, HttpMethod.GET, null, String.class).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(dokument(id).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

    // Then — Plan #415, E17: die Zeile bleibt mit Urheber und Zeitpunkt, das Original geht nach
    // dem Commit.
    assertThat(gekennzeichnet(id)).isOne();
    assertThatThrownBy(() -> speicher.lies(objekt)).isInstanceOf(RuntimeException.class);
  }

  @Test
  void loeschen_onAnOutdatedStand_thenConflictAndNothingIsRemoved() {
    // Given — #411, Kriterium 6: geoeffnet auf Stand 0, inzwischen in einem anderen Fenster
    // geaendert.
    final long id = nachgetragen("RE-2026-10");
    final HttpHeaders veraltet = Stand.aktuell(sitzung, jdbc, TABELLE, id);
    ladeHoch(id, PDF, NachtragResponse.class);
    final String objekt = Objects.requireNonNull(schluessel(id));

    // When
    final ResponseEntity<String> antwort =
        rest.exchange(
            PFAD + "/" + id, HttpMethod.DELETE, new HttpEntity<>(null, veraltet), String.class);

    // Then — abgewiesen; Rechnung und Original stehen unveraendert.
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(antwort.getBody()).contains("\"art\":\"veraltet\"");
    assertThat(gekennzeichnet(id)).isZero();
    assertThat(dokument(id).getBody()).isEqualTo(PDF);
    assertThat(speicher.lies(objekt)).isEqualTo(PDF);
  }

  @Test
  void jederWeg_atADeletedRechnung_thenConflictAsGeloeschtAndNothingArises() {
    // Given — #411, Kriterium 6: die Rechnung ist inzwischen geloescht, in einem anderen Fenster.
    final long id = nachgetragen("RE-2026-11");
    loesche(id);
    final String pfad = PFAD + "/" + id;

    // When / Then — Aendern, Zustand, Original entfernen, Loeschen: 409 mit „gelöscht".
    assertThat(
            List.of(
                rufAufStand(
                    id, pfad, HttpMethod.PUT, rumpf("RE-2026-11a", "1.00", "1.00"), String.class),
                rufAufStand(
                    id,
                    pfad + "/zustand",
                    HttpMethod.PUT,
                    Map.of("zustand", "BEZAHLT"),
                    String.class),
                rufAufStand(id, pfad + "/dokument", HttpMethod.DELETE, null, String.class),
                rufAufStand(id, pfad, HttpMethod.DELETE, null, String.class),
                ladeHoch(id, PDF, String.class)))
        .allSatisfy(
            antwort -> {
              assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(antwort.getBody())
                  .contains("\"art\":\"gelöscht\"")
                  .contains("\"eigenesFenster\":true");
            });

    // Then — nichts ist neu entstanden, nichts liegt im Speicher.
    assertThat(zeilen()).isOne();
    assertThat(schluessel(id)).isNull();
    assertThat(
            jdbc.queryForObject(
                "SELECT nummer FROM rechnung_nachgetragen WHERE id = ?", String.class, id))
        .isEqualTo("RE-2026-11");
  }

  @Test
  void loeschen_thenTheRechnungLeavesTheListe() {
    // Given
    final long id = nachgetragen("RE-2026-12");
    assertThat(nachgetrageneInDerListe()).containsExactly(id);

    // When
    loesche(id);

    // Then
    assertThat(nachgetrageneInDerListe()).isEmpty();
  }

  @Test
  void loeschen_thenTheRechnungLeavesTheStartseite() {
    // Given — gestellt heisst offen; gezaehlt wird gegen den Stand ohne sie.
    final int vorher = offeneRechnungen();
    final long id = nachgetragen("RE-2026-13");
    assertThat(offeneRechnungen()).isEqualTo(vorher + 1);

    // When
    loesche(id);

    // Then
    assertThat(offeneRechnungen()).isEqualTo(vorher);
  }

  @Test
  void loeschen_thenTheRechnungLeavesTheJahresabschluss() {
    // Given — sie zaehlt in den Monat ihres Rechnungsdatums, im laufenden Jahr. Eine zweite steht
    // daneben: Ein Jahr ohne jede Rechnung hat keinen Abschluss.
    nachgetragen("RE-2026-14a");
    final BigDecimal vorher = einnahmenImJahr();
    final long id = nachgetragen("RE-2026-14");
    assertThat(einnahmenImJahr()).isEqualByComparingTo(vorher.add(new BigDecimal("1000.00")));

    // When
    loesche(id);

    // Then
    assertThat(einnahmenImJahr()).isEqualByComparingTo(vorher);
  }

  @Test
  void loeschen_thenItsNummerIsFreeAgain() {
    // Given
    final long geloescht = nachgetragen("RE-2026-15");
    final long andere = nachgetragen("RE-2026-16");
    loesche(geloescht);

    // When — nachtragen in anderer Schreibweise und aendern auf die Nummer der geloeschten
    final ResponseEntity<NachtragResponse> neu = angelegt("re-2026-15");
    loesche(Objects.requireNonNull(neu.getBody()).id());
    final ResponseEntity<NachtragResponse> geaendert =
        rufAufStand(
            andere,
            PFAD + "/" + andere,
            HttpMethod.PUT,
            rumpf("RE-2026-15", "1000.00", "1190.00"),
            NachtragResponse.class);

    // Then — existiertNummer und existiertNummerAusser lassen die geloeschte aus, der Index auch.
    assertThat(neu.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(geaendert.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(Objects.requireNonNull(geaendert.getBody()).nummer()).isEqualTo("RE-2026-15");
  }

  @Test
  void dokumentEntfernen_thenTheDownloadIsGoneAndTheRechnungStays() {
    // Given
    final long id = nachgetragen("RE-2026-8");
    ladeHoch(id, PDF, NachtragResponse.class);

    // When
    final ResponseEntity<NachtragResponse> antwort =
        rufAufStand(
            id, PFAD + "/" + id + "/dokument", HttpMethod.DELETE, null, NachtragResponse.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(Objects.requireNonNull(antwort.getBody()).dokument()).isFalse();
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
