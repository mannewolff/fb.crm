package org.mwolff.fbcrm.angebot;

import static org.assertj.core.api.Assertions.assertThat;

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
 * Die Reaktion des Kunden ueber HTTP, gegen echtes PostgreSQL (Kriterien 17, 18, 19).
 *
 * <p>Hier steht der Fall, den F13 begruendet, und er ist nur an der echten Transaktion zu zeigen:
 * Zwei Angebote werden versendet — das erste wechselt dabei nach {@code ABGELOEST} —, und dann sagt
 * der Kunde „wir nehmen das erste". Danach ist das erste {@code ANGENOMMEN}, das zweite {@code
 * ABGELOEST}, und die Historie des Vorgangs traegt alle fuenf Wechsel in Zeitpunktreihenfolge.
 *
 * <p>Dazu zwei Zusagen, die ebenfalls nur hier greifen. Die Schreibsperre am abgeschlossenen
 * Vorgang gilt fuer diesen Weg <b>nicht</b> (E13) — Kriterium 9 zieht sie nur um Anlegen und
 * Versenden; der Anwendungsfall hat gar keinen Port zum Vorgang, gezeigt wird es am abgeschlossenen
 * Vorgang in der Datenbank. Und jeder neue Pfad unter {@code /api} ist ohne Sitzung verschlossen.
 */
class AngebotReaktionIT extends AbstractIntegrationTest {

  private static final String MAIL = "manne@example.org";
  private static final String PASSWORT = "richtiges-passwort";
  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");
  private static final String BESCHREIBUNG = "Neugestaltung der Website";
  private static final String BEDINGUNGEN = "Zahlbar innerhalb von 14 Tagen ohne Abzug.";

  private final TestRestTemplate rest;
  private final AccountRepository accounts;
  private final PasswordHasher hasher;
  private final JdbcTemplate jdbc;
  private final Clock clock;

  private HttpHeaders sitzung = new HttpHeaders();
  private long vorgangId;

  @Autowired
  AngebotReaktionIT(
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
  void legeEinenVersandfaehigenVorgangAn() {
    jdbc.execute(
        "TRUNCATE angebot_position, angebot, vorgang_eintrag, vorgang, ansprechpartner, firma"
            + " RESTART IDENTITY CASCADE");
    jdbc.execute("DELETE FROM angebot_nummernkreis");
    jdbc.update(
        "UPDATE eigene_angaben SET name = ?, strasse = ?, plz = ?, ort = ?, land = ?,"
            + " bankverbindung = ?, zahlungsbedingungen = ?",
        "Manfred Wolff",
        "Am Deich 2",
        "28199",
        "Hansestadt",
        "Deutschland",
        "Sparkasse, IBAN DE02 1203 0000 0000 2020 51",
        BEDINGUNGEN);
    jdbc.update(
        "INSERT INTO firma (name, strasse, plz, ort, land) VALUES (?, ?, ?, ?, ?)",
        "Adler AG",
        "Hauptstrasse 1",
        "28195",
        "Bremen",
        "Deutschland");
    final long firmaId = einzigeId("SELECT id FROM firma");
    jdbc.update(
        "INSERT INTO vorgang (nummer, titel, firma_id) VALUES (1, ?, ?)",
        "Website-Relaunch",
        Long.valueOf(firmaId));
    vorgangId = einzigeId("SELECT id FROM vorgang");
    jdbc.execute("TRUNCATE outbox_message, password_reset_token, account RESTART IDENTITY CASCADE");
    accounts.save(
        new Account(null, MAIL, "Manne", hasher.hash(PASSWORT), Role.ADMIN, 0, ANGELEGT, ANGELEGT));
    sitzung = angemeldeterKopf();
  }

  private long einzigeId(final String abfrage) {
    return Objects.requireNonNull(jdbc.queryForObject(abfrage, Long.class)).longValue();
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

  private static Map<String, Object> entwurfsrumpf(final LocalDate gueltigBis) {
    final Map<String, Object> position = new LinkedHashMap<>();
    position.put("bezeichnung", "Konzeption");
    position.put("abrechnungsmodus", "AUFWAND");
    position.put("menge", "2.50");
    position.put("einheit", "PERSONENTAG");
    position.put("einzelpreis", "1000.01");
    final Map<String, Object> felder = new LinkedHashMap<>();
    felder.put("gueltigBis", gueltigBis.toString());
    felder.put("leistungsbeschreibung", BESCHREIBUNG);
    felder.put("zahlungsbedingungen", BEDINGUNGEN);
    felder.put("positionen", List.of(position));
    return felder;
  }

  /** Ein versendetes Angebot am Vorgang; geliefert wird seine Nummer. */
  private Versandeter versandtesAngebot() {
    final long angebotId =
        Objects.requireNonNull(
                ruf(
                        "/api/vorgaenge/" + vorgangId + "/angebote",
                        HttpMethod.POST,
                        null,
                        AngebotResponse.class)
                    .getBody())
            .id();
    ruf(
        "/api/angebote/" + angebotId,
        HttpMethod.PUT,
        entwurfsrumpf(LocalDate.now(clock.withZone(Geschaeftszone.ZONE)).plusDays(30)),
        AngebotResponse.class);
    final AngebotResponse versendet =
        Objects.requireNonNull(
            ruf(
                    "/api/angebote/" + angebotId + "/versenden",
                    HttpMethod.POST,
                    null,
                    AngebotResponse.class)
                .getBody());
    return new Versandeter(angebotId, Objects.requireNonNull(versendet.nummer()));
  }

  /** Ein versendetes Angebot mit seiner Kennung und seiner Nummer. */
  private record Versandeter(long id, String nummer) {}

  private ResponseEntity<AngebotResponse> reagiere(final long angebotId, final String weg) {
    return ruf(
        "/api/angebote/" + angebotId + "/" + weg, HttpMethod.POST, null, AngebotResponse.class);
  }

  private String zustand(final long angebotId) {
    return Objects.requireNonNull(
        jdbc.queryForObject(
            "SELECT zustand FROM angebot WHERE id = ?", String.class, Long.valueOf(angebotId)));
  }

  private List<String> ereignisse() {
    return jdbc.queryForList(
        "SELECT text FROM vorgang_eintrag WHERE vorgang_id = ? AND art = 'EREIGNIS'"
            + " ORDER BY geschehen_am, id",
        String.class,
        Long.valueOf(vorgangId));
  }

  @Test
  void annehmen_theSupersededFirstAngebot_thenClosesTheSecondOneAndRecordsEveryWechsel() {
    // Given — F13: nachverhandelt, und der Kunde nimmt am Ende das erste Angebot.
    final Versandeter erstes = versandtesAngebot();
    final Versandeter zweites = versandtesAngebot();
    assertThat(zustand(erstes.id())).isEqualTo("ABGELOEST");

    // When
    final ResponseEntity<AngebotResponse> antwort = reagiere(erstes.id(), "annehmen");

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(Objects.requireNonNull(antwort.getBody()).stand().name()).isEqualTo("ANGENOMMEN");
    assertThat(antwort.getBody().reaktionAm()).isNotNull();
    assertThat(zustand(erstes.id())).isEqualTo("ANGENOMMEN");
    assertThat(zustand(zweites.id())).isEqualTo("ABGELOEST");
    assertThat(ereignisse())
        .containsExactly(
            "Angebot %s versendet".formatted(erstes.nummer()),
            "Angebot %s versendet".formatted(zweites.nummer()),
            "Angebot %s abgeloest".formatted(erstes.nummer()),
            "Angebot %s angenommen".formatted(erstes.nummer()),
            "Angebot %s abgeloest".formatted(zweites.nummer()));
  }

  @Test
  void ablehnen_thenClosesOnlyTheAngebotItself() {
    // Given — Kriterium 17 nennt allein die Annahme; eine Absage laesst die anderen offen.
    final Versandeter erstes = versandtesAngebot();
    final Versandeter zweites = versandtesAngebot();

    // When
    final ResponseEntity<AngebotResponse> antwort = reagiere(zweites.id(), "ablehnen");

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(zustand(zweites.id())).isEqualTo("ABGELEHNT");
    assertThat(zustand(erstes.id())).isEqualTo("ABGELOEST");
    assertThat(ereignisse()).last().isEqualTo("Angebot %s abgelehnt".formatted(zweites.nummer()));
  }

  @Test
  void annehmen_twice_thenAnswers409TheSecondTime() {
    // Given — Kriterium 17: beide Reaktionen sind endgueltig.
    final Versandeter angebot = versandtesAngebot();
    reagiere(angebot.id(), "annehmen");

    // When / Then
    assertThat(
            ruf("/api/angebote/" + angebot.id() + "/annehmen", HttpMethod.POST, null, String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(
            ruf("/api/angebote/" + angebot.id() + "/ablehnen", HttpMethod.POST, null, String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void annehmen_atAClosedVorgang_thenSucceeds() {
    // Given — E13: Kriterium 9 sperrt das Anlegen und das Versenden, nicht die Reaktion.
    final Versandeter angebot = versandtesAngebot();
    jdbc.update("UPDATE vorgang SET abgeschlossen = true WHERE id = ?", Long.valueOf(vorgangId));

    // When
    final ResponseEntity<AngebotResponse> antwort = reagiere(angebot.id(), "annehmen");

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(zustand(angebot.id())).isEqualTo("ANGENOMMEN");
  }

  @Test
  void reaktionswege_withoutASession_thenUnauthorized() {
    // Given — jeder neue Pfad unter /api ist ohne Sitzung verschlossen.
    sitzung = new HttpHeaders();

    // When / Then
    assertThat(ruf("/api/angebote/1/annehmen", HttpMethod.POST, null, String.class).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(ruf("/api/angebote/1/ablehnen", HttpMethod.POST, null, String.class).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
