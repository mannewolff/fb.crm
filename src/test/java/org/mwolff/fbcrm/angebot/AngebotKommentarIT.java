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
import org.mwolff.fbcrm.auth.Anmeldung;
import org.mwolff.fbcrm.auth.Konten;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.SecretHasher;
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
 * (Kriterium 4), ein {@code DELETE} nimmt den Kommentar aus der Liste (Kriterium 10), und ein
 * {@code PUT} laesst {@code createdAt} stehen (Kriterium 9). Dazu die Grenzen: 401 ohne Sitzung,
 * 404 bei unbekanntem Angebot und bei einem Kommentar eines anderen Angebots (E5), und 201 auch an
 * einem Angebot im Status ABGERECHNET (Kriterium 8).
 *
 * <p>Dazu der Schutz gegen gleichzeitiges Bearbeiten (#411, Kriterien 1, 6 und 7; Plan #415, E8,
 * E16, E17): Aendern und Loeschen legen den Stand des Kommentars vor, ein alter Stand und ein
 * geloeschter Kommentar werden mit 409 abgewiesen, und es entsteht nichts neu. Ein neuer Kommentar
 * laesst die Version des Angebots stehen. Geloescht wird mit Kennzeichen an der Zeile.
 *
 * <p>Dazu der Verfasser (Issue #420; Plan #414, A7; fachliche Quelle #409, Kriterium 11): Er kommt
 * aus der Sitzung, nicht aus dem Rumpf, und die Liste traegt seinen aktuellen Namen — auch nach
 * einer Umbenennung des Kontos.
 */
class AngebotKommentarIT extends AbstractIntegrationTest {

  private static final String MAIL = "manne@example.org";
  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");
  private static final String FIRMA = "Adler AG";
  private static final String KOMMENTARE = "angebot_kommentar";

  private final TestRestTemplate rest;
  private final AccountRepository accounts;
  private final SecretHasher hasher;
  private final JdbcTemplate jdbc;

  private HttpHeaders sitzung = new HttpHeaders();
  private long firmaId;
  private long kontoId;

  @Autowired
  AngebotKommentarIT(
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
  void leereDenBestandUndMeldeAn() {
    jdbc.execute(
        "TRUNCATE angebot_kommentar, angebot_position, angebot, ansprechpartner, firma"
            + " RESTART IDENTITY CASCADE");
    jdbc.update("INSERT INTO firma (name) VALUES (?)", FIRMA);
    firmaId =
        Objects.requireNonNull(
                jdbc.queryForObject("SELECT id FROM firma WHERE name = ?", Long.class, FIRMA))
            .longValue();
    leereKonten(jdbc);
    kontoId = accounts.save(Konten.neu(MAIL, ANGELEGT)).requireId();
    sitzung = Anmeldung.perCode(rest, jdbc, hasher, MAIL);
  }

  private <T> ResponseEntity<T> ruf(
      final String pfad, final HttpMethod methode, final Object rumpf, final Class<T> typ) {
    return rest.exchange(pfad, methode, new HttpEntity<>(rumpf, sitzung), typ);
  }

  /** Der Stand, den die Zeile gerade traegt — der, den die Ansicht beim Oeffnen sah. */
  private long version(final String tabelle, final long id) {
    return Objects.requireNonNull(
            jdbc.queryForObject(
                "SELECT version FROM " + tabelle + " WHERE id = ?", Long.class, Long.valueOf(id)))
        .longValue();
  }

  /** Ein Aufruf auf dem genannten Stand, vorgelegt als {@code If-Match}. */
  private <T> ResponseEntity<T> aufStand(
      final long version,
      final String pfad,
      final HttpMethod methode,
      final Object rumpf,
      final Class<T> typ) {
    final HttpHeaders kopf = new HttpHeaders();
    kopf.putAll(sitzung);
    kopf.setIfMatch("\"" + version + "\"");
    return rest.exchange(pfad, methode, new HttpEntity<>(rumpf, kopf), typ);
  }

  private long angebot() {
    return Objects.requireNonNull(
            ruf(
                    "/api/firmen/" + firmaId + "/angebote",
                    HttpMethod.POST,
                    Map.of("titel", "Website-Relaunch"),
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
        aufStand(
            version(KOMMENTARE, kommentarId),
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
  void loeschen_thenTheKommentarIsGoneFromTheListAndMarkedInTheTable() {
    // Given — Kriterium 10; Plan #415, E17: die Zeile bleibt mit Zeitpunkt und Konto der Loeschung.
    final long angebotId = angebot();
    final long kommentarId =
        Objects.requireNonNull(schreibe(angebotId, "Weg damit").getBody()).id();

    // When
    final ResponseEntity<Void> antwort =
        aufStand(
            version(KOMMENTARE, kommentarId),
            pfad(angebotId) + "/" + kommentarId,
            HttpMethod.DELETE,
            null,
            Void.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(liste(angebotId)).isEmpty();
    assertThat(
            jdbc.queryForMap(
                "SELECT geloescht_von, geloescht_am IS NOT NULL AS gekennzeichnet"
                    + " FROM angebot_kommentar WHERE id = ?",
                Long.valueOf(kommentarId)))
        .containsEntry("geloescht_von", Long.valueOf(kontoId))
        .containsEntry("gekennzeichnet", true);
  }

  @Test
  void aendern_thenCarriesTheNewTextAndTheOldCreatedAt() {
    // Given — Kriterium 9: der Zeitpunkt bleibt der der Anlage.
    final long angebotId = angebot();
    final AngebotKommentarResponse angelegt =
        Objects.requireNonNull(schreibe(angebotId, "Erster Stand").getBody());

    // When
    final ResponseEntity<AngebotKommentarResponse> antwort =
        aufStand(
            angelegt.version(),
            pfad(angebotId) + "/" + angelegt.id(),
            HttpMethod.PUT,
            Map.of("text", "Zweiter Stand"),
            AngebotKommentarResponse.class);

    // Then — die Antwort traegt den erhoehten Stand, den die Ansicht beim naechsten Mal vorlegt.
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
    final AngebotKommentarResponse geaendert = Objects.requireNonNull(antwort.getBody());
    assertThat(geaendert.text()).isEqualTo("Zweiter Stand");
    assertThat(geaendert.createdAt()).isEqualTo(angelegt.createdAt());
    assertThat(geaendert.version()).isEqualTo(angelegt.version() + 1);
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

  private long verfasserIdIn(final long kommentarId) {
    return Objects.requireNonNull(
            jdbc.queryForObject(
                "SELECT verfasser_id FROM angebot_kommentar WHERE id = ?",
                Long.class,
                Long.valueOf(kommentarId)))
        .longValue();
  }

  @Test
  void schreiben_thenTheVerfasserIsTheAccountOfTheSession() {
    // Given — A7: der Verfasser kommt aus der Sitzung.
    final long angebotId = angebot();

    // When
    final AngebotKommentarResponse geschrieben =
        Objects.requireNonNull(schreibe(angebotId, "Bitte melden.").getBody());

    // Then
    assertThat(geschrieben.verfasser()).isEqualTo("Manne");
    assertThat(verfasserIdIn(geschrieben.id())).isEqualTo(kontoId);
  }

  @Test
  void schreiben_withAnotherAccountInTheBody_thenTheVerfasserIsStillTheAccountOfTheSession() {
    // Given — wer im Rumpf einen anderen nennt, schreibt trotzdem unter eigenem Namen.
    final long angebotId = angebot();
    final Long fremdesKonto =
        jdbc.queryForObject(
            "INSERT INTO account (email, display_name, role)"
                + " VALUES ('bert@example.org', 'Bert', 'EMPLOYEE') RETURNING id",
            Long.class);

    // When
    final ResponseEntity<AngebotKommentarResponse> antwort =
        ruf(
            pfad(angebotId),
            HttpMethod.POST,
            Map.of("text", "Bitte melden.", "verfasserId", fremdesKonto, "verfasser", "Bert"),
            AngebotKommentarResponse.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    final AngebotKommentarResponse geschrieben = Objects.requireNonNull(antwort.getBody());
    assertThat(geschrieben.verfasser()).isEqualTo("Manne");
    assertThat(verfasserIdIn(geschrieben.id())).isEqualTo(kontoId);
  }

  @Test
  void liste_afterTheAccountWasRenamed_thenCarriesTheCurrentName() {
    // Given — #409, Kriterium 11: auch die bisherigen Kommentare zeigen den neuen Namen.
    final long angebotId = angebot();
    schreibe(angebotId, "Erster");
    schreibe(angebotId, "Zweiter");
    jdbc.update(
        "UPDATE account SET display_name = 'Manfred Wolff' WHERE id = ?", Long.valueOf(kontoId));

    // When
    final List<AngebotKommentarResponse> kommentare = liste(angebotId);

    // Then
    assertThat(kommentare)
        .extracting(AngebotKommentarResponse::verfasser)
        .containsExactly("Manfred Wolff", "Manfred Wolff");
  }

  @Test
  void liste_afterTheAccountWasRetired_thenStillCarriesItsName() {
    // Given — #409, Kriterium 11: die Zuordnung bleibt auch nach dem Stilllegen.
    final long angebotId = angebot();
    schreibe(angebotId, "Vor dem Abschied");
    jdbc.update(
        "INSERT INTO account (email, display_name, role)"
            + " VALUES ('chefin@example.org', 'Chefin', 'C_LEVEL')");
    sitzung = Anmeldung.perCode(rest, jdbc, hasher, "chefin@example.org");
    jdbc.update("UPDATE account SET aktiv = false WHERE id = ?", Long.valueOf(kontoId));

    // When
    final List<AngebotKommentarResponse> kommentare = liste(angebotId);

    // Then
    assertThat(kommentare).extracting(AngebotKommentarResponse::verfasser).containsExactly("Manne");
  }

  /** Meldet ein zweites Konto an; die Sitzung des ersten bleibt in {@code mannesSitzung}. */
  private HttpHeaders bensSitzung() {
    jdbc.update(
        "INSERT INTO account (email, display_name, role)"
            + " VALUES ('ben@example.org', 'Ben', 'EMPLOYEE')");
    return Anmeldung.perCode(rest, jdbc, hasher, "ben@example.org");
  }

  @Test
  void schreiben_whileAnotherHasTheAngebotOpen_thenTheAngebotKeepsItsVersionAndSavesLater() {
    // Given — #411, Szenario „Kommentar trotz geöffnetem Angebot"; Plan #415, E8: Manne hat das
    // Angebot auf seinem Stand geoeffnet.
    final long angebotId = angebot();
    final long geoeffnet = version("angebot", angebotId);
    final HttpHeaders mannesSitzung = sitzung;
    sitzung = bensSitzung();

    // When — Ben schreibt zum selben Angebot einen Kommentar.
    final ResponseEntity<AngebotKommentarResponse> kommentar =
        schreibe(angebotId, "Kunde hat Zeit.");

    // Then — sofort gespeichert, und die Version des Angebots steht unveraendert.
    assertThat(kommentar.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(version("angebot", angebotId)).isEqualTo(geoeffnet);

    // And — Mannes Speichern auf dem beim Oeffnen gelesenen Stand geht danach durch.
    sitzung = mannesSitzung;
    final ResponseEntity<String> gespeichert =
        aufStand(
            geoeffnet,
            "/api/angebote/" + angebotId,
            HttpMethod.PUT,
            Map.of(
                "titel", "Website-Relaunch", "angebotDatum", "2026-09-25", "positionen", List.of()),
            String.class);
    assertThat(gespeichert.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void aendern_withoutIfMatch_thenAnswers428AndChangesNothing() {
    // Given — E3: ohne vorgelegten Stand wird nicht geschrieben.
    final long angebotId = angebot();
    final long kommentarId =
        Objects.requireNonNull(schreibe(angebotId, "Erster Stand").getBody()).id();

    // When
    final ResponseEntity<String> antwort =
        ruf(
            pfad(angebotId) + "/" + kommentarId,
            HttpMethod.PUT,
            Map.of("text", "Neu"),
            String.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.PRECONDITION_REQUIRED);
    assertThat(liste(angebotId))
        .extracting(AngebotKommentarResponse::text)
        .containsExactly("Erster Stand");
  }

  @Test
  void aendern_onAnOutdatedStand_thenAnswers409AndKeepsTheOtherText() {
    // Given — Kriterium 1: Ben und Manne haben denselben Kommentar offen; Ben speichert zuerst.
    final long angebotId = angebot();
    final AngebotKommentarResponse geoeffnet =
        Objects.requireNonNull(schreibe(angebotId, "Erster Stand").getBody());
    final HttpHeaders mannesSitzung = sitzung;
    sitzung = bensSitzung();
    final String einzeln = pfad(angebotId) + "/" + geoeffnet.id();
    aufStand(
        geoeffnet.version(), einzeln, HttpMethod.PUT, Map.of("text", "Bens Stand"), String.class);
    sitzung = mannesSitzung;

    // When — Manne speichert auf dem Stand, den er beim Oeffnen sah.
    final ResponseEntity<String> antwort =
        aufStand(
            geoeffnet.version(),
            einzeln,
            HttpMethod.PUT,
            Map.of("text", "Mannes Stand"),
            String.class);

    // Then — abgewiesen mit Bens Namen; Bens Stand bleibt erhalten.
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(antwort.getBody())
        .contains("\"art\":\"veraltet\"")
        .contains("\"geaendertVon\":\"Ben\"");
    assertThat(liste(angebotId))
        .extracting(AngebotKommentarResponse::text)
        .containsExactly("Bens Stand");
  }

  @Test
  void loeschen_onAnOutdatedStand_thenAnswers409AndDeletesNothing() {
    // Given — Kriterium 6: ein inzwischen geaenderter Kommentar wird nicht geloescht.
    final long angebotId = angebot();
    final AngebotKommentarResponse geoeffnet =
        Objects.requireNonNull(schreibe(angebotId, "Erster Stand").getBody());
    final String einzeln = pfad(angebotId) + "/" + geoeffnet.id();
    aufStand(
        geoeffnet.version(), einzeln, HttpMethod.PUT, Map.of("text", "Neuer Stand"), String.class);

    // When
    final ResponseEntity<String> antwort =
        aufStand(geoeffnet.version(), einzeln, HttpMethod.DELETE, null, String.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(liste(angebotId))
        .extracting(AngebotKommentarResponse::text)
        .containsExactly("Neuer Stand");
  }

  @Test
  void aendern_aDeletedKommentar_thenAnswers409AsDeletedByTheOtherAndCreatesNothing() {
    // Given — Kriterium 6, E16: Ben hat den Kommentar geloescht, waehrend Manne ihn offen hatte.
    final long angebotId = angebot();
    final AngebotKommentarResponse geoeffnet =
        Objects.requireNonNull(schreibe(angebotId, "Erster Stand").getBody());
    final String einzeln = pfad(angebotId) + "/" + geoeffnet.id();
    final HttpHeaders mannesSitzung = sitzung;
    sitzung = bensSitzung();
    aufStand(geoeffnet.version(), einzeln, HttpMethod.DELETE, null, Void.class);
    sitzung = mannesSitzung;

    // When — Manne speichert, und er loescht ein zweites Mal.
    final ResponseEntity<String> speichern =
        aufStand(
            geoeffnet.version(),
            einzeln,
            HttpMethod.PUT,
            Map.of("text", "Mannes Stand"),
            String.class);
    final ResponseEntity<String> loeschen =
        aufStand(geoeffnet.version(), einzeln, HttpMethod.DELETE, null, String.class);

    // Then — beide abgewiesen, mit Bens Namen; kein Kommentar entsteht neu.
    assertThat(speichern.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(speichern.getBody())
        .contains("\"art\":\"gelöscht\"")
        .contains("\"geaendertVon\":\"Ben\"")
        .contains("\"eigenesFenster\":false");
    assertThat(loeschen.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(liste(angebotId)).isEmpty();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM angebot_kommentar", Long.class))
        .isEqualTo(Long.valueOf(1L));
    assertThat(
            jdbc.queryForObject(
                "SELECT text FROM angebot_kommentar WHERE id = ?",
                String.class,
                Long.valueOf(geoeffnet.id())))
        .isEqualTo("Erster Stand");
  }
}
