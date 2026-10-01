package org.mwolff.fbcrm.rechnung.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.angebot.web.AngebotPositionResponse;
import org.mwolff.fbcrm.angebot.web.AngebotResponse;
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
 * Die Vorbelegung eines Rechnungsentwurfs aus der Arbeitszeit, ueber HTTP und gegen eine echte
 * PostgreSQL-Instanz (Issue #193, Kriterien 9 bis 12; Plan #194, A9 bis A11, E1, E7).
 *
 * <p>Hier steht das Beispiel aus #193 als Ganzes: 20 Stunden angeboten, im Oktober 10 erfasst und
 * abgerechnet, im November 12 erfasst. Der Novemberentwurf traegt die 12 — mehr als die offenen 10
 * —, und die Antwort nennt die Ueberschreitung von 2,00. Nur hier pruefbar, denn dazu muessen
 * Zeiteintraege und eine Rechnung im echten Bestand stehen und die Summe eines Monats quer ueber
 * beide Module entstehen: {@code rechnung} fragt {@code arbeitszeit} ueber dessen Auskunft (A1).
 *
 * <p>Dazu die drei Formen des Aufrufs, die der Nachbar in der Oberflaeche braucht: ohne Rumpf wie
 * bisher, mit einem Monat, und mit einem Monat, in dem keine Stunde steht — der letzte ergibt genau
 * den Entwurf des ersten (E7). In jedem Fall bekommt die Festpreisposition ihre offene Menge und
 * nicht null Stunden (E1).
 *
 * <p>Der Ausgangspunkt ist der von {@code ArbeitszeitControllerIT}: ein bestelltes Angebot mit
 * „Konzeption" nach Aufwand in Stunden und daneben einer Pauschale, auf die nicht gebucht werden
 * darf. Die Namen sind die des Beispiels aus #193.
 */
class AngebotRechnungenControllerIT extends AbstractIntegrationTest {

  private static final String MAIL = "manne@example.org";
  private static final String PASSWORT = "richtiges-passwort";
  private static final Instant ANGELEGT = Instant.parse("2026-10-01T08:00:00Z");

  private static final String FIRMA = "IT Bildungshaus";

  /** Der Oktober: ein Eintrag ueber 10 Stunden. */
  private static final String OKTOBER = "2026-10";

  private static final String OKTOBERTAG = "2026-10-12";

  /** Der November: ein Eintrag ueber 12 Stunden. */
  private static final String NOVEMBER = "2026-11";

  private static final String NOVEMBERTAG = "2026-11-12";

  /** Ein Monat ohne jeden Eintrag. */
  private static final String SEPTEMBER = "2026-09";

  /** Die Form, in der der Leistungszeitraum einen Monat nennt: „Oktober 2026". */
  private static final DateTimeFormatter MONATSNAME =
      DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN);

  private final TestRestTemplate rest;
  private final AccountRepository accounts;
  private final PasswordHasher hasher;
  private final JdbcTemplate jdbc;

  private HttpHeaders sitzung = new HttpHeaders();
  private long angebotId;
  private long konzeptionId;
  private long schulungId;

  @Autowired
  AngebotRechnungenControllerIT(
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
  void legeDenAusgangspunktAn() {
    jdbc.execute(
        "TRUNCATE arbeitszeit, rechnung_position, rechnung, angebot_position, angebot,"
            + " ansprechpartner, firma RESTART IDENTITY CASCADE");
    jdbc.execute("DELETE FROM rechnung_einstellungen");
    jdbc.execute("INSERT INTO rechnung_einstellungen DEFAULT VALUES");
    jdbc.execute("TRUNCATE outbox_message, password_reset_token, account RESTART IDENTITY CASCADE");
    accounts.save(
        new Account(null, MAIL, "Manne", hasher.hash(PASSWORT), Role.ADMIN, 0, ANGELEGT, ANGELEGT));
    sitzung = angemeldeterKopf();
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
                        null,
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
    rumpf.put("angebotDatum", "2026-10-01");
    rumpf.put("ansprechpartnerId", null);
    rumpf.put("beschreibung", "Neugestaltung der Website");
    rumpf.put("positionen", positionen);
    return ruf("/api/angebote/" + angebotId, HttpMethod.PUT, rumpf, AngebotResponse.class);
  }

  /*
   * Der laufende Monat als Text, gelesen erst im Test und nicht beim Laden der Klasse: Ein
   * Zeitpunkt im statischen Initialisierer waere der Zeitpunkt des Klassenladens (Error Prone,
   * TimeInStaticInitializer).
   */
  private static String laufenderMonat() {
    return MONATSNAME.format(YearMonth.now(Geschaeftszone.ZONE));
  }

  /** Erfasst Arbeitszeit auf „Konzeption" — beide Uhrzeiten liegen im Viertelstundenraster. */
  private void erfasse(final String tag, final String von, final String bis) {
    final Map<String, Object> rumpf = new LinkedHashMap<>();
    rumpf.put("angebotPositionId", Long.valueOf(konzeptionId));
    rumpf.put("tag", tag);
    rumpf.put("von", von);
    rumpf.put("bis", bis);
    assertThat(ruf("/api/arbeitszeit", HttpMethod.POST, rumpf, String.class).getStatusCode())
        .isEqualTo(HttpStatus.CREATED);
  }

  /**
   * Legt einen Entwurf an.
   *
   * @param monat der Monat als {@code JJJJ-MM}, oder {@code null} fuer einen Aufruf <b>ohne</b>
   *     Rumpf — genau die Form, die die Oberflaeche heute schickt
   */
  private RechnungResponse entwurf(final @Nullable String monat) {
    final ResponseEntity<RechnungResponse> antwort =
        ruf(
            "/api/angebote/" + angebotId + "/rechnungen",
            HttpMethod.POST,
            monat == null ? null : Map.of("monat", monat),
            RechnungResponse.class);
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return Objects.requireNonNull(antwort.getBody());
  }

  /** Die Zeile des Entwurfs zu einer Angebotsposition; die Maske zeigt jede Position. */
  private static RechnungZeileResponse zeile(
      final RechnungResponse entwurf, final long angebotPositionId) {
    return entwurf.zeilen().stream()
        .filter(gefunden -> gefunden.angebotPositionId() == angebotPositionId)
        .findFirst()
        .orElseThrow();
  }

  @Test
  void anlegen_withAMonat_thenTheBuchbarePositionCarriesItsHoursAndTheFestpreisWhatIsOffen() {
    // Given — im Oktober stehen 10 Stunden auf „Konzeption" (Kriterium 9).
    erfasse(OKTOBERTAG, "08:00", "18:00");

    // When
    final RechnungResponse entwurf = entwurf(OKTOBER);

    // Then — die Stunden des Monats als Menge, der Leistungszeitraum der gewaehlte Monat
    // (Kriterium 10), und die Pauschale wie immer mit ihrer offenen Menge (E1).
    assertThat(entwurf.leistungszeitraum()).isEqualTo("Oktober 2026");
    assertThat(zeile(entwurf, konzeptionId).menge()).isEqualByComparingTo("10.00");
    assertThat(zeile(entwurf, schulungId).menge()).isEqualByComparingTo("1");
  }

  @Test
  void anlegen_withAMonat_thenTheMengeGoesBeyondWhatIsOffenAndTheAnswerShowsTheUeberschreitung() {
    // Given — das Beispiel aus #193: 10 Stunden im Oktober sind abgerechnet, im November stehen 12.
    erfasse(OKTOBERTAG, "08:00", "18:00");
    final RechnungResponse oktober = entwurf(OKTOBER);
    assertThat(zeile(oktober, konzeptionId).menge()).isEqualByComparingTo("10.00");
    erfasse(NOVEMBERTAG, "08:00", "20:00");

    // When
    final RechnungResponse november = entwurf(NOVEMBER);

    // Then — 12 statt der offenen 10 (Kriterium 11), und 2,00 zu viel auf 20 angebotene Stunden.
    assertThat(november.leistungszeitraum()).isEqualTo("November 2026");
    assertThat(zeile(november, konzeptionId))
        .satisfies(
            zeile -> assertThat(zeile.angeboten()).isEqualByComparingTo("20.00"),
            zeile -> assertThat(zeile.abgerechnet()).isEqualByComparingTo("10.00"),
            zeile -> assertThat(zeile.offen()).isEqualByComparingTo("10.00"),
            zeile -> assertThat(zeile.menge()).isEqualByComparingTo("12.00"),
            zeile -> assertThat(zeile.ueberschreitung()).isEqualByComparingTo("2.00"));
  }

  @Test
  void anlegen_withoutARumpf_thenTheEntwurfIsPrefilledWithWhatIsOffen() {
    // Given — der Weg ohne Rumpf bleibt gueltig (A9); erfasste Zeit aendert daran nichts.
    erfasse(NOVEMBERTAG, "08:00", "20:00");

    // When
    final RechnungResponse entwurf = entwurf(null);

    // Then
    assertThat(zeile(entwurf, konzeptionId).menge()).isEqualByComparingTo("20.00");
    assertThat(zeile(entwurf, schulungId).menge()).isEqualByComparingTo("1");
  }

  @Test
  void anlegen_withAMonatWithoutAnyHours_thenTheEntwurfIsPrefilledWithWhatIsOffen() {
    // Given — im September wurde nicht gearbeitet, im November schon (E7).
    erfasse(NOVEMBERTAG, "08:00", "20:00");

    // When
    final RechnungResponse entwurf = entwurf(SEPTEMBER);

    // Then — der Entwurf ist der ohne Monat, samt laufendem Monat im Leistungszeitraum.
    assertThat(entwurf.leistungszeitraum()).isEqualTo(laufenderMonat());
    assertThat(zeile(entwurf, konzeptionId).menge()).isEqualByComparingTo("20.00");
    assertThat(zeile(entwurf, schulungId).menge()).isEqualByComparingTo("1");
  }
}
