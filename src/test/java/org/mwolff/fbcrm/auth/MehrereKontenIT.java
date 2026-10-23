package org.mwolff.fbcrm.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.auth.application.AdresseBereitsVergeben;
import org.mwolff.fbcrm.auth.application.LetzterCLevel;
import org.mwolff.fbcrm.auth.application.MitarbeitendeVerwaltung;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.Role;
import org.mwolff.fbcrm.auth.domain.SecretHasher;
import org.mwolff.fbcrm.mail.MailProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Mehrere Konten in einer Firma, gegen den laufenden Server und eine echte PostgreSQL-Instanz
 * (Issue #421; Plan #414, A6, A12, A13, E5, E6; fachliche Quelle #409, Kriterien 2 bis 8 und 11).
 *
 * <p>Die Szenarien aus dem Fachplan gehen den Weg, den die Oberflaeche geht: Einrichtung, Anlegen
 * ueber {@code /api/mitarbeitende}, Anmeldung mit dem Code aus dem Postausgang. Der Mailversand ist
 * eingeschaltet — dieselben Schalter wie in {@code EinrichtungOhnePasswortIT}, damit beide Klassen
 * einen Anwendungskontext teilen.
 *
 * <p>Der Test mit den zwei gleichzeitigen Herabstufungen ruft den Anwendungsfall direkt: Ueber HTTP
 * entschiede je nach Reihenfolge schon die Zugangsregel (403 statt 409), und gemessen werden soll
 * die Sperre. Damit die beiden Aufrufe wirklich gleichzeitig zaehlen wollen, haelt der Test die
 * Sperre zuerst selbst und gibt sie erst frei, wenn beide auf sie warten.
 */
@TestPropertySource(
    properties = {
      "fbcrm.setup.bootstrap-token=" + MehrereKontenIT.SCHLUESSEL,
      "fbcrm.mail.enabled=true",
      "fbcrm.auth.login-max-attempts=1000"
    })
class MehrereKontenIT extends AbstractIntegrationTest {

  /** Derselbe Schluessel wie in {@code SetupControllerIT}, damit der Kontext geteilt bleibt. */
  static final String SCHLUESSEL = "einmal-schluessel-der-einrichtung";

  private static final Instant ANGELEGT = Instant.parse("2026-10-01T08:00:00Z");
  private static final Duration GEDULD = Duration.ofSeconds(30);
  private static final String MANFRED = "manfred@firma.de";
  private static final String ANNA = "anna@firma.de";
  private static final String BERTA = "berta@firma.de";
  private static final String MITARBEITENDE = "/api/mitarbeitende";

  private final TestRestTemplate rest;
  private final AccountRepository accounts;
  private final MitarbeitendeVerwaltung verwaltung;
  private final SecretHasher hasher;
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;
  private final TransactionTemplate transaktion;
  private final MailProperties mail;

  @Autowired
  MehrereKontenIT(
      final TestRestTemplate rest,
      final AccountRepository accounts,
      final MitarbeitendeVerwaltung verwaltung,
      final SecretHasher hasher,
      final JdbcTemplate jdbc,
      final ObjectMapper json,
      final TransactionTemplate transaktion,
      final MailProperties mail) {
    this.rest = rest;
    this.accounts = accounts;
    this.verwaltung = verwaltung;
    this.hasher = hasher;
    this.jdbc = jdbc;
    this.json = json;
    this.transaktion = transaktion;
    this.mail = mail;
  }

  @BeforeEach
  void frischeInstanz() {
    jdbc.execute(
        "TRUNCATE angebot_kommentar, angebot_position, angebot, ansprechpartner, firma"
            + " RESTART IDENTITY CASCADE");
    jdbc.execute("TRUNCATE outbox_message, login_code, account RESTART IDENTITY CASCADE");
  }

  // --- Szenarien ----------------------------------------------------------------------------

  @Test
  @DisplayName("Szenario: Erstes Konto auf frischer Installation")
  void szenarioErstesKontoAufFrischerInstallation_thenCLevelWithAccessToTheAccounts() {
    // Given — frische Instanz ohne Konto.
    assertThat(anzahl("SELECT count(*) FROM account")).isZero();

    // When — Einrichtung mit Einmal-Schluessel, Namen und Adresse, ohne Rolle.
    final ResponseEntity<String> code =
        rest.postForEntity(
            "/api/setup/code",
            Map.of("email", MANFRED, "displayName", "Manfred", "bootstrapToken", SCHLUESSEL),
            String.class);
    assertThat(code.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    final ResponseEntity<String> eingerichtet =
        rest.postForEntity(
            "/api/setup",
            Map.of(
                "email",
                MANFRED,
                "displayName",
                "Manfred",
                "bootstrapToken",
                SCHLUESSEL,
                "code",
                Anmeldung.codeAusDemPostausgang(jdbc, MANFRED)),
            String.class);
    assertThat(eingerichtet.getStatusCode()).isEqualTo(HttpStatus.OK);
    final HttpHeaders manfred = Anmeldung.mitCookie(eingerichtet);

    // Then — C-Level, und die Verwaltung der Konten steht ihm offen.
    assertThat(lies(ruf(manfred, "/api/auth/me", HttpMethod.GET, null)).path("role").asText())
        .isEqualTo(Role.C_LEVEL.name());
    assertThat(ruf(manfred, MITARBEITENDE, HttpMethod.GET, null).getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("Szenario: C-Level legt eine Mitarbeiterin an")
  void szenarioCLevelLegtEineMitarbeiterinAn_thenListedGreetedAndAbleToLogIn() {
    // Given
    final HttpHeaders manfred = cLevel(MANFRED);

    // When
    final ResponseEntity<String> angelegt = legeAn(manfred, ANNA, "Anna Berg", Role.EMPLOYEE);

    // Then — 201, aktiv in der Liste, mit der Rolle Employee.
    assertThat(angelegt.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(lies(angelegt).path("begruessungMailVersandt").asBoolean()).isTrue();
    final JsonNode anna = zeile(manfred, ANNA);
    assertThat(anna.path("displayName").asText()).isEqualTo("Anna Berg");
    assertThat(anna.path("role").asText()).isEqualTo(Role.EMPLOYEE.name());
    assertThat(anna.path("aktiv").asBoolean()).isTrue();

    // Then — die Begruessung nennt die Adresse von fb.crm.
    final List<String> rumpf =
        jdbc.queryForList(
            "SELECT body FROM outbox_message WHERE recipient = ?", String.class, ANNA);
    assertThat(rumpf).singleElement().asString().contains(mail.baseUrl()).contains("Anna Berg");

    // Then — Anna meldet sich mit ihrer Adresse und dem Code aus der Mail an.
    final ResponseEntity<String> angefordert =
        rest.postForEntity("/api/auth/code", Map.of("email", ANNA), String.class);
    assertThat(angefordert.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    final ResponseEntity<String> login =
        rest.postForEntity(
            "/api/auth/login",
            Map.of("email", ANNA, "code", Anmeldung.codeAusDemPostausgang(jdbc, ANNA)),
            String.class);
    assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(
            lies(ruf(Anmeldung.mitCookie(login), "/api/auth/me", HttpMethod.GET, null))
                .path("role")
                .asText())
        .isEqualTo(Role.EMPLOYEE.name());
  }

  @Test
  @DisplayName("Szenario: Stilllegen beendet die Sitzung")
  void szenarioStilllegenBeendetDieSitzung_thenLoggedOutLockedOutAndCommentsKeepTheName() {
    // Given — Anna ist angemeldet und hat an einem Angebot kommentiert.
    final HttpHeaders manfred = cLevel(MANFRED);
    final long annaId = id(legeAn(manfred, ANNA, "Anna Berg", Role.EMPLOYEE));
    final HttpHeaders anna = Anmeldung.perCode(rest, jdbc, hasher, ANNA);
    final long angebot = angebot(manfred);
    kommentiere(anna, angebot, "Bitte bis Freitag.");

    // When
    final ResponseEntity<String> stillgelegt =
        ruf(manfred, MITARBEITENDE + "/" + annaId + "/stilllegen", HttpMethod.POST, null);

    // Then — beim naechsten Schritt abgemeldet.
    assertThat(stillgelegt.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(ruf(anna, kommentare(angebot), HttpMethod.GET, null).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);

    // Then — ein neuer Code fuer ihre Adresse: dieselbe Antwort wie fuer eine unbekannte, und es
    // entsteht weder ein Code noch eine Mail.
    final long codesVorher = anzahl("SELECT count(*) FROM login_code");
    final long mailsVorher = anzahl("SELECT count(*) FROM outbox_message");
    final ResponseEntity<String> fuerAnna =
        rest.postForEntity("/api/auth/code", Map.of("email", ANNA), String.class);
    final ResponseEntity<String> fuerUnbekannt =
        rest.postForEntity("/api/auth/code", Map.of("email", "niemand@firma.de"), String.class);
    assertThat(fuerAnna.getStatusCode()).isEqualTo(fuerUnbekannt.getStatusCode());
    assertThat(fuerAnna.getBody()).isEqualTo(fuerUnbekannt.getBody());
    assertThat(anzahl("SELECT count(*) FROM login_code")).isEqualTo(codesVorher);
    assertThat(anzahl("SELECT count(*) FROM outbox_message")).isEqualTo(mailsVorher);

    // Then — ihre Kommentare tragen weiter ihren Namen.
    assertThat(verfasser(manfred, angebot)).containsExactly("Anna Berg");
  }

  @Test
  @DisplayName("Szenario: Umbenennung wirkt auf alte Kommentare")
  void szenarioUmbenennungWirktAufAlteKommentare_thenTheOldCommentsCarryTheNewName() {
    // Given
    final HttpHeaders manfred = cLevel(MANFRED);
    final long annaId = id(legeAn(manfred, ANNA, "Anna Berg", Role.EMPLOYEE));
    final HttpHeaders anna = Anmeldung.perCode(rest, jdbc, hasher, ANNA);
    final long angebot = angebot(manfred);
    kommentiere(anna, angebot, "Erster Entwurf ist raus.");

    // When
    final ResponseEntity<String> geaendert =
        ruf(
            manfred,
            MITARBEITENDE + "/" + annaId,
            HttpMethod.PUT,
            konto(ANNA, "Anna Müller", Role.EMPLOYEE));

    // Then — der Name wirkt ohne Abmeldung, auch auf die bisherigen Kommentare.
    assertThat(geaendert.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(verfasser(manfred, angebot)).containsExactly("Anna Müller");
    assertThat(ruf(anna, "/api/auth/me", HttpMethod.GET, null).getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("Szenario: Der letzte C-Level bleibt")
  void szenarioDerLetzteCLevelBleibt_thenRefusedAloneAndAllowedWithASecond() {
    // Given — Manfred ist das einzige aktive C-Level-Konto.
    final HttpHeaders manfred = cLevel(MANFRED);
    final long manfredId = kontoId(MANFRED);

    // When / Then — herabstufen und stilllegen werden mit dem einen Satz abgewiesen.
    final ResponseEntity<String> herabgestuft =
        ruf(
            manfred,
            MITARBEITENDE + "/" + manfredId,
            HttpMethod.PUT,
            konto(MANFRED, "Manfred", Role.EMPLOYEE));
    final ResponseEntity<String> stillgelegt =
        ruf(manfred, MITARBEITENDE + "/" + manfredId + "/stilllegen", HttpMethod.POST, null);
    assertThat(List.of(herabgestuft, stillgelegt))
        .allSatisfy(
            antwort -> {
              assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(lies(antwort).path("detail").asText()).isEqualTo(LetzterCLevel.MELDUNG);
            });
    assertThat(accounts.findById(manfredId))
        .hasValueSatisfying(
            k -> {
              assertThat(k.role()).isEqualTo(Role.C_LEVEL);
              assertThat(k.aktiv()).isTrue();
            });

    // Given — ein zweiter aktiver C-Level.
    legeAn(manfred, BERTA, "Berta", Role.C_LEVEL);
    final HttpHeaders berta = Anmeldung.perCode(rest, jdbc, hasher, BERTA);

    // When / Then — jetzt geht das Herabstufen des eigenen Kontos durch …
    assertThat(
            ruf(
                    manfred,
                    MITARBEITENDE + "/" + manfredId,
                    HttpMethod.PUT,
                    konto(MANFRED, "Manfred", Role.EMPLOYEE))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);

    // … und, wieder befoerdert, auch das Stilllegen des eigenen Kontos.
    assertThat(
            ruf(
                    berta,
                    MITARBEITENDE + "/" + manfredId,
                    HttpMethod.PUT,
                    konto(MANFRED, "Manfred", Role.C_LEVEL))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(
            ruf(manfred, MITARBEITENDE + "/" + manfredId + "/stilllegen", HttpMethod.POST, null)
                .getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(ruf(manfred, "/api/auth/me", HttpMethod.GET, null).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(aktiveCLevel()).isEqualTo(1);
  }

  @Test
  void aendern_givenTwoConcurrentDemotionsOfTheLastTwoCLevels_thenExactlyOnePasses()
      throws Exception {
    // Given — zwei aktive C-Level.
    final Account manfred =
        accounts.save(Konten.mit(null, MANFRED, Role.C_LEVEL, true, 0, ANGELEGT));
    final Account berta = accounts.save(Konten.mit(null, BERTA, Role.C_LEVEL, true, 0, ANGELEGT));

    // When — beide Herabstufungen warten auf die Sperre, die der Test haelt, und laufen danach los.
    final List<@Nullable Throwable> ergebnisse = new ArrayList<>();
    final CountDownLatch gesperrt = new CountDownLatch(1);
    final CountDownLatch freigeben = new CountDownLatch(1);
    try (ExecutorService pool = Executors.newFixedThreadPool(3)) {
      final Future<?> halter =
          pool.submit(
              () ->
                  transaktion.executeWithoutResult(
                      status -> {
                        accounts.aktiveCLevelMitSperre();
                        gesperrt.countDown();
                        warte(freigeben);
                      }));
      warte(gesperrt);
      final List<Future<@Nullable Throwable>> herabstufungen =
          List.of(pool.submit(() -> herabstufen(manfred)), pool.submit(() -> herabstufen(berta)));
      warteAufSperrende(2);
      freigeben.countDown();
      halter.get(GEDULD.toSeconds(), TimeUnit.SECONDS);
      for (final Future<@Nullable Throwable> herabstufung : herabstufungen) {
        ergebnisse.add(herabstufung.get(GEDULD.toSeconds(), TimeUnit.SECONDS));
      }
    }

    // Then — genau eine kam durch, die andere scheiterte am letzten C-Level.
    assertThat(ergebnisse).filteredOn(Objects::isNull).hasSize(1);
    assertThat(ergebnisse)
        .filteredOn(Objects::nonNull)
        .singleElement()
        .isInstanceOf(LetzterCLevel.class);
    assertThat(aktiveCLevel()).isEqualTo(1);
  }

  @Test
  void anlegen_givenTheAddressOfADeactivatedAccountInAnotherCase_thenRefusedAtTheField() {
    // Given — Anna ist stillgelegt; ihre Adresse bleibt blockiert (E6).
    final HttpHeaders manfred = cLevel(MANFRED);
    final long annaId = id(legeAn(manfred, ANNA, "Anna Berg", Role.EMPLOYEE));
    ruf(manfred, MITARBEITENDE + "/" + annaId + "/stilllegen", HttpMethod.POST, null);

    // When
    final ResponseEntity<String> antwort =
        legeAn(manfred, "ANNA@Firma.de", "Anna Zwei", Role.READ_ONLY);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(lies(antwort).path("fieldErrors").path("email").path(0).asText())
        .isEqualTo(AdresseBereitsVergeben.MELDUNG);
    assertThat(anzahl("SELECT count(*) FROM account")).isEqualTo(2);
  }

  @Test
  void aendern_givenTheAddressOfAnotherAccountInAnotherCase_thenRefusedAndNothingChanges() {
    // Given
    final HttpHeaders manfred = cLevel(MANFRED);
    final long annaId = id(legeAn(manfred, ANNA, "Anna Berg", Role.EMPLOYEE));

    // When
    final ResponseEntity<String> antwort =
        ruf(
            manfred,
            MITARBEITENDE + "/" + annaId,
            HttpMethod.PUT,
            konto("Manfred@FIRMA.de", "Anna Berg", Role.EMPLOYEE));

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(accounts.findById(annaId))
        .hasValueSatisfying(k -> assertThat(k.email()).isEqualTo(ANNA));
  }

  @Test
  void aendern_givenANewAddress_thenTheSessionEndsAndTheNewAddressLogsIn() {
    // Given — Kriterium 6.
    final HttpHeaders manfred = cLevel(MANFRED);
    final long annaId = id(legeAn(manfred, ANNA, "Anna Berg", Role.EMPLOYEE));
    final HttpHeaders anna = Anmeldung.perCode(rest, jdbc, hasher, ANNA);

    // When
    final ResponseEntity<String> antwort =
        ruf(
            manfred,
            MITARBEITENDE + "/" + annaId,
            HttpMethod.PUT,
            konto("anna.berg@firma.de", "Anna Berg", Role.EMPLOYEE));

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(ruf(anna, "/api/auth/me", HttpMethod.GET, null).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    final HttpHeaders neu = Anmeldung.perCode(rest, jdbc, hasher, "anna.berg@firma.de");
    assertThat(ruf(neu, "/api/auth/me", HttpMethod.GET, null).getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  void aktivieren_thenTheDeactivatedPersonLogsInAgain() {
    // Given — Kriterium 7.
    final HttpHeaders manfred = cLevel(MANFRED);
    final long annaId = id(legeAn(manfred, ANNA, "Anna Berg", Role.EMPLOYEE));
    ruf(manfred, MITARBEITENDE + "/" + annaId + "/stilllegen", HttpMethod.POST, null);

    // When
    final ResponseEntity<String> antwort =
        ruf(manfred, MITARBEITENDE + "/" + annaId + "/aktivieren", HttpMethod.POST, null);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(zeile(manfred, ANNA).path("aktiv").asBoolean()).isTrue();
    assertThat(Anmeldung.perCode(rest, jdbc, hasher, ANNA)).isNotEmpty();
  }

  @Test
  void mitarbeitende_givenEmployeeOrReadOnly_thenForbiddenAndNothingCreated() {
    // Given
    cLevel(MANFRED);
    accounts.save(Konten.mit(null, ANNA, Role.EMPLOYEE, true, 0, ANGELEGT));
    accounts.save(Konten.mit(null, BERTA, Role.READ_ONLY, true, 0, ANGELEGT));
    final HttpHeaders employee = Anmeldung.perCode(rest, jdbc, hasher, ANNA);
    final HttpHeaders readOnly = Anmeldung.perCode(rest, jdbc, hasher, BERTA);

    // When / Then
    for (final HttpHeaders sitzung : List.of(employee, readOnly)) {
      assertThat(ruf(sitzung, MITARBEITENDE, HttpMethod.GET, null).getStatusCode())
          .isEqualTo(HttpStatus.FORBIDDEN);
      assertThat(
              ruf(
                      sitzung,
                      MITARBEITENDE,
                      HttpMethod.POST,
                      konto("neu@firma.de", "Neu", Role.C_LEVEL))
                  .getStatusCode())
          .isEqualTo(HttpStatus.FORBIDDEN);
    }
    assertThat(anzahl("SELECT count(*) FROM account")).isEqualTo(3);
  }

  // --- Helfer -------------------------------------------------------------------------------

  /** Legt ein aktives C-Level-Konto an und meldet es an. */
  private HttpHeaders cLevel(final String email) {
    accounts.save(
        Konten.mit(null, email, Role.C_LEVEL, true, 0, ANGELEGT).mitNamen("Manfred", ANGELEGT));
    return Anmeldung.perCode(rest, jdbc, hasher, email);
  }

  private ResponseEntity<String> legeAn(
      final HttpHeaders sitzung, final String email, final String name, final Role rolle) {
    return ruf(sitzung, MITARBEITENDE, HttpMethod.POST, konto(email, name, rolle));
  }

  private static Map<String, String> konto(
      final String email, final String name, final Role rolle) {
    return Map.of("email", email, "displayName", name, "role", rolle.name());
  }

  private long id(final ResponseEntity<String> antwort) {
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return lies(antwort).path("id").asLong();
  }

  private long kontoId(final String email) {
    return accounts.findByEmail(email).orElseThrow().requireId();
  }

  /** Die Zeile eines Kontos aus der Liste der Verwaltung. */
  private JsonNode zeile(final HttpHeaders sitzung, final String email) {
    final ResponseEntity<String> liste = ruf(sitzung, MITARBEITENDE, HttpMethod.GET, null);
    assertThat(liste.getStatusCode()).isEqualTo(HttpStatus.OK);
    for (final JsonNode eintrag : lies(liste).path("mitarbeitende")) {
      if (email.equals(eintrag.path("email").asText())) {
        return eintrag;
      }
    }
    throw new AssertionError("Kein Konto " + email + " in der Liste: " + liste.getBody());
  }

  private long angebot(final HttpHeaders sitzung) {
    final ResponseEntity<String> firma =
        ruf(sitzung, "/api/firmen", HttpMethod.POST, Map.of("name", "Adler AG"));
    final ResponseEntity<String> angebot =
        ruf(
            sitzung,
            "/api/firmen/" + id(firma) + "/angebote",
            HttpMethod.POST,
            Map.of("titel", "Website-Relaunch"));
    return id(angebot);
  }

  private static String kommentare(final long angebot) {
    return "/api/angebote/" + angebot + "/kommentare";
  }

  private void kommentiere(final HttpHeaders sitzung, final long angebot, final String text) {
    assertThat(
            ruf(sitzung, kommentare(angebot), HttpMethod.POST, Map.of("text", text))
                .getStatusCode())
        .isEqualTo(HttpStatus.CREATED);
  }

  private List<String> verfasser(final HttpHeaders sitzung, final long angebot) {
    final List<String> namen = new ArrayList<>();
    for (final JsonNode kommentar :
        lies(ruf(sitzung, kommentare(angebot), HttpMethod.GET, null)).path("kommentare")) {
      namen.add(kommentar.path("verfasser").asText());
    }
    return namen;
  }

  private @Nullable Throwable herabstufen(final Account konto) {
    return catchThrowable(
        () -> verwaltung.aendern(konto.requireId(), konto.email(), "C-Level", Role.EMPLOYEE));
  }

  /** Wartet, bis so viele Verbindungen auf eine Zeilensperre warten. */
  private void warteAufSperrende(final int erwartet) throws InterruptedException {
    final long ende = System.nanoTime() + GEDULD.toNanos();
    while (anzahl(
            "SELECT count(*) FROM pg_stat_activity"
                + " WHERE datname = current_database() AND wait_event_type = 'Lock'")
        < erwartet) {
      assertThat(System.nanoTime())
          .as("Beide Herabstufungen warten auf die Sperre")
          .isLessThan(ende);
      TimeUnit.MILLISECONDS.sleep(20);
    }
  }

  private static void warte(final CountDownLatch latch) {
    try {
      assertThat(latch.await(GEDULD.toSeconds(), TimeUnit.SECONDS)).isTrue();
    } catch (final InterruptedException unterbrochen) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(unterbrochen);
    }
  }

  private long aktiveCLevel() {
    return anzahl("SELECT count(*) FROM account WHERE role = 'C_LEVEL' AND aktiv");
  }

  private long anzahl(final String sql) {
    return Objects.requireNonNull(jdbc.queryForObject(sql, Long.class));
  }

  private JsonNode lies(final ResponseEntity<String> antwort) {
    try {
      return json.readTree(Objects.requireNonNull(antwort.getBody()));
    } catch (final JsonProcessingException fehler) {
      throw new IllegalStateException("Kein JSON: " + antwort.getBody(), fehler);
    }
  }

  private ResponseEntity<String> ruf(
      final HttpHeaders sitzung,
      final String pfad,
      final HttpMethod methode,
      final @Nullable Object rumpf) {
    final HttpHeaders kopf = new HttpHeaders();
    kopf.addAll(sitzung);
    if (rumpf != null) {
      kopf.setContentType(MediaType.APPLICATION_JSON);
    }
    return rest.exchange(pfad, methode, new HttpEntity<>(rumpf, kopf), String.class);
  }
}
