package org.mwolff.fbcrm.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Die Zugangsregel (K1): Was nicht ausdruecklich offen ist, verlangt eine Sitzung.
 *
 * <p>Die Liste der geprueften Pfade entsteht aus dem {@link RequestMappingHandlerMapping} und nicht
 * aus einer Aufzaehlung im Test. Der Unterschied ist der Zweck der Probe: Eine Aufzaehlung deckt
 * nur ab, woran jemand gedacht hat — ein Endpunkt, der spaeter dazukommt und die Freigabe vergisst,
 * faellt hier von selbst auf.
 */
class AccessRuleIT extends AbstractIntegrationTest {

  /**
   * Was ohne Sitzung erreichbar sein <b>muss</b>.
   *
   * <p>{@code /error} gehoert dazu, weil Spring jede Fehlerlage intern dorthin weiterreicht; waere
   * der Pfad verschlossen, antwortete die Anwendung auf jeden Fehler mit 401.
   *
   * <p>Die beiden Pfade der Einrichtung stehen hier aus demselben Grund wie die Anmeldung: Eine
   * frische Instanz hat kein Konto, also kann niemand eine Sitzung vorweisen. Was sie schuetzt, ist
   * nicht die Zugangsregel, sondern der Einmal-Schluessel und das vorhandene Konto (K3, E5, E11).
   *
   * <p>Die beiden Anforderungen des Codes stehen hier, weil der Code der Weg in die Sitzung ist:
   * Wer ihn anfordert, hat noch keine. Was sie schuetzt, ist die Zaehlbremse, die vor jeder anderen
   * Pruefung zaehlt, und auf der Einrichtungsseite zusaetzlich der Einmal-Schluessel (E8).
   */
  private static final Set<String> OFFEN =
      Set.of(
          "/api/auth/login",
          "/api/auth/code",
          "/api/setup",
          "/api/setup/code",
          "/api/setup/status",
          "/error");

  private final TestRestTemplate rest;
  private final RequestMappingHandlerMapping mappings;

  @Autowired
  AccessRuleIT(
      final TestRestTemplate rest,
      // Der Actuator bringt eine zweite Bean desselben Typs mit; gemeint ist die der Controller.
      @Qualifier("requestMappingHandlerMapping") final RequestMappingHandlerMapping mappings) {
    this.rest = rest;
    this.mappings = mappings;
  }

  private List<String> geschuetztePfade() {
    return mappings.getHandlerMethods().keySet().stream()
        .map(RequestMappingInfo::getPathPatternsCondition)
        .filter(bedingung -> bedingung != null)
        .flatMap(bedingung -> bedingung.getPatternValues().stream())
        .distinct()
        .filter(pfad -> !OFFEN.contains(pfad))
        .filter(pfad -> !pfad.contains("{"))
        .toList();
  }

  @Test
  void anyRegisteredEndpoint_withoutASession_thenAnswersUnauthorized() {
    // Given
    final List<String> geschuetzt = geschuetztePfade();

    // When / Then — geprueft wird mit GET und POST, damit kein Endpunkt an der Methode vorbeikommt.
    assertThat(geschuetzt)
        .isNotEmpty()
        .allSatisfy(
            pfad -> {
              assertThat(status(pfad, HttpMethod.GET)).isEqualTo(HttpStatus.UNAUTHORIZED);
              assertThat(status(pfad, HttpMethod.POST)).isEqualTo(HttpStatus.UNAUTHORIZED);
            });
  }

  /**
   * Die offenen Pfade, die ohne Sitzung erreichbar sein muessen.
   *
   * <p>Der Grund steht je Zeile dabei: ohne offenen Anmeldepfad koennte sich niemand jemals
   * anmelden, ohne offenen Einrichtungspfad waere eine frische Instanz nie einzurichten, und wer
   * einen Code anfordert, hat noch keine Sitzung.
   */
  @ParameterizedTest(name = "POST {0} ohne Sitzung ist erreichbar — {1}")
  @CsvSource({
    "/api/auth/login, ohne diesen Pfad koennte sich niemand jemals anmelden",
    "/api/auth/code, wer einen Code anfordert, hat noch keine Sitzung",
    "/api/setup, ohne diesen Pfad waere eine frische Instanz nie einzurichten",
    "/api/setup/code, eine frische Instanz hat kein Konto und damit keine Sitzung"
  })
  void anOpenEndpoint_withoutASession_thenIsReachable(final String pfad, final String grund) {
    // When
    final HttpStatus antwort = status(pfad, HttpMethod.POST);

    // Then
    assertThat(antwort).as(grund).isNotEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void setupStatusEndpoint_withoutASession_thenIsReachable() {
    // When — E11: die unangemeldete Oberflaeche fragt hier, wohin sie fuehren soll.
    final HttpStatus antwort = status("/api/setup/status", HttpMethod.GET);

    // Then
    assertThat(antwort).isEqualTo(HttpStatus.OK);
  }

  @Test
  void health_withoutASession_thenStaysOpen() {
    // When — der Healthcheck des Containers kennt keine Anmeldung.
    final HttpStatus antwort = status("/actuator/health", HttpMethod.GET);

    // Then
    assertThat(antwort).isEqualTo(HttpStatus.OK);
  }

  @Test
  void info_withoutASession_thenAnswersUnauthorized() {
    // When — der Actuator-Info traegt die Version der laufenden Instanz.
    final HttpStatus antwort = status("/actuator/info", HttpMethod.GET);

    // Then
    assertThat(antwort).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void anUnknownPath_withoutASession_thenGetsTheApplicationItself() {
    // When — E20: eine SPA-Route traegt keine Daten, die Umleitung leistet ProtectedRoute.
    final ResponseEntity<String> antwort = rest.getForEntity("/irgendwas", String.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void anUnknownApiPath_withoutASession_thenIsNotTheApplication() {
    // When — E20: unter /api ist ein unbekannter Pfad wirklich unbekannt.
    final ResponseEntity<String> antwort = rest.getForEntity("/api/gibt-es-nicht", String.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  /**
   * Die Wege mit einer Kennung im Pfad.
   *
   * <p>Der generische Nachweis oben laesst jeden Pfad mit {@code {} } aus — eine Vorlage laesst
   * sich nicht aufrufen. Genau diese Wege blieben damit ungeprueft, obwohl sie die fachlichen Daten
   * tragen. Deshalb stehen sie hier als Zeilen ausgeschrieben; {@code GET} und {@code POST} auf
   * {@code /api/firmen} deckt der generische Fall ab.
   *
   * <p>Die Wege des Ansprechpartners liegen <b>vollstaendig</b> hinter einer Kennung — selbst das
   * Anlegen unter {@code /api/firmen} traegt die Kennung der Firma als Vorlage. Ohne diese Zeilen
   * waere die Zugangsregel fuer das ganze Paket ungeprueft.
   */
  @ParameterizedTest(name = "{1} {0} ohne Sitzung antwortet 401")
  @CsvSource({
    "/api/firmen/1, GET",
    "/api/firmen/1, PUT",
    "/api/firmen/1/stilllegen, POST",
    "/api/firmen/1/aktivieren, POST",
    "/api/firmen/1/ansprechpartner, POST",
    "/api/firmen/1/ansprechpartner/1, PUT"
  })
  void aPathWithAnId_withoutASession_thenAnswersUnauthorized(
      final String pfad, final String methode) {
    // When — die Methode wird hier umgewandelt und nicht von JUnit: HttpMethod ist kein Enum.
    final HttpStatus antwort = status(pfad, HttpMethod.valueOf(methode));

    // Then
    assertThat(antwort).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  private HttpStatus status(final String pfad, final HttpMethod methode) {
    return HttpStatus.valueOf(
        rest.exchange(pfad, methode, null, String.class).getStatusCode().value());
  }
}
