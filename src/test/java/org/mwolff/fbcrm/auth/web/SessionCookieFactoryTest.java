package org.mwolff.fbcrm.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.auth.application.SessionCookie;

/**
 * Die eine Stelle, an der das Session-Cookie seine Eigenschaften bekommt.
 *
 * <p>Sie existiert, damit Anmelden und Einrichten wirklich dasselbe Cookie setzen: Zwei Controller
 * mit je eigenem Bauplan wuerden frueher oder spaeter auseinanderlaufen, und die Abweichung faende
 * niemand, weil beide fuer sich gruen waeren (K8, E4, CLAUDE-security.md).
 */
class SessionCookieFactoryTest {

  private static final String TOKEN = "nutzlast.signatur";

  private final SessionCookieFactory fabrik = new SessionCookieFactory();

  private static SessionCookie beschreibung(final boolean secure, final Duration laufzeit) {
    return new SessionCookie("fbcrm_session", TOKEN, laufzeit, secure);
  }

  @Test
  void header_thenCarriesNameAndToken() {
    // When / Then
    assertThat(fabrik.header(beschreibung(true, Duration.ofDays(1))))
        .startsWith("fbcrm_session=" + TOKEN);
  }

  @Test
  void header_thenHidesTheCookieFromJavaScript() {
    // When / Then — ohne HttpOnly liest jedes eingeschleuste Skript die Sitzung mit.
    assertThat(fabrik.header(beschreibung(true, Duration.ofDays(1)))).contains("HttpOnly");
  }

  @Test
  void header_thenMakesTheCookieCsrfProof() {
    // When / Then — SameSite=Strict ersetzt das CSRF-Token (siehe SecurityConfig).
    assertThat(fabrik.header(beschreibung(true, Duration.ofDays(1)))).contains("SameSite=Strict");
  }

  @Test
  void header_thenLetsTheCookieCountForTheWholeApplication() {
    // When / Then
    assertThat(fabrik.header(beschreibung(true, Duration.ofDays(1)))).contains("Path=/");
  }

  @Test
  void header_givenALifetime_thenSurvivesTheBrowserBeingClosed() {
    // When / Then — K8, E4: ohne Max-Age endete die Sitzung mit dem Fenster.
    assertThat(fabrik.header(beschreibung(true, Duration.ofDays(1)))).contains("Max-Age=86400");
  }

  @Test
  void header_givenSecure_thenSendsTheCookieOnlyOverHttps() {
    // When / Then
    assertThat(fabrik.header(beschreibung(true, Duration.ofDays(1)))).contains("Secure");
  }

  @Test
  void header_givenNoSecure_thenOmitsTheFlag() {
    // When / Then — im lokalen Betrieb ohne TLS.
    assertThat(fabrik.header(beschreibung(false, Duration.ofDays(1)))).doesNotContain("Secure");
  }

  @Test
  void header_givenAZeroLifetime_thenInvalidatesTheCookie() {
    // When / Then — K9: so entwertet das Abmelden die Sitzung im Browser.
    assertThat(fabrik.header(new SessionCookie("fbcrm_session", "", Duration.ZERO, true)))
        .startsWith("fbcrm_session=;")
        .contains("Max-Age=0");
  }
}
