package org.mwolff.fbcrm.angebot;

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
import org.mwolff.fbcrm.angebot.web.AngebotPositionResponse;
import org.mwolff.fbcrm.angebot.web.AngebotResponse;
import org.mwolff.fbcrm.angebot.web.VorgangAngeboteResponse;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.PasswordHasher;
import org.mwolff.fbcrm.auth.domain.Role;
import org.mwolff.fbcrm.common.Geschaeftszone;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Der Lebensweg eines Angebotsentwurfs ueber HTTP, mit Sitzung und gegen eine echte
 * PostgreSQL-Instanz: anlegen, fortschreiben, lesen, verwerfen.
 *
 * <p>Drei Zusagen sind nur hier pruefbar, weil sie an der echten Transaktion und am echten Schema
 * haengen. Erstens E24: Nach einem {@code PUT} mit umgestellter Liste tragen die Positionszeilen
 * die Plaetze 1 bis n in der gesendeten Reihenfolge — das steht in der Spalte, nicht im Speicher.
 * Zweitens Kriterium 7: Ein verworfener Entwurf ist samt seinen Positionszeilen wirklich weg.
 * Drittens die Grenze aus E13: Am abgeschlossenen Vorgang antwortet das Anlegen 409, waehrend
 * derselbe Entwurf sich weiterhin fortschreiben laesst.
 *
 * <p>Der Nachweis der Zugangsregel steht hier und nicht nur in {@code AccessRuleIT}: Ein neuer Pfad
 * unter {@code /api} ist ohne Sitzung verschlossen, und das gehoert zu jedem neuen Weg dazu.
 */
class AngebotEntwurfIT extends AbstractIntegrationTest {

  private static final String MAIL = "manne@example.org";
  private static final String PASSWORT = "richtiges-passwort";
  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");
  private static final String STANDARDBEDINGUNGEN = "Zahlbar innerhalb von 30 Tagen netto.";

  private final TestRestTemplate rest;
  private final AccountRepository accounts;
  private final PasswordHasher hasher;
  private final JdbcTemplate jdbc;
  private final Clock clock;

  private HttpHeaders sitzung = new HttpHeaders();
  private long vorgangId;
  private long abgeschlossenerVorgangId;
  private long fremderVorgangId;

  @Autowired
  AngebotEntwurfIT(
      final TestRestTemplate rest,
      final AccountRepository accounts,
      final PasswordHasher hasher,
      final JdbcTemplate jdbc,
      final Clock clock) {
    this.rest = rest;
    this.accounts = accounts;
    this.hasher = hasher;
    this.jdbc = jdbc;
    this.clock = clock;
  }

  @BeforeEach
  void leereDenBestandUndMeldeAn() {
    jdbc.execute(
        "TRUNCATE angebot_position, angebot, vorgang_eintrag, vorgang, ansprechpartner, firma"
            + " RESTART IDENTITY CASCADE");
    jdbc.update("UPDATE eigene_angaben SET zahlungsbedingungen = ?", STANDARDBEDINGUNGEN);
    jdbc.update("INSERT INTO firma (name) VALUES ('Adler AG')");
    final long firmaId =
        Objects.requireNonNull(
                jdbc.queryForObject("SELECT id FROM firma WHERE name = 'Adler AG'", Long.class))
            .longValue();
    vorgangId = vorgang(1L, "Website-Relaunch", firmaId, false);
    abgeschlossenerVorgangId = vorgang(2L, "Altes Geschaeft", firmaId, true);
    fremderVorgangId = vorgang(3L, "Anderes Geschaeft", firmaId, false);
    jdbc.execute("TRUNCATE outbox_message, password_reset_token, account RESTART IDENTITY CASCADE");
    accounts.save(
        new Account(null, MAIL, "Manne", hasher.hash(PASSWORT), Role.ADMIN, 0, ANGELEGT, ANGELEGT));
    sitzung = angemeldeterKopf();
  }

  private long vorgang(
      final long nummer, final String titel, final long firmaId, final boolean abgeschlossen) {
    jdbc.update(
        "INSERT INTO vorgang (nummer, titel, firma_id, abgeschlossen) VALUES (?, ?, ?, ?)",
        Long.valueOf(nummer),
        titel,
        Long.valueOf(firmaId),
        Boolean.valueOf(abgeschlossen));
    return Objects.requireNonNull(
            jdbc.queryForObject(
                "SELECT id FROM vorgang WHERE nummer = ?", Long.class, Long.valueOf(nummer)))
        .longValue();
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

  private static Map<String, Object> position(
      final String bezeichnung, final String modus, final String menge, final String einheit) {
    final Map<String, Object> felder = new LinkedHashMap<>();
    felder.put("bezeichnung", bezeichnung);
    felder.put("abrechnungsmodus", modus);
    felder.put("menge", menge);
    felder.put("einheit", einheit);
    felder.put("einzelpreis", "1000.01");
    return felder;
  }

  private static Map<String, Object> entwurfsrumpf(final List<Map<String, Object>> positionen) {
    final Map<String, Object> felder = new LinkedHashMap<>();
    felder.put("gueltigBis", "2026-12-31");
    felder.put("leistungsbeschreibung", "Neugestaltung der Website");
    felder.put("zahlungsbedingungen", null);
    felder.put("positionen", positionen);
    return felder;
  }

  private ResponseEntity<AngebotResponse> anlegenMitVorlage(
      final long anVorgang, final long vorlageAngebotId) {
    return ruf(
        "/api/vorgaenge/" + anVorgang + "/angebote",
        HttpMethod.POST,
        Map.of("vorlageAngebotId", Long.valueOf(vorlageAngebotId)),
        AngebotResponse.class);
  }

  private AngebotResponse entwurf() {
    return Objects.requireNonNull(
        ruf(
                "/api/vorgaenge/" + vorgangId + "/angebote",
                HttpMethod.POST,
                null,
                AngebotResponse.class)
            .getBody());
  }

  private List<String> bezeichnungenNachPlatz(final long angebotId) {
    return jdbc.queryForList(
        "SELECT bezeichnung FROM angebot_position WHERE angebot_id = ? ORDER BY position",
        String.class,
        Long.valueOf(angebotId));
  }

  @Test
  void anlegen_thenAnswersCreatedWithThePrefilledDraft() {
    // Given — Kriterium 3: Angebotsdatum heute in der Geschaeftszone, Gueltigkeit dreissig Tage,
    // Zahlungsbedingungen aus „Eigene Angaben".
    final LocalDate heute = LocalDate.now(clock.withZone(Geschaeftszone.ZONE));

    // When
    final ResponseEntity<AngebotResponse> antwort =
        ruf(
            "/api/vorgaenge/" + vorgangId + "/angebote",
            HttpMethod.POST,
            null,
            AngebotResponse.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    final AngebotResponse angelegt = Objects.requireNonNull(antwort.getBody());
    assertThat(angelegt.stand().name()).isEqualTo("ENTWURF");
    assertThat(angelegt.nummer()).isNull();
    assertThat(angelegt.angebotDatum()).isEqualTo(heute);
    assertThat(angelegt.gueltigBis()).isEqualTo(heute.plusDays(30));
    assertThat(angelegt.zahlungsbedingungen()).isEqualTo(STANDARDBEDINGUNGEN);
    assertThat(angelegt.positionen()).isEmpty();
  }

  @Test
  void aendern_withAReorderedList_thenRenumbersThePositionsFromOne() {
    // Given — E24: die Plaetze entstehen aus der Reihenfolge der gesendeten Liste.
    final long angebotId = entwurf().id();
    ruf(
        "/api/angebote/" + angebotId,
        HttpMethod.PUT,
        entwurfsrumpf(
            List.of(
                position("Konzeption", "AUFWAND", "2.50", "PERSONENTAG"),
                position("Schulungstag", "FESTPREIS", "1.00", "PAUSCHAL"))),
        AngebotResponse.class);

    // When — dieselben Positionen, umgestellt.
    final ResponseEntity<AngebotResponse> antwort =
        ruf(
            "/api/angebote/" + angebotId,
            HttpMethod.PUT,
            entwurfsrumpf(
                List.of(
                    position("Schulungstag", "FESTPREIS", "1.00", "PAUSCHAL"),
                    position("Konzeption", "AUFWAND", "2.50", "PERSONENTAG"))),
            AngebotResponse.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(bezeichnungenNachPlatz(angebotId)).containsExactly("Schulungstag", "Konzeption");
    assertThat(
            jdbc.queryForList(
                "SELECT position FROM angebot_position WHERE angebot_id = ? ORDER BY position",
                Integer.class,
                Long.valueOf(angebotId)))
        .containsExactly(Integer.valueOf(1), Integer.valueOf(2));
  }

  @Test
  void lesen_thenAnswersWithThePositionsInOrderAndTheCalculatedSumme() {
    // Given — Kriterium 5: 2,5 × 1.000,01 € = 2.500,03 €, plus 1 × 1.000,01 €.
    final long angebotId = entwurf().id();
    ruf(
        "/api/angebote/" + angebotId,
        HttpMethod.PUT,
        entwurfsrumpf(
            List.of(
                position("Konzeption", "AUFWAND", "2.50", "PERSONENTAG"),
                position("Schulungstag", "FESTPREIS", "1.00", "PAUSCHAL"))),
        AngebotResponse.class);

    // When
    final AngebotResponse gelesen =
        Objects.requireNonNull(
            ruf("/api/angebote/" + angebotId, HttpMethod.GET, null, AngebotResponse.class)
                .getBody());

    // Then
    assertThat(gelesen.positionen())
        .extracting(AngebotPositionResponse::bezeichnung)
        .containsExactly("Konzeption", "Schulungstag");
    assertThat(gelesen.summe()).isEqualByComparingTo(new BigDecimal("3500.04"));
    assertThat(gelesen.gueltigBis()).isEqualTo(LocalDate.of(2026, 12, 31));
  }

  @Test
  void verwerfen_thenTheDraftAndItsPositionsAreGone() {
    // Given — Kriterium 7.
    final long angebotId = entwurf().id();
    ruf(
        "/api/angebote/" + angebotId,
        HttpMethod.PUT,
        entwurfsrumpf(List.of(position("Konzeption", "AUFWAND", "2.50", "PERSONENTAG"))),
        AngebotResponse.class);

    // When
    final ResponseEntity<Void> antwort =
        ruf("/api/angebote/" + angebotId, HttpMethod.DELETE, null, Void.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(
            ruf("/api/angebote/" + angebotId, HttpMethod.GET, null, String.class).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(bezeichnungenNachPlatz(angebotId)).isEmpty();
  }

  @Test
  void angebote_thenListsTheDraftsOfTheVorgangNewestFirst() {
    // Given — Kriterium 20, E25.
    final long aelterer = entwurf().id();
    final long juengerer = entwurf().id();

    // When
    final VorgangAngeboteResponse liste =
        Objects.requireNonNull(
            ruf(
                    "/api/vorgaenge/" + vorgangId + "/angebote",
                    HttpMethod.GET,
                    null,
                    VorgangAngeboteResponse.class)
                .getBody());

    // Then
    assertThat(liste.angebote())
        .extracting(zeile -> Long.valueOf(zeile.id()))
        .containsExactly(Long.valueOf(juengerer), Long.valueOf(aelterer));
  }

  @Test
  void anlegen_withAVorlage_thenCopiesTheTextsAndThePositionen() {
    // Given — E23.
    final long quelle = entwurf().id();
    ruf(
        "/api/angebote/" + quelle,
        HttpMethod.PUT,
        entwurfsrumpf(List.of(position("Konzeption", "AUFWAND", "2.50", "PERSONENTAG"))),
        AngebotResponse.class);

    // When
    final AngebotResponse kopie =
        Objects.requireNonNull(anlegenMitVorlage(vorgangId, quelle).getBody());

    // Then
    assertThat(kopie.id()).isNotEqualTo(quelle);
    assertThat(kopie.leistungsbeschreibung()).isEqualTo("Neugestaltung der Website");
    assertThat(kopie.positionen())
        .extracting(AngebotPositionResponse::bezeichnung)
        .containsExactly("Konzeption");
    assertThat(kopie.gueltigBis()).isNotEqualTo(LocalDate.of(2026, 12, 31));
  }

  @Test
  void anlegen_withAVorlageFromAnotherVorgang_thenAnswers422() {
    // Given — E23: die Quelle haengt an einem anderen, offenen Vorgang.
    final long quelle = entwurf().id();

    // When
    final ResponseEntity<AngebotResponse> antwort = anlegenMitVorlage(fremderVorgangId, quelle);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
  }

  @Test
  void anlegen_atAClosedVorgangWithAVorlage_thenTheClosedVorgangDecidesFirst() {
    // Given — E13 wird vor E23 geprueft: eine abgewiesene Anlage liest die Vorlage nicht einmal.
    final long quelle = entwurf().id();

    // When
    final ResponseEntity<AngebotResponse> antwort =
        anlegenMitVorlage(abgeschlossenerVorgangId, quelle);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void schreibsperre_atAClosedVorgang_thenBlocksAnlegenButNotAendern() {
    // Given — E13, Kriterium 9: die Sperre gilt dem Anlegen, nicht der Pflege eines Entwurfs.
    final long angebotId = entwurf().id();
    jdbc.update("UPDATE vorgang SET abgeschlossen = true WHERE id = ?", Long.valueOf(vorgangId));

    // When
    final ResponseEntity<String> anlegen =
        ruf("/api/vorgaenge/" + vorgangId + "/angebote", HttpMethod.POST, null, String.class);
    final ResponseEntity<AngebotResponse> aendern =
        ruf(
            "/api/angebote/" + angebotId,
            HttpMethod.PUT,
            entwurfsrumpf(List.of(position("Konzeption", "AUFWAND", "2.50", "PERSONENTAG"))),
            AngebotResponse.class);

    // Then
    assertThat(anlegen.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(aendern.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void angebotswege_withoutASession_thenUnauthorized() {
    // Given — jeder neue Pfad unter /api ist ohne Sitzung verschlossen.
    sitzung = new HttpHeaders();

    // When / Then
    assertThat(
            ruf("/api/vorgaenge/" + vorgangId + "/angebote", HttpMethod.GET, null, String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(ruf("/api/angebote/1", HttpMethod.GET, null, String.class).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
