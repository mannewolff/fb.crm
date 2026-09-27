package org.mwolff.fbcrm.angebot.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.angebot.domain.Abrechnungsmodus;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.angebot.domain.Angebotszustand;
import org.mwolff.fbcrm.angebot.domain.Belegabsender;
import org.mwolff.fbcrm.angebot.domain.Belegempfaenger;
import org.mwolff.fbcrm.angebot.domain.Einheit;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.PasswordHasher;
import org.mwolff.fbcrm.auth.domain.Role;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.common.Geschaeftszone;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
import org.mwolff.fbcrm.vorgang.domain.Vorgang;
import org.mwolff.fbcrm.vorgang.domain.VorgangRepository;
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
 * Die Pipeline ueber HTTP, mit Sitzung und gegen eine echte PostgreSQL-Instanz (Kriterien 23, 24).
 *
 * <p>Der Gegenstand ist das Zusammenspiel, das der Anwendungsfall-Test nicht zeigen kann: die
 * Abfrage der Kandidaten, das Nachladen der Positionen, das Zuordnen von Vorgang und Firma und die
 * Reihenfolge — alles gegen den Bestand, nicht gegen ein Doppel.
 *
 * <p>Die Gueltigkeiten liegen gegen den <b>heutigen</b> Tag der Geschaeftszone und nicht gegen ein
 * festes Datum: Der Lauf richtet sich nach der Uhr der Anwendung, und ein festes Datum machte den
 * Test irgendwann falsch (E4, E12).
 *
 * <p>Der Nachweis der Zugangsregel steht hier und nicht nur in {@code AccessRuleIT}: Ein neuer Pfad
 * unter {@code /api} ist ohne Sitzung verschlossen, und das gehoert zu jedem neuen Weg dazu.
 */
class PipelineIT extends AbstractIntegrationTest {

  private static final String MAIL = "manne@example.org";
  private static final String PASSWORT = "richtiges-passwort";
  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");

  private final TestRestTemplate rest;
  private final AccountRepository accounts;
  private final PasswordHasher hasher;
  private final FirmaRepository firmen;
  private final VorgangRepository vorgaenge;
  private final AngebotRepository angebote;
  private final TransactionTemplate transaktion;
  private final JdbcTemplate jdbc;

  private HttpHeaders sitzung = new HttpHeaders();

  /*
   * Der heutige Tag der Geschaeftszone, je Test frisch geholt: Als Konstante stuende dort der Tag
   * des Klassenladens, und ein Lauf ueber Mitternacht rechnete gegen das falsche Datum (E12).
   */
  private LocalDate heute = LocalDate.now(Geschaeftszone.ZONE);

  @Autowired
  PipelineIT(
      final TestRestTemplate rest,
      final AccountRepository accounts,
      final PasswordHasher hasher,
      final FirmaRepository firmen,
      final VorgangRepository vorgaenge,
      final AngebotRepository angebote,
      final TransactionTemplate transaktion,
      final JdbcTemplate jdbc) {
    this.rest = rest;
    this.accounts = accounts;
    this.hasher = hasher;
    this.firmen = firmen;
    this.vorgaenge = vorgaenge;
    this.angebote = angebote;
    this.transaktion = transaktion;
    this.jdbc = jdbc;
  }

  @BeforeEach
  void leereDenBestandUndMeldeAn() {
    jdbc.execute(
        "TRUNCATE angebot_position, angebot, vorgang_eintrag, vorgang, ansprechpartner, firma"
            + " RESTART IDENTITY CASCADE");
    jdbc.execute("TRUNCATE outbox_message, password_reset_token, account RESTART IDENTITY CASCADE");
    accounts.save(
        new Account(null, MAIL, "Manne", hasher.hash(PASSWORT), Role.ADMIN, 0, ANGELEGT, ANGELEGT));
    final ResponseEntity<String> anmeldung =
        rest.postForEntity(
            "/api/auth/login", Map.of("email", MAIL, "password", PASSWORT), String.class);
    final String gesetzt = String.valueOf(anmeldung.getHeaders().getFirst(HttpHeaders.SET_COOKIE));
    sitzung = new HttpHeaders();
    sitzung.add(HttpHeaders.COOKIE, gesetzt.substring(0, gesetzt.indexOf(';')));
    heute = LocalDate.now(Geschaeftszone.ZONE);
  }

  private long firma(final String name) {
    return firmen
        .save(
            new Firma(
                null,
                name,
                new Anschrift("Am Wall 1", "28195", "Bremen", "Deutschland"),
                null,
                null,
                true,
                ANGELEGT,
                ANGELEGT))
        .requireId();
  }

  private long vorgang(
      final long nummer,
      final String titel,
      final long firmaId,
      final Integer wahrscheinlichkeit,
      final LocalDate entscheidungErwartetAm,
      final boolean abgeschlossen) {
    return vorgaenge
        .save(
            new Vorgang(
                null,
                nummer,
                titel,
                firmaId,
                null,
                wahrscheinlichkeit,
                entscheidungErwartetAm,
                abgeschlossen,
                ANGELEGT,
                ANGELEGT))
        .requireId();
  }

  /*
   * In einer eigenen Transaktion, wie in PhaseAbleitungIT: Das Schreiben eines Angebots loescht
   * zuerst seine Positionszeilen, und diese Massenloeschung braucht eine laufende Transaktion.
   */
  private long versendetesAngebot(
      final long vorgangId,
      final String nummer,
      final String einzelpreis,
      final LocalDate gueltigBis) {
    return Objects.requireNonNull(
            transaktion.execute(
                status ->
                    angebote.save(
                        new Angebot(
                            null,
                            vorgangId,
                            nummer,
                            Angebotszustand.VERSENDET,
                            heute.minusDays(1),
                            gueltigBis,
                            "Neugestaltung der Website",
                            "Zahlbar innerhalb von 14 Tagen ohne Abzug.",
                            ANGELEGT,
                            null,
                            "angebot/" + nummer + "/beleg.pdf",
                            new Belegempfaenger(
                                "Adler AG",
                                new Anschrift("Am Wall 1", "28195", "Bremen", "Deutschland"),
                                null),
                            new Belegabsender(
                                "Manfred Wolff",
                                new Anschrift("Am Deich 2", "28199", "Bremen", "Deutschland"),
                                null,
                                null,
                                null,
                                null,
                                null),
                            List.of(
                                new Angebotsposition(
                                    "Leistung",
                                    Abrechnungsmodus.FESTPREIS,
                                    BigDecimal.ONE,
                                    Einheit.PAUSCHAL,
                                    new BigDecimal(einzelpreis))),
                            ANGELEGT,
                            ANGELEGT))))
        .requireId();
  }

  private PipelineResponse pipeline() {
    return Objects.requireNonNull(
        rest.exchange(
                "/api/pipeline", HttpMethod.GET, new HttpEntity<>(sitzung), PipelineResponse.class)
            .getBody());
  }

  @Test
  void pipeline_thenCountsOnlyTheOpenOffersAndSortsThemByTheExpectedDecision() {
    // Given — zwei offene Vorgaenge, einer ohne Einschaetzung, dazu ein abgeschlossener.
    final long firmaId = firma("Adler AG");
    final long spaeter = vorgang(1L, "Website-Relaunch", firmaId, 60, heute.plusDays(20), false);
    final long frueher = vorgang(2L, "Schulung", firmaId, null, heute.plusDays(5), false);
    final long geschlossen = vorgang(3L, "Altlast", firmaId, 100, heute.plusDays(1), true);
    versendetesAngebot(spaeter, "A-2026-001", "1000.00", heute.plusDays(10));
    versendetesAngebot(frueher, "A-2026-002", "1000.00", heute.plusDays(10));
    versendetesAngebot(geschlossen, "A-2026-003", "5000.00", heute.plusDays(10));

    // When
    final PipelineResponse antwort = pipeline();

    // Then — Kriterium 24: die Summen zaehlen den abgeschlossenen Vorgang nicht mit.
    assertThat(antwort.summe()).isEqualByComparingTo("2000.00");
    assertThat(antwort.gewichteteSumme()).isEqualByComparingTo("1100.00");
    assertThat(antwort.zeilen())
        .extracting(
            PipelineZeileResponse::nummer,
            PipelineZeileResponse::vorgangTitel,
            PipelineZeileResponse::firma,
            PipelineZeileResponse::wahrscheinlichkeit,
            PipelineZeileResponse::gewichteteSumme)
        .containsExactly(
            tuple("A-2026-002", "Schulung", "Adler AG", null, new BigDecimal("500.00")),
            tuple(
                "A-2026-001",
                "Website-Relaunch",
                "Adler AG",
                Integer.valueOf(60),
                new BigDecimal("600.00")));
  }

  @Test
  void pipeline_givenAnExpiredOffer_thenLeavesItOutOfRowsAndTotals() {
    // Given — E4: die Gueltigkeit von gestern macht das Angebot abgelaufen, der letzte Tag zaehlt.
    final long firmaId = firma("Adler AG");
    final long offen = vorgang(1L, "Website-Relaunch", firmaId, 50, heute.plusDays(5), false);
    final long abgelaufen = vorgang(2L, "Schulung", firmaId, 50, heute.plusDays(5), false);
    versendetesAngebot(offen, "A-2026-001", "1000.00", heute);
    versendetesAngebot(abgelaufen, "A-2026-002", "1000.00", heute.minusDays(1));

    // When
    final PipelineResponse antwort = pipeline();

    // Then
    assertThat(antwort.zeilen())
        .extracting(PipelineZeileResponse::nummer)
        .containsExactly("A-2026-001");
    assertThat(antwort.summe()).isEqualByComparingTo("1000.00");
    assertThat(antwort.gewichteteSumme()).isEqualByComparingTo("500.00");
  }

  @Test
  void pipeline_givenNoOpenOffer_thenTwoZeroTotalsAndNoRow() {
    // When — daran erkennt die Oberflaeche „keine offene Chance".
    final PipelineResponse antwort = pipeline();

    // Then
    assertThat(antwort.zeilen()).isEmpty();
    assertThat(antwort.summe()).isEqualByComparingTo("0.00");
    assertThat(antwort.gewichteteSumme()).isEqualByComparingTo("0.00");
  }

  @Test
  void pipeline_thenCarriesTheExpectedDecisionAsAnIsoDate() {
    // Given — die Oberflaeche liest ein Datum und kein Feld-Array.
    final long firmaId = firma("Adler AG");
    final long vorgangId = vorgang(1L, "Website-Relaunch", firmaId, 60, heute.plusDays(5), false);
    versendetesAngebot(vorgangId, "A-2026-001", "1000.00", heute.plusDays(10));

    // When
    final ResponseEntity<String> antwort =
        rest.exchange("/api/pipeline", HttpMethod.GET, new HttpEntity<>(sitzung), String.class);

    // Then
    assertThat(antwort.getBody())
        .contains("\"entscheidungErwartetAm\":\"" + heute.plusDays(5) + "\"");
  }

  @Test
  void pipeline_givenNoSession_thenAnswersUnauthorized() {
    // When — ein neuer Pfad unter /api ist ohne Sitzung verschlossen.
    final ResponseEntity<String> antwort = rest.getForEntity("/api/pipeline", String.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
