package org.mwolff.fbcrm.rechnung;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.angebot.web.AngebotResponse;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.PasswordHasher;
import org.mwolff.fbcrm.auth.domain.Role;
import org.mwolff.fbcrm.rechnung.domain.Rechnungszustand;
import org.mwolff.fbcrm.rechnung.web.AngebotAbrechnungResponse;
import org.mwolff.fbcrm.rechnung.web.RechnungResponse;
import org.mwolff.fbcrm.rechnung.web.RechnungenUebersichtResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Der Rechnungsentwurf ueber HTTP, mit Sitzung und gegen eine echte PostgreSQL-Instanz (#160,
 * Kriterien 1 bis 12 und 26).
 *
 * <p>Hier steht die Teilabrechnung als Ganzes: Auf 160 angebotene Stunden entsteht ein Entwurf
 * ueber 160, wird auf 80 gesetzt, ein zweiter Entwurf nimmt die restlichen 80 und geht auf 100 —
 * danach ist nichts mehr offen und 20 sind zu viel. Nur hier pruefbar, weil dazu zwei Rechnungen im
 * echten Bestand stehen und einander sehen muessen; der Abrechnungsstand entsteht aus ihrer Summe
 * und nicht aus einer Spalte.
 *
 * <p>Dazu die Grenzen, die am echten Stand haengen: Ein Angebot vor „bestellt" und ein Angebot ohne
 * Offenes antworten 409 — <b>der Server entscheidet</b>, nicht die Auswahlliste. Eine
 * Preisaenderung am Angebot erreicht einen bestehenden Entwurf nicht (Kriterium 9, Frage 15).
 *
 * <p>Der Nachweis der Zugangsregel steht hier und nicht nur in {@code AccessRuleIT}: Jeder neue
 * Pfad unter {@code /api} ist ohne Sitzung verschlossen, und das gehoert zu jedem neuen Weg dazu.
 */
class RechnungEntwurfIT extends AbstractIntegrationTest {

  private static final String MAIL = "manne@example.org";
  private static final String PASSWORT = "richtiges-passwort";
  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");
  private static final String STUNDENSATZ = "100.00";

  private final TestRestTemplate rest;
  private final AccountRepository accounts;
  private final PasswordHasher hasher;
  private final JdbcTemplate jdbc;

  private HttpHeaders sitzung = new HttpHeaders();
  private long firmaId;

  @Autowired
  RechnungEntwurfIT(
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
        "TRUNCATE rechnung_position, rechnung, angebot_position, angebot, ansprechpartner, firma"
            + " RESTART IDENTITY CASCADE");
    jdbc.execute("DELETE FROM rechnung_einstellungen");
    jdbc.execute("INSERT INTO rechnung_einstellungen DEFAULT VALUES");
    jdbc.update("INSERT INTO firma (name) VALUES (?)", "Adler AG");
    firmaId =
        Objects.requireNonNull(
                jdbc.queryForObject("SELECT id FROM firma WHERE name = ?", Long.class, "Adler AG"))
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

  /** Ein Angebot mit einer Position ueber die angegebene Menge Beratung, im Status ANGELEGT. */
  private long angebotMit(final String menge, final String einzelpreis) {
    final long angebotId =
        Objects.requireNonNull(
                ruf(
                        "/api/firmen/" + firmaId + "/angebote",
                        HttpMethod.POST,
                        null,
                        AngebotResponse.class)
                    .getBody())
            .id();
    positionSetzen(angebotId, null, menge, einzelpreis);
    return angebotId;
  }

  /*
   * Mit Kennung heisst „schreib diese Zeile fort" (Plan #169, E2); ohne legt der Anwendungsfall
   * eine neue an. Fuer die Preisaenderung an einem Angebot, dessen Position schon in einer Rechnung
   * steht, muss die Kennung mit: Eine neue Zeile liesse die Rechnungsposition ins Leere zeigen.
   */
  private void positionSetzen(
      final long angebotId,
      final @Nullable Long positionId,
      final String menge,
      final String einzelpreis) {
    final Map<String, Object> position = new LinkedHashMap<>();
    position.put("id", positionId);
    position.put("bezeichnung", "Beratung");
    position.put("abrechnungsmodus", "AUFWAND");
    position.put("menge", menge);
    position.put("einheit", "STUNDE");
    position.put("einzelpreis", einzelpreis);
    final Map<String, Object> rumpf = new LinkedHashMap<>();
    rumpf.put("angebotDatum", "2026-09-25");
    rumpf.put("ansprechpartnerId", null);
    rumpf.put("beschreibung", "Neugestaltung der Website");
    rumpf.put("positionen", List.of(position));
    ruf("/api/angebote/" + angebotId, HttpMethod.PUT, rumpf, AngebotResponse.class);
  }

  /** Dasselbe Angebot, zweimal weitergeschaltet: von ANGELEGT ueber ABGEGEBEN auf BESTELLT. */
  private long bestelltesAngebot(final String menge) {
    final long angebotId = angebotMit(menge, STUNDENSATZ);
    ruf("/api/angebote/" + angebotId + "/status/weiter", HttpMethod.POST, null, String.class);
    ruf("/api/angebote/" + angebotId + "/status/weiter", HttpMethod.POST, null, String.class);
    return angebotId;
  }

  private RechnungResponse entwurfZu(final long angebotId) {
    final ResponseEntity<RechnungResponse> antwort =
        ruf(
            "/api/angebote/" + angebotId + "/rechnungen",
            HttpMethod.POST,
            null,
            RechnungResponse.class);
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return Objects.requireNonNull(antwort.getBody());
  }

  private ResponseEntity<RechnungResponse> setzeMenge(
      final RechnungResponse entwurf, final String menge) {
    final Map<String, Object> position = new LinkedHashMap<>();
    position.put(
        "angebotPositionId", Long.valueOf(entwurf.zeilen().getFirst().angebotPositionId()));
    position.put("bezeichnung", "Beratung");
    position.put("menge", menge);
    final Map<String, Object> rumpf = new LinkedHashMap<>();
    rumpf.put("rechnungDatum", "2026-09-30");
    rumpf.put("leistungszeitraum", "September 2026");
    rumpf.put("positionen", List.of(position));
    return ruf("/api/rechnungen/" + entwurf.id(), HttpMethod.PUT, rumpf, RechnungResponse.class);
  }

  private AngebotAbrechnungResponse abrechnung(final long angebotId) {
    return Objects.requireNonNull(
        ruf(
                "/api/angebote/" + angebotId + "/abrechnung",
                HttpMethod.GET,
                null,
                AngebotAbrechnungResponse.class)
            .getBody());
  }

  @Test
  void teilabrechnung_thenTheOffenenMengenFollowEveryRechnung() {
    // Given — 160 angebotene Stunden, der erste Entwurf ist damit vorbelegt.
    final long angebotId = bestelltesAngebot("160.00");
    final RechnungResponse erster = entwurfZu(angebotId);
    assertThat(erster.zeilen().getFirst().menge()).isEqualByComparingTo("160.00");
    assertThat(erster.zustand()).isEqualTo(Rechnungszustand.ENTWURF);

    // When — der erste Entwurf rechnet nur 80 ab.
    assertThat(setzeMenge(erster, "80.00").getStatusCode()).isEqualTo(HttpStatus.OK);

    // Then — 80 abgerechnet, 80 offen.
    assertThat(abrechnung(angebotId).positionen().getFirst())
        .satisfies(
            zeile -> assertThat(zeile.abgerechnet()).isEqualByComparingTo("80.00"),
            zeile -> assertThat(zeile.offen()).isEqualByComparingTo("80.00"),
            zeile -> assertThat(zeile.ueberschreitung()).isEqualByComparingTo("0"));

    // When — der zweite Entwurf nimmt den Rest und geht darueber hinaus.
    final RechnungResponse zweiter = entwurfZu(angebotId);
    assertThat(zweiter.zeilen().getFirst().menge()).isEqualByComparingTo("80.00");
    assertThat(setzeMenge(zweiter, "100.00").getStatusCode()).isEqualTo(HttpStatus.OK);

    // Then — nichts mehr offen, 20 zu viel; beide Rechnungen stehen am Angebot.
    final AngebotAbrechnungResponse danach = abrechnung(angebotId);
    assertThat(danach.positionen().getFirst())
        .satisfies(
            zeile -> assertThat(zeile.abgerechnet()).isEqualByComparingTo("180.00"),
            zeile -> assertThat(zeile.offen()).isEqualByComparingTo("0"),
            zeile -> assertThat(zeile.ueberschreitung()).isEqualByComparingTo("20.00"));
    assertThat(danach.rechnungen()).hasSize(2);
  }

  @Test
  void aendern_withAMengeOfZero_thenTheRechnungCarriesNoPosition() {
    // Given — eine Position ueber nichts ist keine Position (Kriterium 5).
    final long angebotId = bestelltesAngebot("160.00");
    final RechnungResponse entwurf = entwurfZu(angebotId);

    // When
    final RechnungResponse geaendert = Objects.requireNonNull(setzeMenge(entwurf, "0").getBody());

    // Then — die Zeile der Maske bleibt, die Rechnungsposition verschwindet.
    assertThat(geaendert.zeilen()).hasSize(1);
    assertThat(geaendert.zeilen().getFirst().menge()).isEqualByComparingTo("0");
    assertThat(geaendert.netto()).isEqualByComparingTo("0");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM rechnung_position WHERE rechnung_id = ?",
                Long.class,
                Long.valueOf(entwurf.id())))
        .isZero();
  }

  @Test
  void loeschen_thenTheMengenAreOffenAgain() {
    // Given — ein Entwurf ueber die vollen 160 Stunden.
    final long angebotId = bestelltesAngebot("160.00");
    final RechnungResponse entwurf = entwurfZu(angebotId);
    assertThat(abrechnung(angebotId).positionen().getFirst().offen()).isEqualByComparingTo("0");

    // When
    final ResponseEntity<Void> antwort =
        ruf("/api/rechnungen/" + entwurf.id(), HttpMethod.DELETE, null, Void.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(abrechnung(angebotId).positionen().getFirst().offen())
        .isEqualByComparingTo("160.00");
    assertThat(abrechnung(angebotId).rechnungen()).isEmpty();
  }

  @Test
  void anlegen_atAnAngebotInAngelegt_thenConflict() {
    // Given — vor „bestellt" gibt es nichts abzurechnen (Kriterium 2).
    final long angebotId = angebotMit("160.00", STUNDENSATZ);

    // When
    final ResponseEntity<String> antwort =
        ruf("/api/angebote/" + angebotId + "/rechnungen", HttpMethod.POST, null, String.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void anlegen_atAnAngebotWithoutOffenes_thenConflict() {
    // Given — der erste Entwurf traegt bereits die vollen 160 Stunden.
    final long angebotId = bestelltesAngebot("160.00");
    entwurfZu(angebotId);

    // When
    final ResponseEntity<String> antwort =
        ruf("/api/angebote/" + angebotId + "/rechnungen", HttpMethod.POST, null, String.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void anlegen_atAnUnknownAngebot_thenNotFound() {
    // When
    final ResponseEntity<String> antwort =
        ruf("/api/angebote/999999/rechnungen", HttpMethod.POST, null, String.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void aendern_afterAPriceChangeAtTheAngebot_thenTheEntwurfKeepsItsEinzelpreis() {
    // Given — der Entwurf haelt 100,00 fest, danach steigt der Preis am Angebot auf 120,00.
    final long angebotId = bestelltesAngebot("160.00");
    final RechnungResponse entwurf = entwurfZu(angebotId);
    positionSetzen(
        angebotId,
        Long.valueOf(entwurf.zeilen().getFirst().angebotPositionId()),
        "160.00",
        "120.00");

    // When
    final RechnungResponse geaendert =
        Objects.requireNonNull(setzeMenge(entwurf, "80.00").getBody());

    // Then — 80 mal 100,00 und nicht mal 120,00 (Kriterium 9).
    assertThat(geaendert.zeilen().getFirst().einzelpreis()).isEqualByComparingTo("100.00");
    assertThat(geaendert.netto()).isEqualByComparingTo("8000.00");
  }

  @Test
  void lesen_withAnUnknownRechnung_thenNotFound() {
    // When
    final ResponseEntity<String> antwort =
        ruf("/api/rechnungen/999999", HttpMethod.GET, null, String.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void rechnungen_thenTheEntwurfHasNoNummerAndABruttoFromTheEinstellungen() {
    // Given — 80 Stunden zu 100,00 €, 19 Prozent aus der Vorbelegung der Migration.
    final long angebotId = bestelltesAngebot("160.00");
    setzeMenge(entwurfZu(angebotId), "80.00");

    // When
    final RechnungenUebersichtResponse liste =
        Objects.requireNonNull(
            ruf("/api/rechnungen", HttpMethod.GET, null, RechnungenUebersichtResponse.class)
                .getBody());

    // Then
    assertThat(liste.rechnungen())
        .singleElement()
        .satisfies(
            zeile -> assertThat(zeile.nummer()).isNull(),
            zeile -> assertThat(zeile.zustand()).isEqualTo(Rechnungszustand.ENTWURF),
            zeile -> assertThat(zeile.firmaId()).isEqualTo(firmaId),
            zeile -> assertThat(zeile.firmaName()).isEqualTo("Adler AG"),
            zeile -> assertThat(zeile.brutto()).isEqualByComparingTo("9520.00"));
  }

  @Test
  void abrechenbareAngebote_thenOnlyTheAngebotWithSomethingOffen() {
    // Given — ein bestelltes Angebot mit Offenem und eines, dessen Rest im Entwurf steht.
    final long offenes = bestelltesAngebot("160.00");
    final long erledigtes = bestelltesAngebot("10.00");
    entwurfZu(erledigtes);

    // When
    final String antwort =
        ruf("/api/rechnungen/abrechenbare-angebote", HttpMethod.GET, null, String.class).getBody();

    // Then
    assertThat(antwort)
        .contains("\"angebotId\":" + offenes)
        .doesNotContain("\"angebotId\":" + erledigtes);
  }

  @Test
  void jederWeg_withoutASitzung_thenUnauthorized() {
    // Given — dieselben Wege, nur ohne das Sitzungs-Cookie.
    sitzung = new HttpHeaders();

    // When / Then
    assertThat(ruf("/api/rechnungen", HttpMethod.GET, null, String.class).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(
            ruf("/api/rechnungen/abrechenbare-angebote", HttpMethod.GET, null, String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(ruf("/api/rechnungen/1", HttpMethod.GET, null, String.class).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(ruf("/api/rechnungen/1", HttpMethod.PUT, Map.of(), String.class).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(ruf("/api/rechnungen/1", HttpMethod.DELETE, null, String.class).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(
            ruf("/api/angebote/1/rechnungen", HttpMethod.POST, null, String.class).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(
            ruf("/api/angebote/1/abrechnung", HttpMethod.GET, null, String.class).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
