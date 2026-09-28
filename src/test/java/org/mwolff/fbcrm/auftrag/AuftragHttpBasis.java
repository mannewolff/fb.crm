package org.mwolff.fbcrm.auftrag;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.angebot.domain.Angebotszustand;
import org.mwolff.fbcrm.angebot.domain.Belegabsender;
import org.mwolff.fbcrm.angebot.domain.Belegempfaenger;
import org.mwolff.fbcrm.auftrag.web.AuftragResponse;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.PasswordHasher;
import org.mwolff.fbcrm.auth.domain.Role;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.common.Einheit;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Das gemeinsame Bett der beiden Schreibwege-{@code IT} des Auftrags: Pflegen und Loeschen.
 *
 * <p>Beide brauchen denselben Aufbau — eine Firma, einen offenen Vorgang, zwei angenommene
 * Angebote, ein angemeldetes Konto — und beide legen ihren Auftrag ueber denselben HTTP-Weg an, den
 * {@code AuftragAnlegenIT} prueft. Zweimal dieselben hundertzwanzig Zeilen abzuschreiben hiesse,
 * sie kuenftig zweimal nachziehen zu muessen; dieselbe Abwaegung wie bei {@code
 * auftrag.application.Auftragsdoppel}.
 *
 * <p>Kein {@code IT} im Namen: Failsafe sammelt {@code **}{@code /*IT.java} ein, und dieses Bett
 * ist kein Test.
 */
abstract class AuftragHttpBasis extends AbstractIntegrationTest {

  static final String MAIL = "manne@example.org";
  static final String PASSWORT = "richtiges-passwort";
  static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");
  static final Instant VERSANDZEITPUNKT = Instant.parse("2026-09-21T09:00:00Z");
  static final Instant REAKTION = Instant.parse("2026-09-22T11:00:00Z");
  static final LocalDate ANGEBOTSDATUM = LocalDate.of(2026, 9, 20);
  static final LocalDate GUELTIG_BIS = LocalDate.of(2026, 10, 20);
  static final String PDF_SCHLUESSEL = "angebot/1/6f1c9a.pdf";

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

  protected final TestRestTemplate rest;
  protected final JdbcTemplate jdbc;

  private final AngebotRepository angebote;
  private final AccountRepository accounts;
  private final PasswordHasher hasher;
  private final TransactionTemplate transaktion;

  private HttpHeaders sitzung = new HttpHeaders();

  protected long vorgangId;
  protected long angebotId;
  protected long zweitesAngebotId;

  protected AuftragHttpBasis(
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
    jdbc.update("INSERT INTO firma (name) VALUES ('Adler AG')");
    final long firmaId =
        Objects.requireNonNull(
                jdbc.queryForObject("SELECT id FROM firma WHERE name = 'Adler AG'", Long.class))
            .longValue();
    vorgangId = vorgang(firmaId);
    angebotId = angenommenesAngebot(vorgangId, "A-2026-001");
    zweitesAngebotId = angenommenesAngebot(vorgangId, "A-2026-002");
    jdbc.execute("TRUNCATE outbox_message, password_reset_token, account RESTART IDENTITY CASCADE");
    accounts.save(
        new Account(null, MAIL, "Manne", hasher.hash(PASSWORT), Role.ADMIN, 0, ANGELEGT, ANGELEGT));
    sitzung = angemeldeterKopf();
  }

  private long vorgang(final long firmaId) {
    jdbc.update(
        "INSERT INTO vorgang (nummer, titel, firma_id, abgeschlossen) VALUES (1, ?, ?, false)",
        "Website-Relaunch",
        Long.valueOf(firmaId));
    return Objects.requireNonNull(
            jdbc.queryForObject("SELECT id FROM vorgang WHERE nummer = 1", Long.class))
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
            List.of(KONZEPTION),
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

  /** Ein Aufruf mit der Sitzung des angemeldeten Kontos. */
  protected <T> ResponseEntity<T> ruf(
      final String pfad, final HttpMethod methode, final Object rumpf, final Class<T> typ) {
    return rest.exchange(pfad, methode, new HttpEntity<>(rumpf, sitzung), typ);
  }

  /** Derselbe Weg, den {@code AuftragAnlegenIT} prueft — hier nur das Mittel zum Zweck. */
  protected AuftragResponse legeAuftragAn(final long zuAngebot) {
    final Map<String, Object> position = new LinkedHashMap<>();
    position.put("platz", Integer.valueOf(1));
    position.put("menge", "2.50");
    position.put("stundenJePersonentag", "7.50");
    final Map<String, Object> felder = new LinkedHashMap<>();
    felder.put("leistungAb", "2026-10-01");
    felder.put("leistungBis", "2026-12-31");
    felder.put("positionen", List.of(position));
    return Objects.requireNonNull(
        ruf(
                "/api/angebote/" + zuAngebot + "/auftrag",
                HttpMethod.POST,
                felder,
                AuftragResponse.class)
            .getBody());
  }

  /** Der Rumpf der Pflege: alle vier aenderbaren Angaben (Kriterium 7). */
  protected static Map<String, Object> pflegerumpf(
      final String status, final String bestellnummer) {
    final Map<String, Object> felder = new LinkedHashMap<>();
    felder.put("auftragDatum", "2026-09-28");
    felder.put("kundenbestellnummer", bestellnummer);
    felder.put("leistungAb", "2026-10-01");
    felder.put("leistungBis", "2026-12-31");
    felder.put("status", status);
    return felder;
  }
}
