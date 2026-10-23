package org.mwolff.fbcrm.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.mwolff.fbcrm.auth.Anmeldung;
import org.mwolff.fbcrm.auth.Konten;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.Role;
import org.mwolff.fbcrm.auth.domain.SecretHasher;
import org.mwolff.fbcrm.common.web.GlobalExceptionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Die Zugangsregel je Rolle gegen den laufenden Server (Plan #414, A1, A2, A9; fachliche Quelle
 * #409, Kriterien 9 und 10).
 *
 * <p>Wie in {@link AccessRuleIT} entsteht die Liste der geprueften Wege aus dem {@link
 * RequestMappingHandlerMapping} und nicht aus einer Aufzaehlung: Ein kuenftig neuer schreibender
 * Endpunkt faellt hier von selbst auf, wenn er ReadOnly offenstuende. Anders als dort bleiben die
 * Wege mit Kennung nicht draussen — jede Kennung wird zu {@code 1}, und der Bestand legt eine Firma
 * mit dieser Kennung an, damit ein schreibender Weg auch etwas zu aendern haette.
 *
 * <p>Ob „aendert nichts" stimmt, misst ein Fingerabdruck jeder Tabelle vor und nach den Aufrufen.
 */
class RollenZugangIT extends AbstractIntegrationTest {

  private static final Instant ANGELEGT = Instant.parse("2026-10-01T08:00:00Z");
  private static final String C_LEVEL = "chefin@example.org";
  private static final String EMPLOYEE = "anna@example.org";
  private static final String READ_ONLY = "ben@example.org";

  /** Die Wege, die ohne Sitzung offen sind (siehe {@link AccessRuleIT}); sie stehen vor A2. */
  private static final Set<String> OFFEN =
      Set.of(
          "/api/auth/login",
          "/api/auth/code",
          "/api/setup",
          "/api/setup/code",
          "/api/setup/status");

  /** Der eine schreibende Weg, der jeder Sitzung offen bleibt (A2). */
  private static final String ABMELDEN = "/api/auth/logout";

  /** Die Verwaltung der Konten; nur C-Level, auch lesend (A2, Stufe 1). */
  private static final String MITARBEITENDE = "/api/mitarbeitende";

  private static final Set<HttpMethod> SCHREIBEND =
      Set.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE);

  private final TestRestTemplate rest;
  private final AccountRepository accounts;
  private final SecretHasher hasher;
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;
  private final RequestMappingHandlerMapping mappings;

  private HttpHeaders cLevel = new HttpHeaders();
  private HttpHeaders employee = new HttpHeaders();
  private HttpHeaders readOnly = new HttpHeaders();

  @Autowired
  RollenZugangIT(
      final TestRestTemplate rest,
      final AccountRepository accounts,
      final SecretHasher hasher,
      final JdbcTemplate jdbc,
      final ObjectMapper json,
      // Der Actuator bringt eine zweite Bean desselben Typs mit; gemeint ist die der Controller.
      @Qualifier("requestMappingHandlerMapping") final RequestMappingHandlerMapping mappings) {
    this.rest = rest;
    this.accounts = accounts;
    this.hasher = hasher;
    this.jdbc = jdbc;
    this.json = json;
    this.mappings = mappings;
  }

  @BeforeEach
  void dreiKontenUndEineFirma() {
    jdbc.execute(
        "TRUNCATE rechnung_position, rechnung, angebot_position, angebot, ansprechpartner, firma"
            + " RESTART IDENTITY CASCADE");
    jdbc.execute("TRUNCATE outbox_message, login_code, account RESTART IDENTITY CASCADE");
    accounts.save(Konten.mit(null, C_LEVEL, Role.C_LEVEL, true, 0, ANGELEGT));
    accounts.save(Konten.mit(null, EMPLOYEE, Role.EMPLOYEE, true, 0, ANGELEGT));
    accounts.save(Konten.mit(null, READ_ONLY, Role.READ_ONLY, true, 0, ANGELEGT));
    cLevel = Anmeldung.perCode(rest, jdbc, hasher, C_LEVEL);
    employee = Anmeldung.perCode(rest, jdbc, hasher, EMPLOYEE);
    readOnly = Anmeldung.perCode(rest, jdbc, hasher, READ_ONLY);
    final ResponseEntity<String> firma = ruf(cLevel, "/api/firmen", HttpMethod.POST, firma());
    assertThat(firma.getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

  @Test
  @DisplayName("Szenario: Employee kommt nicht an die Einstellungen")
  void szenarioEmployeeKommtNichtAnDieEinstellungen_thenForbiddenWithAProblemDetail() {
    // Given — A2, Stufen 1 und 2: Einstellungen und Konten sind fuer Employee zu.
    final List<Weg> wege =
        List.of(
            new Weg(HttpMethod.GET, "/api/rechnung/einstellungen"),
            new Weg(HttpMethod.PUT, "/api/rechnung/einstellungen"),
            new Weg(HttpMethod.GET, "/api/eigene-angaben"),
            new Weg(HttpMethod.PUT, "/api/eigene-angaben"),
            new Weg(HttpMethod.GET, "/api/briefpapier"),
            new Weg(HttpMethod.PUT, "/api/briefpapier"),
            new Weg(HttpMethod.GET, MITARBEITENDE));
    final Map<String, String> vorher = bestand();

    // When / Then — 403 als Problem Detail mit dem einen Satz (A9), und nichts geaendert.
    assertThat(wege).allSatisfy(weg -> istVerboten(ruf(employee, weg.pfad(), weg.methode(), "{}")));
    assertThat(bestand()).isEqualTo(vorher);
  }

  @Test
  @DisplayName("Szenario: ReadOnly liest, ändert aber nichts")
  void szenarioReadOnlyLiestAendertAberNichts_thenEveryReadOpenAndEveryWriteForbidden() {
    // Given
    final List<Weg> lesend = wege().filter(weg -> HttpMethod.GET.equals(weg.methode())).toList();
    final List<Weg> schreibend =
        wege()
            .filter(weg -> SCHREIBEND.contains(weg.methode()))
            .filter(weg -> !ABMELDEN.equals(weg.pfad()))
            .toList();

    // When / Then — jeder Lesezugriff ist offen, 200 oder je nach Bestand 404 wie in AccessRuleIT.
    assertThat(lesend)
        .isNotEmpty()
        .allSatisfy(
            weg ->
                assertThat(ruf(readOnly, weg.pfad(), weg.methode(), null).getStatusCode())
                    .as(weg.toString())
                    .isNotIn(HttpStatus.FORBIDDEN, HttpStatus.UNAUTHORIZED));

    // When / Then — jeder schreibende Weg ist zu, und die Datenbank bleibt, wie sie war.
    final Map<String, String> vorher = bestand();
    assertThat(schreibend)
        .isNotEmpty()
        .allSatisfy(weg -> istVerboten(ruf(readOnly, weg.pfad(), weg.methode(), "{}")));
    assertThat(bestand()).isEqualTo(vorher);
  }

  @Test
  void generatedPaths_thenCoverTheWritingPathsWithAnId() {
    // When — die Probe deckt auch Wege hinter einer Kennung ab, anders als AccessRuleIT.
    final List<Weg> alle = wege().toList();

    // Then
    assertThat(alle)
        .contains(
            new Weg(HttpMethod.PUT, "/api/firmen/1"),
            new Weg(HttpMethod.DELETE, "/api/angebote/1/anlagen/1"),
            new Weg(HttpMethod.POST, "/api/rechnungen/1/stellen"));
  }

  /**
   * Die Downloads bleiben jeder Sitzung offen (E4), der Probedruck folgt den Einstellungen (E1).
   */
  @ParameterizedTest(name = "ReadOnly: GET {0} ist nicht verboten")
  @CsvSource({
    "/api/rechnungen/1/dokument",
    "/api/nachgetragene-rechnungen/1/dokument",
    "/api/angebote/1/anlagen/1/inhalt",
    "/api/briefpapier/probedruck",
    "/api/rechnung/einstellungen",
    "/api/eigene-angaben",
    "/api/briefpapier"
  })
  void readOnly_givenAReadingPath_thenIsNotForbidden(final String pfad) {
    // When
    final ResponseEntity<String> antwort = ruf(readOnly, pfad, HttpMethod.GET, null);

    // Then
    assertThat(antwort.getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void readOnly_givenTheSettings_thenReadsThem() {
    // When — Kriterium 10: die Einstellungen liest ReadOnly mit.
    final ResponseEntity<String> antwort =
        ruf(readOnly, "/api/rechnung/einstellungen", HttpMethod.GET, null);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void readOnly_givenTheAccounts_thenForbidden() {
    // When — A2, Stufe 1: die Konten auch lesend nur fuer C-Level.
    final ResponseEntity<String> antwort = ruf(readOnly, MITARBEITENDE, HttpMethod.GET, null);

    // Then
    istVerboten(antwort);
  }

  @Test
  void readOnly_givenLogout_thenLogsOut() {
    // When — A2: ohne eigene Zeile vor Stufe 3 kaeme ReadOnly nicht mehr heraus.
    final ResponseEntity<String> antwort = ruf(readOnly, ABMELDEN, HttpMethod.POST, null);

    // Then
    assertThat(antwort.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(ruf(readOnly, "/api/auth/me", HttpMethod.GET, null).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void employee_givenTheProofPrint_thenForbidden() {
    // When — E1/E4: der Probedruck gehoert zu den Einstellungen.
    final ResponseEntity<String> antwort =
        ruf(employee, "/api/briefpapier/probedruck", HttpMethod.GET, null);

    // Then
    istVerboten(antwort);
  }

  @Test
  void employee_givenTheLetterheadFile_thenForbidden() {
    // When — A2, Stufe 2: schreibend unter /api/briefpapier/** nur C-Level.
    final ResponseEntity<String> antwort =
        ruf(employee, "/api/briefpapier/datei", HttpMethod.DELETE, null);

    // Then
    istVerboten(antwort);
  }

  @Test
  void employee_givenBusinessData_thenWrites() {
    // When — Kriterium 9: Employee legt Kunden an und aendert sie.
    final ResponseEntity<String> anlegen = ruf(employee, "/api/firmen", HttpMethod.POST, firma());
    final ResponseEntity<String> aendern = ruf(employee, "/api/firmen/1", HttpMethod.PUT, firma());

    // Then
    assertThat(anlegen.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(aendern.getStatusCode().is2xxSuccessful()).isTrue();
  }

  @Test
  void cLevel_givenTheSettingsAndTheAccounts_thenIsNotForbidden() {
    // When — C-Level darf alles; /api/mitarbeitende antwortet, solange es ihn nicht gibt, 404.
    final ResponseEntity<String> einstellungen =
        ruf(cLevel, "/api/rechnung/einstellungen", HttpMethod.GET, null);
    final ResponseEntity<String> konten = ruf(cLevel, MITARBEITENDE, HttpMethod.GET, null);

    // Then
    assertThat(einstellungen.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(konten.getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
  }

  /** Ein Aufruf: Methode und Pfad, jede Kennung schon durch {@code 1} ersetzt. */
  private record Weg(HttpMethod methode, String pfad) {
    @Override
    public String toString() {
      return methode + " " + pfad;
    }
  }

  /**
   * Jeder Weg unter {@code /api}, den die Controller des Kontexts anbieten, ohne die offenen Wege
   * und ohne die Verwaltung der Konten — die ist auch lesend zu (Stufe 1).
   */
  private Stream<Weg> wege() {
    return mappings.getHandlerMethods().keySet().stream()
        .flatMap(RollenZugangIT::wegeVon)
        .filter(weg -> weg.pfad().startsWith("/api/"))
        .filter(weg -> !OFFEN.contains(weg.pfad()))
        .filter(weg -> !weg.pfad().startsWith(MITARBEITENDE))
        .distinct();
  }

  private static Stream<Weg> wegeVon(final RequestMappingInfo info) {
    final Set<RequestMethod> angegeben = info.getMethodsCondition().getMethods();
    // Ohne Angabe nimmt das Mapping jede Methode an — dann zaehlt es als lesend und schreibend.
    final Set<RequestMethod> methoden =
        angegeben.isEmpty()
            ? Set.of(RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE)
            : angegeben;
    return Objects.requireNonNull(info.getPathPatternsCondition()).getPatternValues().stream()
        .map(pfad -> pfad.replaceAll("\\{[^}]+}", "1"))
        .flatMap(pfad -> methoden.stream().map(m -> new Weg(HttpMethod.valueOf(m.name()), pfad)));
  }

  private void istVerboten(final ResponseEntity<String> antwort) {
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(antwort.getHeaders().getContentType())
        .isNotNull()
        .satisfies(
            typ -> assertThat(typ.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isTrue());
    assertThat(detail(antwort)).isEqualTo(GlobalExceptionHandler.VERBOTEN_DETAIL);
  }

  private String detail(final ResponseEntity<String> antwort) {
    try {
      final JsonNode rumpf = json.readTree(Objects.requireNonNull(antwort.getBody()));
      return rumpf.path("detail").asText();
    } catch (final JsonProcessingException fehler) {
      throw new IllegalStateException("Kein JSON: " + antwort.getBody(), fehler);
    }
  }

  /**
   * Ein Fingerabdruck jeder Tabelle des Schemas: aendert ein Aufruf eine Zeile, aendert sich ihr
   * Abdruck. Die Tabellennamen stammen aus dem Katalog der Datenbank, nicht aus einer Eingabe.
   */
  private Map<String, String> bestand() {
    final List<String> tabellen =
        jdbc.queryForList(
            "SELECT tablename FROM pg_tables WHERE schemaname = current_schema()", String.class);
    final Map<String, String> abdruck = new TreeMap<>();
    for (final String tabelle : tabellen) {
      abdruck.put(
          tabelle,
          Objects.requireNonNull(
              jdbc.queryForObject(
                  "SELECT coalesce(md5(string_agg(t::text, '|' ORDER BY t::text)), '') FROM \""
                      + tabelle
                      + "\" t",
                  String.class)));
    }
    return abdruck;
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

  private static Map<String, Object> firma() {
    final Map<String, Object> rumpf = new LinkedHashMap<>();
    rumpf.put("name", "Adler AG");
    rumpf.put("strasse", "Hauptstrasse 1");
    rumpf.put("plz", "28195");
    rumpf.put("ort", "Bremen");
    return rumpf;
  }
}
