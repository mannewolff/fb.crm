package org.mwolff.fbcrm.arbeitszeit.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.angebot.web.AngebotPositionResponse;
import org.mwolff.fbcrm.angebot.web.AngebotResponse;
import org.mwolff.fbcrm.auth.Anmeldung;
import org.mwolff.fbcrm.auth.Konten;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.Role;
import org.mwolff.fbcrm.auth.domain.SecretHasher;
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
 * Die Schreibwege der Arbeitszeit ueber HTTP, mit Sitzung und gegen eine echte PostgreSQL-Instanz
 * (Issue #193, Kriterien 1, 3, 4 und 6).
 *
 * <p>Hier stehen die Zusagen, die an der echten Verdrahtung und am echten Schema haengen: Ein
 * erfasster Eintrag kommt mit 201 und seiner gerechneten Dauer zurueck, ein {@code PUT} antwortet
 * 200, ein {@code DELETE} nimmt die Zeile wirklich fort, und eine unbekannte Kennung ist 404. Dazu
 * die beiden Abweisungen, die der Anwendungsfall als Meldung am Feld liefert (Plan #194, A19, A8):
 * 9:10 liegt nicht im Raster, und ein zweiter Eintrag zur selben Zeit nennt den ersten mit Uhrzeit,
 * Position und Firma.
 *
 * <p>Dazu die beiden Lesewege (Plan #194, A20, A15): Die Monatsliste traegt je Eintrag die
 * Bezeichnung seiner Position und den Namen der Firma, je Tag eine Summe und fuer den Monat eine
 * weitere; ohne Parameter antwortet sie fuer den laufenden Monat, und ein Monat, den es nicht gibt,
 * ist eine fehlerhafte Anfrage. Die buchbaren Positionen nennen nur, was nach Aufwand in Stunden
 * abrechnet. Die waehlbaren Personen (Plan #426, E11) nennt ein eigener Weg. Welche Zeilen die
 * Monatsliste zeigt, entscheidet die Rolle (E10): Ein Employee bekommt auch mit {@code person=alle}
 * nur seine eigenen.
 *
 * <p>Der Ausgangspunkt ist ein bestelltes Angebot mit zwei Positionen: „Konzeption" nach Aufwand in
 * Stunden, auf die gebucht werden darf, und daneben eine Pauschale, auf die nicht gebucht werden
 * darf. Die Namen sind die des Beispiels aus #193.
 */
class ArbeitszeitControllerIT extends AbstractIntegrationTest {

  private static final String MAIL = "manne@example.org";
  private static final Instant ANGELEGT = Instant.parse("2026-11-01T08:00:00Z");

  private static final String FIRMA = "IT Bildungshaus";
  private static final String TAG = "2026-11-12";
  private static final String SPAETERER_TAG = "2026-11-20";
  private static final String MONAT = "2026-11";
  private static final String PFAD = "/api/arbeitszeit";

  private final TestRestTemplate rest;
  private final AccountRepository accounts;
  private final SecretHasher hasher;
  private final JdbcTemplate jdbc;

  private HttpHeaders sitzung = new HttpHeaders();
  private long angebotId;
  private long konzeptionId;
  private long schulungId;

  @Autowired
  ArbeitszeitControllerIT(
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
  void legeDenAusgangspunktAn() {
    jdbc.execute(
        "TRUNCATE arbeitszeit, rechnung_position, rechnung, angebot_position, angebot,"
            + " ansprechpartner, firma RESTART IDENTITY CASCADE");
    jdbc.execute("TRUNCATE outbox_message, account RESTART IDENTITY CASCADE");
    accounts.save(Konten.neu(MAIL, ANGELEGT));
    sitzung = Anmeldung.perCode(rest, jdbc, hasher, MAIL);
    jdbc.update("INSERT INTO firma (name) VALUES (?)", FIRMA);
    final long firmaId =
        Objects.requireNonNull(
                jdbc.queryForObject("SELECT id FROM firma WHERE name = ?", Long.class, FIRMA))
            .longValue();
    angebotId =
        Objects.requireNonNull(
                ruf(
                        "/api/firmen/" + firmaId + "/angebote",
                        HttpMethod.POST,
                        Map.of("titel", "Website-Relaunch"),
                        AngebotResponse.class)
                    .getBody())
            .id();
    final AngebotResponse mitPositionen =
        Objects.requireNonNull(
            aendereDasAngebot(
                    List.of(
                        position("Konzeption", "AUFWAND", "20.00", "STUNDE", "120.00"),
                        position("Schulungstag", "FESTPREIS", "1", "PAUSCHAL", "1200.00")))
                .getBody());
    konzeptionId = kennung(mitPositionen.positionen().get(0));
    schulungId = kennung(mitPositionen.positionen().get(1));
    ruf("/api/angebote/" + angebotId + "/status/weiter", HttpMethod.POST, null, String.class);
    ruf("/api/angebote/" + angebotId + "/status/weiter", HttpMethod.POST, null, String.class);
  }

  private <T> ResponseEntity<T> ruf(
      final String pfad, final HttpMethod methode, final Object rumpf, final Class<T> typ) {
    return rest.exchange(pfad, methode, new HttpEntity<>(rumpf, sitzung), typ);
  }

  private static long kennung(final AngebotPositionResponse position) {
    return Objects.requireNonNull(position.id()).longValue();
  }

  private static Map<String, Object> position(
      final String bezeichnung,
      final String abrechnungsmodus,
      final String menge,
      final String einheit,
      final String einzelpreis) {
    final Map<String, Object> felder = new LinkedHashMap<>();
    felder.put("id", null);
    felder.put("bezeichnung", bezeichnung);
    felder.put("abrechnungsmodus", abrechnungsmodus);
    felder.put("menge", menge);
    felder.put("einheit", einheit);
    felder.put("einzelpreis", einzelpreis);
    return felder;
  }

  private ResponseEntity<AngebotResponse> aendereDasAngebot(
      final List<Map<String, Object>> positionen) {
    final Map<String, Object> rumpf = new LinkedHashMap<>();
    rumpf.put("angebotDatum", "2026-11-01");
    rumpf.put("titel", "Website-Relaunch");
    rumpf.put("ansprechpartnerId", null);
    rumpf.put("beschreibung", "Neugestaltung der Website");
    rumpf.put("positionen", positionen);
    return ruf("/api/angebote/" + angebotId, HttpMethod.PUT, rumpf, AngebotResponse.class);
  }

  private static Map<String, Object> eintrag(
      final long angebotPositionId, final String von, final String bis) {
    return eintrag(angebotPositionId, TAG, von, bis);
  }

  private static Map<String, Object> eintrag(
      final long angebotPositionId, final String tag, final String von, final String bis) {
    final Map<String, Object> rumpf = new LinkedHashMap<>();
    rumpf.put("angebotPositionId", Long.valueOf(angebotPositionId));
    rumpf.put("tag", tag);
    rumpf.put("von", von);
    rumpf.put("bis", bis);
    return rumpf;
  }

  private <T> ResponseEntity<T> erfasse(final String von, final String bis, final Class<T> typ) {
    return ruf(PFAD, HttpMethod.POST, eintrag(konzeptionId, von, bis), typ);
  }

  private long erfassteKennung(final String von, final String bis) {
    return Objects.requireNonNull(erfasse(von, bis, ZeiteintragResponse.class).getBody()).id();
  }

  @Test
  void anlegen_thenAnswers201WithTheEntryAndItsCalculatedHours() {
    // Given — Kriterium 1: Tag, von, bis und die Position; die Dauer rechnet die Anwendung.
    // When
    final ResponseEntity<ZeiteintragResponse> erfasst =
        erfasse("09:00", "10:45", ZeiteintragResponse.class);

    // Then
    assertThat(erfasst.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    final ZeiteintragResponse angelegt = Objects.requireNonNull(erfasst.getBody());
    assertThat(angelegt.stunden()).isEqualByComparingTo("1.75");
  }

  @Test
  void anlegen_thenTheAnswerCarriesDayAndTimeAsText() {
    // Given — die Oberflaeche liest Tag und Uhrzeit als Text. Welche Form der ObjectMapper von
    // Spring Boot schreibt, laesst sich nur hier belegen: standaloneSetup kennt ihn nicht.
    // When
    final ResponseEntity<String> erfasst = erfasse("09:00", "10:45", String.class);

    // Then
    assertThat(erfasst.getBody())
        .contains("\"tag\":\"" + TAG + "\"")
        .contains("\"von\":\"09:00:00\"")
        .contains("\"bis\":\"10:45:00\"");
  }

  @Test
  void anlegen_withoutASession_thenAnswers401() {
    // Given — die Wege fallen unter /api/** und sind nur angemeldet erreichbar (E11).
    sitzung = new HttpHeaders();

    // When / Then
    assertThat(erfasse("09:00", "10:45", String.class).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void anlegen_givenABeginnOutsideTheGrid_thenAnswers422WithAFieldErrorAtVon() {
    // Given — A19: 9:10 ist keine Viertelstunde, und Kriterium 3 verlangt die Meldung am Feld.
    // When
    final ResponseEntity<String> abgewiesen = erfasse("09:10", "11:00", String.class);

    // Then
    assertThat(abgewiesen.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(abgewiesen.getBody()).contains("\"fieldErrors\"").contains("\"von\"");
  }

  @Test
  void anlegen_givenAnOverlappingEntry_thenNamesTheOtherEntryInTheFieldMessage() {
    // Given — A8, Kriterium 4: der erste Eintrag belegt 9:00 bis 11:00.
    erfasse("09:00", "11:00", ZeiteintragResponse.class);

    // When — der zweite Eintrag greift hinein, auf derselben Position.
    final ResponseEntity<String> abgewiesen = erfasse("10:00", "12:00", String.class);

    // Then — die Meldung nennt den anderen Eintrag mit Uhrzeit, Position und Firma.
    assertThat(abgewiesen.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(abgewiesen.getBody())
        .contains("\"fieldErrors\"")
        .contains("9:00 bis 11:00")
        .contains("Konzeption")
        .contains(FIRMA);
  }

  @Test
  void anlegen_onAFlatRatePosition_thenAnswers422WithAFieldErrorAtThePosition() {
    // Given — Antworten 3 und 5: auf eine Pauschale wird keine Zeit gebucht.
    // When
    final ResponseEntity<String> abgewiesen =
        ruf(PFAD, HttpMethod.POST, eintrag(schulungId, "09:00", "11:00"), String.class);

    // Then
    assertThat(abgewiesen.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(abgewiesen.getBody()).contains("\"fieldErrors\"").contains("\"angebotPositionId\"");
  }

  @Test
  void aendern_thenAnswers200WithTheChangedEntry() {
    // Given — Kriterium 6.
    final long id = erfassteKennung("09:00", "11:00");

    // When
    final ResponseEntity<ZeiteintragResponse> geaendert =
        ruf(
            PFAD + "/" + id,
            HttpMethod.PUT,
            eintrag(konzeptionId, "09:00", "12:00"),
            ZeiteintragResponse.class);

    // Then
    assertThat(geaendert.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(Objects.requireNonNull(geaendert.getBody()).stunden()).isEqualByComparingTo("3.00");
  }

  @Test
  void aendern_whenTheEntryIsUnknown_thenAnswers404() {
    // Given — die Kennung steht im Pfad.
    // When / Then
    assertThat(
            ruf(
                    PFAD + "/999",
                    HttpMethod.PUT,
                    eintrag(konzeptionId, "09:00", "12:00"),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void loeschen_thenTheRowIsGone() {
    // Given — A5, Antwort 4: geloescht wird wirklich.
    final long id = erfassteKennung("09:00", "11:00");

    // When
    final ResponseEntity<String> geloescht =
        ruf(PFAD + "/" + id, HttpMethod.DELETE, null, String.class);

    // Then — derselbe Eintrag ist danach nicht mehr aenderbar.
    assertThat(geloescht.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(
            ruf(
                    PFAD + "/" + id,
                    HttpMethod.PUT,
                    eintrag(konzeptionId, "09:00", "12:00"),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void loeschen_whenTheEntryIsUnknown_thenAnswers404() {
    // Given
    // When / Then
    assertThat(ruf(PFAD + "/999", HttpMethod.DELETE, null, String.class).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  private ArbeitszeitMonatResponse lieseDenMonat(final String monat) {
    return Objects.requireNonNull(
        ruf(PFAD + "?monat=" + monat, HttpMethod.GET, null, ArbeitszeitMonatResponse.class)
            .getBody());
  }

  @Test
  void monat_thenEachEntryCarriesItsPositionWithTheCompanyName() {
    // Given — A20, Kriterium 5: die Zeile nennt die Position und die Firma, zu der sie gehoert.
    erfasse("09:00", "10:45", ZeiteintragResponse.class);

    // When
    final ArbeitszeitMonatResponse gelesen = lieseDenMonat(MONAT);

    // Then
    final ArbeitszeitMonatResponse.Zeitzeile zeile = gelesen.tage().get(0).eintraege().get(0);
    assertThat(zeile.position().bezeichnung()).isEqualTo("Konzeption");
    assertThat(zeile.position().firmaName()).isEqualTo(FIRMA);
    assertThat(zeile.position().angebotId()).isEqualTo(angebotId);
    assertThat(zeile.stunden()).isEqualByComparingTo("1.75");
  }

  @Test
  void monat_thenCarriesTheSumOfEachDayAndOfTheMonth() {
    // Given — zwei Tage: 1,75 Std. und 2,00 Std. am 12., 2,00 Std. am 20. November.
    erfasse("09:00", "10:45", ZeiteintragResponse.class);
    ruf(PFAD, HttpMethod.POST, eintrag(konzeptionId, "13:00", "15:00"), String.class);
    ruf(
        PFAD,
        HttpMethod.POST,
        eintrag(konzeptionId, SPAETERER_TAG, "08:00", "10:00"),
        String.class);

    // When
    final ArbeitszeitMonatResponse gelesen = lieseDenMonat(MONAT);

    // Then — Tage aufsteigend, je Tag die Summe seiner Zeilen, darueber die des Monats.
    assertThat(gelesen.monat()).isEqualTo(YearMonth.of(2026, 11));
    assertThat(gelesen.tage()).hasSize(2);
    assertThat(gelesen.tage().get(0).stunden()).isEqualByComparingTo("3.75");
    assertThat(gelesen.tage().get(1).stunden()).isEqualByComparingTo("2.00");
    assertThat(gelesen.stunden()).isEqualByComparingTo("5.75");
    // Und die Aufteilung der Monatssumme (Issue #230): hier ist alles auf ein Kundenangebot
    // gebucht, also traegt der Kundenteil die ganze Summe und der interne nichts.
    assertThat(gelesen.stundenFuerKunden()).isEqualByComparingTo("5.75");
    assertThat(gelesen.stundenIntern()).isEqualByComparingTo("0.00");
  }

  @Test
  void monat_withoutTheParameter_thenAnswersTheCurrentMonthInTheBusinessZone() {
    // Given — E4: welcher Monat laeuft, entscheidet der Kalender des Freiberuflers.
    // When
    final ArbeitszeitMonatResponse gelesen =
        Objects.requireNonNull(
            ruf(PFAD, HttpMethod.GET, null, ArbeitszeitMonatResponse.class).getBody());

    // Then
    assertThat(gelesen.monat()).isEqualTo(YearMonth.now(Geschaeftszone.ZONE));
    assertThat(gelesen.stunden()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(gelesen.stundenFuerKunden()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(gelesen.stundenIntern()).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void monat_givenAMonthThatIsNoMonth_thenAnswers400() {
    // Given — 2026-13 gibt es nicht.
    // When / Then
    assertThat(ruf(PFAD + "?monat=2026-13", HttpMethod.GET, null, String.class).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void monat_withoutASession_thenAnswers401() {
    // Given — auch der Leseweg faellt unter /api/** (E11).
    sitzung = new HttpHeaders();

    // When / Then
    assertThat(ruf(PFAD + "?monat=" + MONAT, HttpMethod.GET, null, String.class).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void buchbarePositionen_thenOnlyTheEffortPositionInHours() {
    // Given — A15: der Schulungstag ist eine Pauschale und steht nicht zur Wahl.
    // When
    final ResponseEntity<BuchungspositionResponse[]> gelesen =
        ruf(PFAD + "/buchbare-positionen", HttpMethod.GET, null, BuchungspositionResponse[].class);

    // Then
    assertThat(gelesen.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(Objects.requireNonNull(gelesen.getBody()))
        .singleElement()
        .satisfies(
            position -> {
              assertThat(position.id()).isEqualTo(konzeptionId);
              assertThat(position.bezeichnung()).isEqualTo("Konzeption");
              assertThat(position.firmaName()).isEqualTo(FIRMA);
              assertThat(position.angebotId()).isEqualTo(angebotId);
              assertThat(position.angebotTitel()).isEqualTo("Website-Relaunch");
            });
  }

  @Test
  void personen_signedInAsCLevel_thenAnswers200WithAllAccountsIncludingTheDeactivatedOne() {
    // Given — Kriterium 9 von #410: auch eine stillgelegte Person steht zur Auswahl.
    final long ben =
        accounts
            .save(
                Konten.mit(null, "ben@example.org", Role.EMPLOYEE, false, 0, ANGELEGT)
                    .mitNamen("Ben", ANGELEGT))
            .requireId();

    // When
    final ResponseEntity<PersonResponse[]> gelesen =
        ruf(PFAD + "/personen", HttpMethod.GET, null, PersonResponse[].class);

    // Then — nach Anzeigenamen: Ben vor Manne.
    assertThat(gelesen.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(Objects.requireNonNull(gelesen.getBody()))
        .extracting(PersonResponse::id, PersonResponse::name, PersonResponse::aktiv)
        .containsExactly(
            tuple(ben, "Ben", false),
            tuple(accounts.findByEmail(MAIL).orElseThrow().requireId(), "Manne", true));
  }

  @Test
  void monat_asAnEmployeeAskingForAll_thenOnlyTheOwnEntries() {
    // Given — Kriterium 2 von #410: Manne (C-Level) und Ben (Employee) haben am selben Tag Zeit
    // erfasst; Ben meldet sich an und fragt ausdruecklich nach „Alle".
    erfasse("09:00", "10:45", ZeiteintragResponse.class);
    final long ben =
        accounts
            .save(
                Konten.mit(null, "ben@example.org", Role.EMPLOYEE, true, 0, ANGELEGT)
                    .mitNamen("Ben", ANGELEGT))
            .requireId();
    sitzung = Anmeldung.perCode(rest, jdbc, hasher, "ben@example.org");
    final long bensEintrag = erfassteKennung("11:00", "12:00");

    // When
    final ArbeitszeitMonatResponse gelesen =
        Objects.requireNonNull(
            ruf(
                    PFAD + "?monat=" + MONAT + "&person=alle",
                    HttpMethod.GET,
                    null,
                    ArbeitszeitMonatResponse.class)
                .getBody());

    // Then — nur Bens Eintrag mit seinem Namen, seine Summen, und die Auswahl nennt ihn.
    assertThat(gelesen.tage())
        .singleElement()
        .satisfies(tag -> assertThat(tag.stunden()).isEqualByComparingTo("1.00"));
    assertThat(gelesen.tage().get(0).eintraege())
        .extracting(
            zeile -> zeile.id(), zeile -> zeile.person().id(), zeile -> zeile.person().name())
        .containsExactly(tuple(bensEintrag, ben, "Ben"));
    assertThat(gelesen.stunden()).isEqualByComparingTo("1.00");
    assertThat(gelesen.auswahl()).isEqualTo(String.valueOf(ben));
  }

  @Test
  void monat_asACLevelAskingForAll_thenTheEntriesOfEveryPersonWithTheirNames() {
    // Given — Kriterium 3 von #410: bei „Alle" steht an jedem Eintrag der Name der Person.
    final HttpHeaders manne = sitzung;
    erfasse("09:00", "10:45", ZeiteintragResponse.class);
    accounts.save(
        Konten.mit(null, "ben@example.org", Role.EMPLOYEE, true, 0, ANGELEGT)
            .mitNamen("Ben", ANGELEGT));
    sitzung = Anmeldung.perCode(rest, jdbc, hasher, "ben@example.org");
    erfasse("11:00", "12:00", ZeiteintragResponse.class);
    sitzung = manne;

    // When
    final ArbeitszeitMonatResponse gelesen =
        Objects.requireNonNull(
            ruf(
                    PFAD + "?monat=" + MONAT + "&person=alle",
                    HttpMethod.GET,
                    null,
                    ArbeitszeitMonatResponse.class)
                .getBody());

    // Then — zwei Personen am selben Tag, und die Summen gelten fuer beide.
    assertThat(gelesen.auswahl()).isEqualTo("alle");
    assertThat(gelesen.tage().get(0).eintraege())
        .extracting(zeile -> zeile.person().name())
        .containsExactly("Manne", "Ben");
    assertThat(gelesen.stunden()).isEqualByComparingTo("2.75");
  }

  private long legeBenAn() {
    return accounts
        .save(
            Konten.mit(null, "ben@example.org", Role.EMPLOYEE, true, 0, ANGELEGT)
                .mitNamen("Ben", ANGELEGT))
        .requireId();
  }

  private static Map<String, Object> fuer(final long accountId, final Map<String, Object> rumpf) {
    final Map<String, Object> mitPerson = new LinkedHashMap<>(rumpf);
    mitPerson.put("accountId", Long.valueOf(accountId));
    return mitPerson;
  }

  /*
   * Die Zeile, wie sie in der Tabelle steht — verglichen wird vorher gegen nachher, nicht gegen
   * einen erwarteten Text: Wie die Uhrzeit dort steht, ist Sache der Abbildung und nicht dieses
   * Tests.
   */
  private Map<String, Object> zeile(final long id) {
    return jdbc.queryForMap(
        "SELECT account_id, angebot_position_id, tag, von, bis, updated_at FROM arbeitszeit"
            + " WHERE id = ?",
        id);
  }

  private long personDerZeile(final long id) {
    return Objects.requireNonNull(
            jdbc.queryForObject("SELECT account_id FROM arbeitszeit WHERE id = ?", Long.class, id))
        .longValue();
  }

  @Test
  void anlegen_byACLevelWithAPerson_thenTheEntryBelongsToThatPerson() {
    // Given — Kriterium 1 von #410: Manne (C-Level) traegt Zeit fuer Ben nach (Plan #426, E12).
    final long ben = legeBenAn();

    // When
    final ResponseEntity<ZeiteintragResponse> erfasst =
        ruf(
            PFAD,
            HttpMethod.POST,
            fuer(ben, eintrag(konzeptionId, "09:00", "12:00")),
            ZeiteintragResponse.class);

    // Then — die Antwort und die Zeile nennen Ben.
    assertThat(erfasst.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    final ZeiteintragResponse angelegt = Objects.requireNonNull(erfasst.getBody());
    assertThat(angelegt.accountId()).isEqualTo(ben);
    assertThat(personDerZeile(angelegt.id())).isEqualTo(ben);
  }

  @Test
  void aendern_byACLevelWithoutAPerson_thenKeepsTheOwnerUntilAnotherOneIsNamed() {
    // Given — Ben hat 8:00 bis 18:00 Uhr gebucht; Manne (C-Level) korrigiert (E12, E13).
    final HttpHeaders manne = sitzung;
    final long manneId = accounts.findByEmail(MAIL).orElseThrow().requireId();
    final long ben = legeBenAn();
    sitzung = Anmeldung.perCode(rest, jdbc, hasher, "ben@example.org");
    final long bensEintrag = erfassteKennung("08:00", "18:00");
    sitzung = manne;

    // When — ein PUT ohne Feld accountId
    final ResponseEntity<ZeiteintragResponse> korrigiert =
        ruf(
            PFAD + "/" + bensEintrag,
            HttpMethod.PUT,
            eintrag(konzeptionId, "08:00", "16:00"),
            ZeiteintragResponse.class);

    // Then — die Zeiten sind geaendert, und der Eintrag gehoert weiter Ben.
    assertThat(korrigiert.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(Objects.requireNonNull(korrigiert.getBody()).stunden()).isEqualByComparingTo("8.00");
    assertThat(personDerZeile(bensEintrag)).isEqualTo(ben);

    // When — erst ein PUT mit genanntem anderem Konto haengt ihn um.
    final ResponseEntity<ZeiteintragResponse> umgehaengt =
        ruf(
            PFAD + "/" + bensEintrag,
            HttpMethod.PUT,
            fuer(manneId, eintrag(konzeptionId, "08:00", "16:00")),
            ZeiteintragResponse.class);

    // Then
    assertThat(umgehaengt.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(Objects.requireNonNull(umgehaengt.getBody()).accountId()).isEqualTo(manneId);
    assertThat(personDerZeile(bensEintrag)).isEqualTo(manneId);
  }

  @Test
  void aendern_byAnEmployeeOnAForeignEntry_thenAnswers404AndTheRowIsUnchanged() {
    // Given — E7: Mannes Eintrag ist fuer Ben einer, den es nicht gibt.
    final long mannesEintrag = erfassteKennung("09:00", "11:00");
    final Map<String, Object> vorher = zeile(mannesEintrag);
    legeBenAn();
    sitzung = Anmeldung.perCode(rest, jdbc, hasher, "ben@example.org");

    // When
    final ResponseEntity<String> abgewiesen =
        ruf(
            PFAD + "/" + mannesEintrag,
            HttpMethod.PUT,
            eintrag(konzeptionId, "09:00", "12:00"),
            String.class);

    // Then
    assertThat(abgewiesen.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(zeile(mannesEintrag)).isEqualTo(vorher);
  }

  @Test
  void personen_withoutASession_thenAnswers401() {
    // Given
    sitzung = new HttpHeaders();

    // When / Then
    assertThat(ruf(PFAD + "/personen", HttpMethod.GET, null, String.class).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
