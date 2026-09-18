package org.mwolff.fbcrm.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
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
   */
  private static final Set<String> OFFEN =
      Set.of("/api/auth/login", "/api/setup", "/api/setup/status", "/error");

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

  @Test
  void loginEndpoint_withoutASession_thenIsReachable() {
    // When — ohne offenen Anmeldepfad koennte sich niemand jemals anmelden.
    final HttpStatus antwort = status("/api/auth/login", HttpMethod.POST);

    // Then
    assertThat(antwort).isNotEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void setupEndpoint_withoutASession_thenIsReachable() {
    // When — ohne offenen Einrichtungspfad koennte eine frische Instanz nie eingerichtet werden.
    final HttpStatus antwort = status("/api/setup", HttpMethod.POST);

    // Then
    assertThat(antwort).isNotEqualTo(HttpStatus.UNAUTHORIZED);
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

  private HttpStatus status(final String pfad, final HttpMethod methode) {
    return HttpStatus.valueOf(
        rest.exchange(pfad, methode, null, String.class).getStatusCode().value());
  }
}
