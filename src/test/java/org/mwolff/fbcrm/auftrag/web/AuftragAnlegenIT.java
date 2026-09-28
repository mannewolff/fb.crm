package org.mwolff.fbcrm.auftrag.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.angebot.domain.Angebotszustand;
import org.mwolff.fbcrm.angebot.domain.Belegabsender;
import org.mwolff.fbcrm.angebot.domain.Belegempfaenger;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.PasswordHasher;
import org.mwolff.fbcrm.auth.domain.Role;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.common.Einheit;
import org.mwolff.fbcrm.common.Geschaeftszone;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Der Weg vom angenommenen Angebot zum Auftrag ueber HTTP, mit Sitzung und gegen eine echte
 * PostgreSQL-Instanz.
 *
 * <p>Drei Zusagen haengen an der echten Transaktion und am echten Schema und sind darum nur hier
 * pruefbar. Erstens der Nummernkreis: Die erste Nummer des Jahres endet auf 001, die zweite auf
 * 002, und das Jahr ist das des <b>Anlegens</b> in {@code common.Geschaeftszone}. Zweitens die
 * Uebernahme aus Plan E7: Die Preise der Antwort sind die des Angebots, und der Rumpf traegt gar
 * kein Preisfeld — ein mitgeschicktes wird nicht gelesen, und genau das steht hier am Vertrag.
 * Drittens die Ereigniszeile: Sie entsteht in derselben Transaktion wie der Auftrag (Kriterium 8).
 *
 * <p>Der Nachweis der Zugangsregel steht hier und nicht nur in {@code AccessRuleIT}: Ein neuer Pfad
 * unter {@code /api} ist ohne Sitzung verschlossen, und das gehoert zu jedem neuen Weg dazu.
 */
class AuftragAnlegenIT extends AbstractIntegrationTest {

  private static final String MAIL = "manne@example.org";
  private static final String PASSWORT = "richtiges-passwort";
  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");
  private static final Instant VERSANDZEITPUNKT = Instant.parse("2026-09-21T09:00:00Z");
  private static final Instant REAKTION = Instant.parse("2026-09-22T11:00:00Z");
  private static final LocalDate ANGEBOTSDATUM = LocalDate.of(2026, 9, 20);
  private static final LocalDate GUELTIG_BIS = LocalDate.of(2026, 10, 20);
  private static final String PDF_SCHLUESSEL = "angebot/1/6f1c9a.pdf";

  private static final Belegempfaenger EMPFAENGER =
      new Belegempfaenger(
          "Adler AG", new Anschrift("Hauptstrasse 1", "28195", "Bremen", "Deutschland"), null);

  private static final Belegabsender ABSENDER =
      new Belegabsender(
          "Manfred Wolff",
          new Anschrift("Am Deich 2", "28199", "Hansestadt", "Bundesrepublik"),
          null,
          null,
          null,
          null,
          null);

  private static final Angebotsposition KONZEPTION =
      new Angebotsposition(
          "Konzeption",
          Abrechnungsmodus.AUFWAND,
          new BigDecimal("2.50"),
          Einheit.PERSONENTAG,
          new BigDecimal("1000.01"));

  private static final Angebotsposition SCHULUNG =
      new Angebotsposition(
          "Schulungstag",
          Abrechnungsmodus.FESTPREIS,
          new BigDecimal("1.00"),
          Einheit.PAUSCHAL,
          new BigDecimal("1200.00"));

  private final TestRestTemplate rest;
  private final AngebotRepository angebote;
  private final AccountRepository accounts;
  private final PasswordHasher hasher;
  private final TransactionTemplate transaktion;
  private final JdbcTemplate jdbc;
  private final Clock clock;

  private HttpHeaders sitzung = new HttpHeaders();
  private long vorgangId;
  private long angebotId;
  private long zweitesAngebotId;

  @Autowired
  AuftragAnlegenIT(
      final TestRestTemplate rest,
      final AngebotRepository angebote,
      final AccountRepository accounts,
      final PasswordHasher hasher,
      final TransactionTemplate transaktion,
      final JdbcTemplate jdbc,
      final Clock clock) {
    this.rest = rest;
    this.angebote = angebote;
    this.accounts = accounts;
    this.hasher = hasher;
    this.transaktion = transaktion;
    this.jdbc = jdbc;
    this.clock = clock;
  }

  @BeforeEach
  void leereDenBestandUndMeldeAn() {
    jdbc.execute(
        "TRUNCATE auftrag_position, auftrag, angebot_position, angebot, vorgang_eintrag, vorgang,"
            + " ansprechpartner, firma RESTART IDENTITY CASCADE");
    jdbc.execute("DELETE FROM auftrag_nummernkreis");
    jdbc.update("INSERT INTO firma (name) VALUES ('Adler AG')");
    final long firmaId =
        Objects.requireNonNull(
                jdbc.queryForObject("SELECT id FROM firma WHERE name = 'Adler AG'", Long.class))
            .longValue();
    vorgangId = vorgang(1L, firmaId);
    angebotId = angenommenesAngebot(vorgangId, "A-2026-001");
    zweitesAngebotId = angenommenesAngebot(vorgangId, "A-2026-002");
    jdbc.execute("TRUNCATE outbox_message, password_reset_token, account RESTART IDENTITY CASCADE");
    accounts.save(
        new Account(null, MAIL, "Manne", hasher.hash(PASSWORT), Role.ADMIN, 0, ANGELEGT, ANGELEGT));
    sitzung = angemeldeterKopf();
  }

  private long vorgang(final long nummer, final long firmaId) {
    jdbc.update(
        "INSERT INTO vorgang (nummer, titel, firma_id, abgeschlossen) VALUES (?, ?, ?, false)",
        Long.valueOf(nummer),
        "Website-Relaunch",
        Long.valueOf(firmaId));
    return Objects.requireNonNull(
            jdbc.queryForObject(
                "SELECT id FROM vorgang WHERE nummer = ?", Long.class, Long.valueOf(nummer)))
        .longValue();
  }

  private long angenommenesAngebot(final long vorgang, final String nummer) {
    final Angebot entwurf =
        new Angebot(
            null,
            vorgang,
            null,
            Angebotszustand.ENTWURF,
            ANGEBOTSDATUM,
            GUELTIG_BIS,
            "Neugestaltung der Website",
            "Zahlbar innerhalb von 14 Tagen ohne Abzug.",
            null,
            null,
            null,
            null,
            null,
            List.of(KONZEPTION, SCHULUNG),
            ANGELEGT,
            ANGELEGT);
    return transaktion
        .execute(
            status ->
                angebote.save(
                    entwurf
                        .versendet(nummer, EMPFAENGER, ABSENDER, PDF_SCHLUESSEL, VERSANDZEITPUNKT)
                        .angenommen(REAKTION)))
        .requireId();
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

  /*
   * Der Rumpf des Anlegens — mit einem Preisfeld, das es im Vertrag nicht gibt. Genau das ist der
   * Gegenstand: Was der Absender nicht aendern darf, kommt gar nicht erst im Rumpf vor (Plan E7),
   * und ein trotzdem mitgeschickter Preis wird nicht gelesen.
   */
  private static Map<String, Object> rumpfMitUntergeschobenemPreis() {
    final Map<String, Object> position = new LinkedHashMap<>();
    position.put("platz", Integer.valueOf(1));
    position.put("menge", "2.50");
    position.put("stundenJePersonentag", "7.50");
    position.put("einzelpreis", "1.00");
    final Map<String, Object> felder = new LinkedHashMap<>();
    felder.put("kundenbestellnummer", "BST-4711");
    felder.put("positionen", List.of(position));
    return felder;
  }

  private static Map<String, Object> rumpf(final int platz, final String menge) {
    final Map<String, Object> position = new LinkedHashMap<>();
    position.put("platz", Integer.valueOf(platz));
    position.put("menge", menge);
    position.put("stundenJePersonentag", platz == 1 ? "7.50" : null);
    final Map<String, Object> felder = new LinkedHashMap<>();
    felder.put("positionen", List.of(position));
    return felder;
  }

  private String nummerDesJahres(final int laufend) {
    return "AU-%d-%03d"
        .formatted(
            Integer.valueOf(LocalDate.now(clock.withZone(Geschaeftszone.ZONE)).getYear()),
            Integer.valueOf(laufend));
  }

  @Test
  void anlegen_thenTakesTheFirstNumberOfTheYearOfCreation() {
    // When — Kriterium 3: das Jahr des Anlegens, nicht das des Auftragsdatums.
    final ResponseEntity<AuftragResponse> antwort =
        ruf(
            "/api/angebote/" + angebotId + "/auftrag",
            HttpMethod.POST,
            rumpf(1, "2.50"),
            AuftragResponse.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(Objects.requireNonNull(antwort.getBody()).nummer()).isEqualTo(nummerDesJahres(1));
  }

  @Test
  void anlegen_twiceInTheSameYear_thenTheSecondCarries002() {
    // Given — F9: der zweite Auftrag braucht ein zweites Angebot.
    ruf(
        "/api/angebote/" + angebotId + "/auftrag",
        HttpMethod.POST,
        rumpf(1, "2.50"),
        AuftragResponse.class);

    // When
    final ResponseEntity<AuftragResponse> zweiter =
        ruf(
            "/api/angebote/" + zweitesAngebotId + "/auftrag",
            HttpMethod.POST,
            rumpf(1, "2.50"),
            AuftragResponse.class);

    // Then
    assertThat(zweiter.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(Objects.requireNonNull(zweiter.getBody()).nummer()).isEqualTo(nummerDesJahres(2));
  }

  @Test
  void anlegen_thenThePricesComeFromTheAngebotAndNotFromTheRequest() {
    // When — Kriterium 2, Plan E7.
    final ResponseEntity<AuftragResponse> antwort =
        ruf(
            "/api/angebote/" + angebotId + "/auftrag",
            HttpMethod.POST,
            rumpfMitUntergeschobenemPreis(),
            AuftragResponse.class);

    // Then
    final AuftragResponse auftrag = Objects.requireNonNull(antwort.getBody());
    assertThat(auftrag.positionen())
        .singleElement()
        .satisfies(
            position -> {
              assertThat(position.bezeichnung()).isEqualTo("Konzeption");
              assertThat(position.einzelpreis()).isEqualByComparingTo(new BigDecimal("1000.01"));
              assertThat(position.betrag()).isEqualByComparingTo(new BigDecimal("2500.03"));
            });
    assertThat(auftrag.summe()).isEqualByComparingTo(new BigDecimal("2500.03"));
    assertThat(auftrag.angebotNummer()).isEqualTo("A-2026-001");
    assertThat(auftrag.kundenbestellnummer()).isEqualTo("BST-4711");
  }

  @Test
  void anlegen_thenTheVorgangCarriesTheEreignisWithTheNumber() {
    // When — Kriterium 8: die Ereigniszeile entsteht in derselben Transaktion.
    final ResponseEntity<AuftragResponse> antwort =
        ruf(
            "/api/angebote/" + angebotId + "/auftrag",
            HttpMethod.POST,
            rumpf(1, "2.50"),
            AuftragResponse.class);

    // Then
    assertThat(
            jdbc.queryForObject(
                "SELECT text FROM vorgang_eintrag WHERE vorgang_id = ? AND art = 'EREIGNIS'",
                String.class,
                Long.valueOf(vorgangId)))
        .isEqualTo("Auftrag " + Objects.requireNonNull(antwort.getBody()).nummer() + " angelegt");
  }

  @Test
  void anlegen_whenAnAuftragAlreadyExists_thenAnswers409AndDrawsNoSecondNumber() {
    // Given — F9.
    ruf(
        "/api/angebote/" + angebotId + "/auftrag",
        HttpMethod.POST,
        rumpf(1, "2.50"),
        AuftragResponse.class);

    // When
    final ResponseEntity<String> zweiter =
        ruf(
            "/api/angebote/" + angebotId + "/auftrag",
            HttpMethod.POST,
            rumpf(1, "2.50"),
            String.class);

    // Then
    assertThat(zweiter.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM auftrag", Long.class)).isEqualTo(1L);
  }

  @Test
  void anlegen_withAMengeAboveTheAngebot_thenAnswers409AndWritesNothing() {
    // When — Kriterium 2: die Menge darf nicht erhoeht werden.
    final ResponseEntity<String> antwort =
        ruf(
            "/api/angebote/" + angebotId + "/auftrag",
            HttpMethod.POST,
            rumpf(1, "3.00"),
            String.class);

    // Then — die abgewiesene Uebernahme hinterlaesst weder Auftrag noch verbrauchte Nummer.
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(antwort.getBody()).contains("positionen[0].menge");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM auftrag", Long.class)).isEqualTo(0L);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM auftrag_nummernkreis", Long.class))
        .isEqualTo(0L);
  }

  @Test
  void auftragZumAngebot_beforeAndAfter_thenAnswersTheRowAndTheAnlegbarFlag() {
    // Given — Plan E3: die Angebotsansicht fragt beides an einem Weg.
    final ResponseEntity<AngebotAuftragResponse> vorher =
        ruf(
            "/api/angebote/" + angebotId + "/auftrag",
            HttpMethod.GET,
            null,
            AngebotAuftragResponse.class);

    // When
    ruf(
        "/api/angebote/" + angebotId + "/auftrag",
        HttpMethod.POST,
        rumpf(1, "2.50"),
        AuftragResponse.class);
    final ResponseEntity<AngebotAuftragResponse> nachher =
        ruf(
            "/api/angebote/" + angebotId + "/auftrag",
            HttpMethod.GET,
            null,
            AngebotAuftragResponse.class);

    // Then
    assertThat(Objects.requireNonNull(vorher.getBody()).auftrag()).isNull();
    assertThat(vorher.getBody().anlegbar()).isTrue();
    assertThat(Objects.requireNonNull(nachher.getBody()).auftrag()).isNotNull();
    assertThat(nachher.getBody().auftrag().nummer()).isEqualTo(nummerDesJahres(1));
  }

  @Test
  void lesen_thenAnswersWithTheStoredAuftrag() {
    // Given
    final ResponseEntity<AuftragResponse> angelegt =
        ruf(
            "/api/angebote/" + angebotId + "/auftrag",
            HttpMethod.POST,
            rumpf(2, "1.00"),
            AuftragResponse.class);
    final long auftragId = Objects.requireNonNull(angelegt.getBody()).id();

    // When
    final ResponseEntity<AuftragResponse> gelesen =
        ruf("/api/auftraege/" + auftragId, HttpMethod.GET, null, AuftragResponse.class);

    // Then — die weggelassene erste Position fehlt, die zweite steht allein da.
    final AuftragResponse auftrag = Objects.requireNonNull(gelesen.getBody());
    assertThat(auftrag.angebotNummer()).isEqualTo("A-2026-001");
    assertThat(auftrag.positionen())
        .singleElement()
        .satisfies(
            position -> {
              assertThat(position.bezeichnung()).isEqualTo("Schulungstag");
              assertThat(position.stundenJePersonentag()).isNull();
            });
  }

  @Test
  void lesen_givenAnUnknownAuftrag_thenAnswers404() {
    // When
    final ResponseEntity<String> antwort =
        ruf("/api/auftraege/999", HttpMethod.GET, null, String.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void jederWeg_withoutASession_thenAnswersUnauthorized() {
    // When / Then — die Zugangsregel gilt fuer jeden neuen Pfad unter /api (K1).
    assertThat(
            rest.exchange(
                    "/api/angebote/" + angebotId + "/auftrag",
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(
            rest.exchange(
                    "/api/angebote/" + angebotId + "/auftrag",
                    HttpMethod.POST,
                    HttpEntity.EMPTY,
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(
            rest.exchange("/api/auftraege/1", HttpMethod.GET, HttpEntity.EMPTY, String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
