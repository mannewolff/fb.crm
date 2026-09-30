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
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.PasswordHasher;
import org.mwolff.fbcrm.auth.domain.Role;
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
 */
class RechnungseinstellungenIT extends AbstractIntegrationTest {

  private static final String PFAD = "/api/rechnung/einstellungen";
  private static final String MAIL = "manne@example.org";
  private static final String PASSWORT = "richtiges-passwort";
  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");

  private final TestRestTemplate rest;
  private final AccountRepository accounts;
  private final PasswordHasher hasher;
  private final JdbcTemplate jdbc;

  private HttpHeaders sitzung = new HttpHeaders();

  @Autowired
  RechnungseinstellungenIT(
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
  void stelleDenStandDerMigrationHerUndMeldeAn() {
    jdbc.execute("DELETE FROM rechnung_einstellungen");
    jdbc.execute("INSERT INTO rechnung_einstellungen DEFAULT VALUES");
    jdbc.execute("DELETE FROM rechnung_nummernkreis");
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
      final HttpMethod methode, final Object rumpf, final Class<T> typ, final HttpHeaders kopf) {
    return rest.exchange(PFAD, methode, new HttpEntity<>(rumpf, kopf), typ);
  }

  private RechnungseinstellungenResponse lies() {
    return Objects.requireNonNull(
        ruf(HttpMethod.GET, null, RechnungseinstellungenResponse.class, sitzung).getBody());
  }

  private ResponseEntity<String> schreibe(
      final String muster, final int nummer, final String steuersatz, final int ziel) {
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
        sitzung);
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
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    pruefe(lies(), "R{JJ}-{NNNN}", 4, "19.50", 14);
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
}
