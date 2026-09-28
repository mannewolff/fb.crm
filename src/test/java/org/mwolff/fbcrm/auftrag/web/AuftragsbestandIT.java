package org.mwolff.fbcrm.auftrag.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
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
import org.mwolff.fbcrm.auftrag.domain.Auftragsstatus;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.PasswordHasher;
import org.mwolff.fbcrm.auth.domain.Role;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.common.Einheit;
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
 * Der Auftragsbestand ueber HTTP, mit Sitzung und gegen eine echte PostgreSQL-Instanz (Kriterien
 * 12, 13).
 *
 * <p>Der Gegenstand ist das Zusammenspiel, das der Anwendungsfall-Test nicht zeigen kann: die
 * Abfrage der Kandidaten, das Nachladen der Positionen, das Zuordnen von Vorgang und Firma und die
 * beiden Kennzahlen — alles gegen den Bestand, nicht gegen ein Doppel. Dazu die beiden Wege, die
 * eine Zeile kommen und gehen lassen: das Setzen des Auftragsstatus (Issue #120) und das
 * Wiedereroeffnen des Vorgangs.
 *
 * <p>Der Nachweis der Zugangsregel steht hier und nicht nur in {@code AccessRuleIT}: Ein neuer Pfad
 * unter {@code /api} ist ohne Sitzung verschlossen, und das gehoert zu jedem neuen Weg dazu.
 */
class AuftragsbestandIT extends AbstractIntegrationTest {

  private static final String MAIL = "manne@example.org";
  private static final String PASSWORT = "richtiges-passwort";
  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");
  private static final Instant VERSANDZEITPUNKT = Instant.parse("2026-09-21T09:00:00Z");
  private static final Instant REAKTION = Instant.parse("2026-09-22T11:00:00Z");
  private static final LocalDate ANGEBOTSDATUM = LocalDate.of(2026, 9, 20);
  private static final LocalDate GUELTIG_BIS = LocalDate.of(2026, 10, 20);
  private static final BigDecimal NULL_EURO = new BigDecimal("0.00");

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

  private final TestRestTemplate rest;
  private final AngebotRepository angebote;
  private final AccountRepository accounts;
  private final PasswordHasher hasher;
  private final TransactionTemplate transaktion;
  private final JdbcTemplate jdbc;

  private HttpHeaders sitzung = new HttpHeaders();
  private long offenerVorgang;
  private long vorgangMitAbgeschlossenemAuftrag;
  private long abgeschlossenerVorgang;
  private long abzuschliessenderAuftrag;

  @Autowired
  AuftragsbestandIT(
      final TestRestTemplate rest,
      final AngebotRepository angebote,
      final AccountRepository accounts,
      final PasswordHasher hasher,
      final TransactionTemplate transaktion,
      final JdbcTemplate jdbc) {
    this.rest = rest;
    this.angebote = angebote;
    this.accounts = accounts;
    this.hasher = hasher;
    this.transaktion = transaktion;
    this.jdbc = jdbc;
  }

  @BeforeEach
  void leereDenBestandUndMeldeAn() {
    jdbc.execute(
        "TRUNCATE auftrag_position, auftrag, angebot_position, angebot, vorgang_eintrag, vorgang,"
            + " ansprechpartner, firma RESTART IDENTITY CASCADE");
    jdbc.execute("DELETE FROM auftrag_nummernkreis");
    jdbc.execute("TRUNCATE outbox_message, password_reset_token, account RESTART IDENTITY CASCADE");
    accounts.save(
        new Account(null, MAIL, "Manne", hasher.hash(PASSWORT), Role.ADMIN, 0, ANGELEGT, ANGELEGT));
    sitzung = angemeldeterKopf();

    final long firmaId = firma("Adler AG");
    offenerVorgang = vorgang(1L, "Website-Relaunch", firmaId);
    vorgangMitAbgeschlossenemAuftrag = vorgang(2L, "Schulung", firmaId);
    abgeschlossenerVorgang = vorgang(3L, "Altlast", firmaId);
    auftrag(offenerVorgang, "A-2026-001", "1000.00");
    abzuschliessenderAuftrag = auftrag(vorgangMitAbgeschlossenemAuftrag, "A-2026-002", "400.00");
    auftrag(abgeschlossenerVorgang, "A-2026-003", "5000.00");
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

  private long firma(final String name) {
    jdbc.update("INSERT INTO firma (name) VALUES (?)", name);
    return Objects.requireNonNull(
            jdbc.queryForObject("SELECT id FROM firma WHERE name = ?", Long.class, name))
        .longValue();
  }

  private long vorgang(final long nummer, final String titel, final long firmaId) {
    jdbc.update(
        "INSERT INTO vorgang (nummer, titel, firma_id, abgeschlossen) VALUES (?, ?, ?, false)",
        Long.valueOf(nummer),
        titel,
        Long.valueOf(firmaId));
    return Objects.requireNonNull(
            jdbc.queryForObject(
                "SELECT id FROM vorgang WHERE nummer = ?", Long.class, Long.valueOf(nummer)))
        .longValue();
  }

  /*
   * In einer eigenen Transaktion, wie in AuftragAnlegenIT: Das Schreiben eines Angebots loescht
   * zuerst seine Positionszeilen, und diese Massenloeschung braucht eine laufende Transaktion.
   */
  private long angenommenesAngebot(
      final long vorgangId, final String nummer, final String einzelpreis) {
    final Angebot entwurf =
        new Angebot(
            null,
            vorgangId,
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
            List.of(
                new Angebotsposition(
                    "Leistung",
                    Abrechnungsmodus.FESTPREIS,
                    BigDecimal.ONE,
                    Einheit.PAUSCHAL,
                    new BigDecimal(einzelpreis))),
            ANGELEGT,
            ANGELEGT);
    return Objects.requireNonNull(
            transaktion.execute(
                status ->
                    angebote.save(
                        entwurf
                            .versendet(
                                nummer,
                                EMPFAENGER,
                                ABSENDER,
                                "angebot/" + nummer + "/beleg.pdf",
                                VERSANDZEITPUNKT)
                            .angenommen(REAKTION))))
        .requireId();
  }

  /** Der Auftrag entsteht ueber den Weg aus Issue #119 — wie in der Anwendung. */
  private long auftrag(final long vorgangId, final String angebotsnummer, final String preis) {
    final long angebotId = angenommenesAngebot(vorgangId, angebotsnummer, preis);
    final Map<String, Object> position = new LinkedHashMap<>();
    position.put("platz", Integer.valueOf(1));
    position.put("menge", "1.00");
    final Map<String, Object> rumpf = new LinkedHashMap<>();
    rumpf.put("positionen", List.of(position));
    final ResponseEntity<AuftragResponse> antwort =
        ruf(
            "/api/angebote/" + angebotId + "/auftrag",
            HttpMethod.POST,
            rumpf,
            AuftragResponse.class);
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return Objects.requireNonNull(antwort.getBody()).id();
  }

  private <T> ResponseEntity<T> ruf(
      final String pfad, final HttpMethod methode, final Object rumpf, final Class<T> typ) {
    return rest.exchange(pfad, methode, new HttpEntity<>(rumpf, sitzung), typ);
  }

  private AuftragsbestandResponse bestand() {
    return Objects.requireNonNull(
        rest.exchange(
                "/api/auftragsbestand",
                HttpMethod.GET,
                new HttpEntity<>(sitzung),
                AuftragsbestandResponse.class)
            .getBody());
  }

  private void schliesseDenAuftragAb(final long auftragId) {
    final Map<String, Object> rumpf = new LinkedHashMap<>();
    rumpf.put("auftragDatum", "2026-09-28");
    rumpf.put("status", Auftragsstatus.ABGESCHLOSSEN.name());
    final ResponseEntity<AuftragResponse> antwort =
        ruf("/api/auftraege/" + auftragId, HttpMethod.PUT, rumpf, AuftragResponse.class);
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  private void schliesseDenVorgangAb(final long vorgangId) {
    final ResponseEntity<Void> antwort =
        ruf("/api/vorgaenge/" + vorgangId + "/abschliessen", HttpMethod.POST, null, Void.class);
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  void auftragsbestand_thenCountsOnlyTheUnfinishedOrdersOfOpenVorgaenge() {
    // Given — F7 und Kriterium 12: einer abgeschlossen, einer am abgeschlossenen Vorgang.
    schliesseDenAuftragAb(abzuschliessenderAuftrag);
    schliesseDenVorgangAb(abgeschlossenerVorgang);

    // When
    final AuftragsbestandResponse antwort = bestand();

    // Then
    assertThat(antwort.zeilen())
        .extracting(
            AuftragsbestandZeileResponse::vorgangTitel,
            AuftragsbestandZeileResponse::firma,
            AuftragsbestandZeileResponse::status)
        .containsExactly(tuple("Website-Relaunch", "Adler AG", Auftragsstatus.OFFEN));
    assertThat(antwort.beauftragt()).isEqualByComparingTo("1000.00");
    assertThat(antwort.nochOffen()).isEqualByComparingTo("1000.00");
  }

  @Test
  void auftragsbestand_thenTheAmountsAreTheSumsOfTheDomainAndNothingIsBilledYet() {
    // Given — Kriterium 13, Plan E12: bis Idee #7 sind beide Kennzahlen gleich.
    schliesseDenVorgangAb(abgeschlossenerVorgang);

    // When
    final AuftragsbestandResponse antwort = bestand();

    // Then — 1.000,00 € und 400,00 €, der groessere Rest oben.
    assertThat(antwort.zeilen())
        .extracting(
            AuftragsbestandZeileResponse::auftragssumme,
            AuftragsbestandZeileResponse::abgerechnet,
            AuftragsbestandZeileResponse::offenerRest)
        .containsExactly(
            tuple(new BigDecimal("1000.00"), NULL_EURO, new BigDecimal("1000.00")),
            tuple(new BigDecimal("400.00"), NULL_EURO, new BigDecimal("400.00")));
    assertThat(antwort.beauftragt()).isEqualByComparingTo("1400.00");
    assertThat(antwort.nochOffen()).isEqualByComparingTo("1400.00");
  }

  @Test
  void auftragsbestand_givenAReopenedVorgang_thenItsOrderIsBackAgain() {
    // Given — Kriterium 12: der abgeschlossene Vorgang haelt seinen Auftrag nur so lange draussen.
    schliesseDenAuftragAb(abzuschliessenderAuftrag);
    schliesseDenVorgangAb(abgeschlossenerVorgang);
    assertThat(bestand().zeilen()).hasSize(1);

    // When
    ruf(
        "/api/vorgaenge/" + abgeschlossenerVorgang + "/wiedereroeffnen",
        HttpMethod.POST,
        null,
        Void.class);

    // Then — der 5.000-€-Auftrag steht wieder oben, der offene darunter.
    final AuftragsbestandResponse antwort = bestand();
    assertThat(antwort.zeilen())
        .extracting(AuftragsbestandZeileResponse::vorgangTitel)
        .containsExactly("Altlast", "Website-Relaunch");
    assertThat(antwort.beauftragt()).isEqualByComparingTo("6000.00");
  }

  @Test
  void auftragsbestand_whenAnOrderIsFinished_thenItLeavesTheBestandAndBothTotalsDrop() {
    // Given — F7, ueber den Weg aus Issue #120.
    schliesseDenVorgangAb(abgeschlossenerVorgang);
    assertThat(bestand().zeilen()).hasSize(2);

    // When
    schliesseDenAuftragAb(abzuschliessenderAuftrag);

    // Then
    final AuftragsbestandResponse antwort = bestand();
    assertThat(antwort.zeilen()).hasSize(1);
    assertThat(antwort.beauftragt()).isEqualByComparingTo("1000.00");
    assertThat(antwort.nochOffen()).isEqualByComparingTo("1000.00");
  }

  @Test
  void auftragsbestand_thenCarriesTheMoneyAsADecimalText() {
    // Given — eine „0" ohne Cent waere eine andere Zahl auf dem Bildschirm.
    schliesseDenAuftragAb(abzuschliessenderAuftrag);
    schliesseDenVorgangAb(abgeschlossenerVorgang);

    // When
    final ResponseEntity<String> antwort =
        rest.exchange(
            "/api/auftragsbestand", HttpMethod.GET, new HttpEntity<>(sitzung), String.class);

    // Then
    assertThat(antwort.getBody())
        .contains("\"abgerechnet\":0.00")
        .contains("\"beauftragt\":1000.00");
  }

  @Test
  void auftragsbestand_givenNoSession_thenAnswersUnauthorized() {
    // When — ein neuer Pfad unter /api ist ohne Sitzung verschlossen.
    final ResponseEntity<String> antwort = rest.getForEntity("/api/auftragsbestand", String.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
