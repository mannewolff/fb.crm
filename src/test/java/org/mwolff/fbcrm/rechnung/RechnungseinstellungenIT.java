package org.mwolff.fbcrm.rechnung;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
import org.mwolff.fbcrm.rechnung.domain.Nummernmuster;
import org.mwolff.fbcrm.rechnung.web.RechnungseinstellungenResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Die beiden Wege der Rechnungseinstellungen ueber HTTP, mit Sitzung und gegen eine echte
 * PostgreSQL-Instanz (fachliche Quelle #159, Kriterien 5, 7, 8, 11 und 12).
 *
 * <p>Hier stehen die Zusagen, die am Zusammenspiel von Schnittstelle, Transaktion und Schema
 * haengen: Eine frische Instanz liefert die Vorbelegungen der Migration, ein {@code PUT} steht
 * anschliessend wirklich im Bestand, ein abgewiesenes Muster laesst die gespeicherten Werte
 * unberuehrt, und ohne Sitzung antworten beide Wege 401 — die Wege fallen unter das bestehende
 * {@code /api/**} fuer angemeldete Benutzer (Kriterium 12), ohne eine eigene Regel in {@code
 * SecurityConfig}.
 *
 * <p>Die eine Zeile wird vor jeder Methode auf die Vorbelegung zurueckgesetzt; die Datenbank der
 * Suite ist geteilt, und ohne das Zuruecksetzen lieferte „frische Instanz" nach dem Speichern-Test
 * die gespeicherten Werte.
 *
 * <p>Dazu die Pruefung der naechsten Nummer gegen den Bestand (#160, Kriterium 19; Plan #169, E16).
 * Sie ist nur hier zu sehen: Ob eine Nummer vergeben ist, weiss die Datenbank, und abgewiesen wird
 * so, dass anschliessend wirklich nichts gespeichert ist.
 */
class RechnungseinstellungenIT extends AbstractIntegrationTest {

  private static final String PFAD = "/api/rechnung/einstellungen";
  private static final String MAIL = "manne@example.org";
  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");

  private final TestRestTemplate rest;
  private final AccountRepository accounts;
  private final SecretHasher hasher;
  private final JdbcTemplate jdbc;

  private HttpHeaders sitzung = new HttpHeaders();

  @Autowired
  RechnungseinstellungenIT(
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
  void stelleDenStandDerMigrationHerUndMeldeAn() {
    jdbc.execute(
        "TRUNCATE rechnung_position, rechnung, angebot_position, angebot, ansprechpartner, firma"
            + " RESTART IDENTITY CASCADE");
    jdbc.execute("DELETE FROM rechnung_einstellungen");
    jdbc.execute("INSERT INTO rechnung_einstellungen DEFAULT VALUES");
    jdbc.execute("DELETE FROM rechnung_nummernkreis");
    leereKonten(jdbc);
    accounts.save(Konten.neu(MAIL, ANGELEGT));
    sitzung = Anmeldung.perCode(rest, jdbc, hasher, MAIL);
  }

  private <T> ResponseEntity<T> ruf(
      final HttpMethod methode, final Object rumpf, final Class<T> typ, final HttpHeaders kopf) {
    return rest.exchange(PFAD, methode, new HttpEntity<>(rumpf, kopf), typ);
  }

  private RechnungseinstellungenResponse lies() {
    return Objects.requireNonNull(
        ruf(HttpMethod.GET, null, RechnungseinstellungenResponse.class, sitzung).getBody());
  }

  /** Schreibt auf dem aktuellen Stand der Zeile, wie die Maske (Plan #415, E2). */
  private ResponseEntity<String> schreibe(
      final String muster, final int nummer, final String steuersatz, final int ziel) {
    return schreibe(
        muster,
        nummer,
        steuersatz,
        ziel,
        Stand.aktuell(sitzung, jdbc, "rechnung_einstellungen", 1L));
  }

  private ResponseEntity<String> schreibe(
      final String muster,
      final int nummer,
      final String steuersatz,
      final int ziel,
      final HttpHeaders kopf) {
    return ruf(
        HttpMethod.PUT,
        Map.of(
            "nummerMuster",
            muster,
            "naechsteNummer",
            nummer,
            "steuersatz",
            steuersatz,
            "zahlungszielTage",
            ziel),
        String.class,
        kopf);
  }

  /** Die Kopfzeilen der Sitzung samt {@code If-Match} auf einen frei gewaehlten Stand. */
  private HttpHeaders aufStand(final long version) {
    final HttpHeaders kopf = new HttpHeaders();
    kopf.putAll(sitzung);
    kopf.setIfMatch("\"" + version + "\"");
    return kopf;
  }

  private static void pruefe(
      final RechnungseinstellungenResponse gelesen,
      final String muster,
      final int nummer,
      final String steuersatz,
      final int ziel) {
    assertThat(gelesen.nummerMuster()).isEqualTo(muster);
    assertThat(gelesen.naechsteNummer()).isEqualTo(nummer);
    assertThat(gelesen.steuersatz()).isEqualByComparingTo(new BigDecimal(steuersatz));
    assertThat(gelesen.zahlungszielTage()).isEqualTo(ziel);
  }

  /**
   * Legt eine gestellte Rechnung an, die genau diese Nummer traegt.
   *
   * <p>Direkt in der Datenbank und nicht ueber den Weg des Stellens: Hier geht es nur darum,
   * <b>dass</b> eine Nummer vergeben ist. Wie sie vergeben wird, belegt {@code RechnungNummerIT};
   * den Weg hier noch einmal zu fahren, brauchte Angebot, Positionen und eigene Angaben und
   * erzeugte ein Dokument im Objektspeicher, das niemand liest.
   */
  private void rechnungMitNummer(final String nummer) {
    final Long firmaId =
        jdbc.queryForObject(
            "INSERT INTO firma (name) VALUES ('Adler AG') RETURNING id", Long.class);
    final Long angebotId =
        jdbc.queryForObject(
            """
            INSERT INTO angebot (firma_id, status, angebot_datum)
            VALUES (?, 'BESTELLT', CURRENT_DATE) RETURNING id
            """,
            Long.class,
            firmaId);
    jdbc.update(
        """
        INSERT INTO rechnung (angebot_id, zustand, rechnung_datum, nummer, steuersatz,
                              zahlungsziel_tage, gestellt_am, empfaenger_firma, absender_name)
        VALUES (?, 'GESTELLT', CURRENT_DATE, ?, 19.00, 10, now(), 'Adler AG', 'Manfred Wolff')
        """,
        angebotId,
        nummer);
  }

  /** Die Rechnungsnummer, die das Muster {@code {NNNN}-{JJJJ}} zur laufenden Nummer bildet. */
  private static String nummerImLaufendenJahr(final int laufend) {
    return "%04d-%d"
        .formatted(
            Integer.valueOf(laufend),
            Integer.valueOf(LocalDate.now(Geschaeftszone.ZONE).getYear()));
  }

  /** Der Zaehlerstand eines Zaehlerjahrs, direkt aus der Tabelle; ohne Zeile {@code null}. */
  private @Nullable Integer zaehler(final int zaehlerjahr) {
    final List<Integer> stand =
        jdbc.queryForList(
            "SELECT naechste_nummer FROM rechnung_nummernkreis WHERE jahr = ?",
            Integer.class,
            zaehlerjahr);
    return stand.isEmpty() ? null : stand.get(0);
  }

  @Test
  void lesen_givenTheFreshInstance_thenAnswersWithTheDefaults() {
    // When / Then — Kriterium 11: die Vorbelegungen sind von Anfang an da.
    pruefe(lies(), "{NNNN}-{JJJJ}", 1, "19", 10);
  }

  @Test
  void pflegen_thenTheNextReadAnswersWithTheStoredValues() {
    // When — Kriterium 7.
    final ResponseEntity<String> antwort = schreibe("R{JJ}-{NNNN}", 4, "19.50", 14);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
    pruefe(lies(), "R{JJ}-{NNNN}", 4, "19.50", 14);
  }

  @Test
  void pflegen_thenAnswersWithTheNewStandAndARaisedVersion() {
    // Given
    final long vorher = lies().version();

    // When — Plan #415, E2: die Antwort traegt den Stand, den die Maske beim naechsten Speichern
    // vorlegt.
    final ResponseEntity<RechnungseinstellungenResponse> antwort =
        ruf(
            HttpMethod.PUT,
            Map.of(
                "nummerMuster",
                "R{JJ}-{NNNN}",
                "naechsteNummer",
                4,
                "steuersatz",
                "19.50",
                "zahlungszielTage",
                14),
            RechnungseinstellungenResponse.class,
            aufStand(vorher));

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
    final RechnungseinstellungenResponse neu = Objects.requireNonNull(antwort.getBody());
    pruefe(neu, "R{JJ}-{NNNN}", 4, "19.50", 14);
    assertThat(neu.version()).isEqualTo(vorher + 1);
    assertThat(lies()).isEqualTo(neu);
  }

  @Test
  void pflegen_withoutIfMatch_thenAnswers428AndLeavesTheStoredValues() {
    // When — Plan #415, E3: ohne vorgelegten Stand wird nicht geschrieben.
    final ResponseEntity<String> antwort = schreibe("R{JJ}-{NNNN}", 4, "19.50", 14, sitzung);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.PRECONDITION_REQUIRED);
    pruefe(lies(), "{NNNN}-{JJJJ}", 1, "19", 10);
    assertThat(zaehler(LocalDate.now(Geschaeftszone.ZONE).getYear())).isNull();
  }

  @Test
  void pflegen_onAnOutdatedStand_thenAnswers409AndKeepsTheFirstChange() {
    // Given — #411, Kriterium 1: zwei Masken auf demselben Stand; die erste speichert.
    final long geoeffnet = lies().version();
    assertThat(schreibe("A-{NNNN}", 3, "19.00", 10, aufStand(geoeffnet)).getStatusCode())
        .isEqualTo(HttpStatus.OK);

    // When — die zweite speichert auf dem Stand, den sie geoeffnet hat.
    final ResponseEntity<String> antwort = schreibe("B-{NNNN}", 9, "7.00", 0, aufStand(geoeffnet));

    // Then — abgewiesen mit der Erweiterung konflikt, und der Stand der ersten bleibt.
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(antwort.getBody()).contains("\"konflikt\"").contains("veraltet");
    pruefe(lies(), "A-{NNNN}", 3, "19.00", 10);
  }

  @Test
  void lesen_givenNoSession_thenAnswersWithUnauthorized() {
    // When / Then — Kriterium 12, ohne eigene Regel in SecurityConfig.
    assertThat(ruf(HttpMethod.GET, null, String.class, new HttpHeaders()).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void pflegen_givenNoSession_thenAnswersWithUnauthorized() {
    // When / Then — Kriterium 12.
    assertThat(
            ruf(
                    HttpMethod.PUT,
                    Map.of(
                        "nummerMuster",
                        "{NNNN}",
                        "naechsteNummer",
                        1,
                        "steuersatz",
                        "19.00",
                        "zahlungszielTage",
                        10),
                    String.class,
                    new HttpHeaders())
                .getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void pflegen_givenAPatternWithoutTheNumberPlaceholder_thenRefusesAndLeavesTheStoredValues() {
    // When — Kriterium 8.
    final ResponseEntity<String> antwort = schreibe("nur-text-{JJJJ}", 4, "19.50", 14);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(antwort.getBody()).contains("fieldErrors").contains("nummerMuster");
    pruefe(lies(), "{NNNN}-{JJJJ}", 1, "19", 10);
  }

  @Test
  void pflegen_givenAPatternWithoutAYear_thenTheNumberLandsInTheYearlessCounter() {
    // When — Kriterium 15: eine selbst gesetzte Nummer gilt.
    schreibe("{NNNN}", 7, "19.00", 10);

    // Then — der GET liefert sie wieder, und im Bestand steht sie unter dem Zaehlerjahr 0.
    assertThat(lies().naechsteNummer()).isEqualTo(7);
    assertThat(zaehler(Nummernmuster.OHNE_JAHR)).isEqualTo(7);
  }

  @Test
  void pflegen_givenAPatternWithAYear_thenTheNumberLandsInTheCounterOfTheCurrentYear() {
    // Given — Kriterium 16: mit Jahres-Platzhalter zaehlt jedes Jahr fuer sich.
    final int jahr = LocalDate.now(Geschaeftszone.ZONE).getYear();

    // When
    schreibe("{NNNN}-{JJJJ}", 4, "19.00", 10);

    // Then
    assertThat(lies().naechsteNummer()).isEqualTo(4);
    assertThat(zaehler(jahr)).isEqualTo(4);
    assertThat(zaehler(Nummernmuster.OHNE_JAHR)).isNull();
  }

  @Test
  void pflegen_givenANummerAnExistingRechnungCarries_thenRefusesAndLeavesTheStoredValues() {
    // Given — Kriterium 19: die 0001 des laufenden Jahres steht schon auf einem Beleg.
    rechnungMitNummer(nummerImLaufendenJahr(1));

    // When
    final ResponseEntity<String> antwort = schreibe("{NNNN}-{JJJJ}", 1, "19.50", 14);

    // Then — abgewiesen am Feld, und die gespeicherten Werte sind unveraendert.
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(antwort.getBody()).contains("\"fieldErrors\"").contains("\"naechsteNummer\"");
    pruefe(lies(), "{NNNN}-{JJJJ}", 1, "19", 10);
  }

  @Test
  void pflegen_givenTheNextFreeNummer_thenStores() {
    // Given — dieselbe Lage, aber die 0002 traegt niemand.
    rechnungMitNummer(nummerImLaufendenJahr(1));

    // When / Then
    assertThat(schreibe("{NNNN}-{JJJJ}", 2, "19.50", 14).getStatusCode()).isEqualTo(HttpStatus.OK);
    pruefe(lies(), "{NNNN}-{JJJJ}", 2, "19.50", 14);
  }

  @Test
  void pflegen_givenTheSameNummerUnderAnotherPattern_thenStores() {
    // Given — geprueft wird die Rechnungsnummer und nicht die laufende Zahl: Mit einem anderen
    // Muster ergibt dieselbe 1 eine Nummer, die niemand traegt.
    rechnungMitNummer(nummerImLaufendenJahr(1));

    // When / Then
    assertThat(schreibe("R{JJ}-{NNNN}", 1, "19.50", 14).getStatusCode()).isEqualTo(HttpStatus.OK);
    pruefe(lies(), "R{JJ}-{NNNN}", 1, "19.50", 14);
  }
}
