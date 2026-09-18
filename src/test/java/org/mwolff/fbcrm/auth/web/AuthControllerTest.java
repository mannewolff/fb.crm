package org.mwolff.fbcrm.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.auth.application.GetCurrentAccountUseCase;
import org.mwolff.fbcrm.auth.application.LoginResult;
import org.mwolff.fbcrm.auth.application.LoginUseCase;
import org.mwolff.fbcrm.auth.application.LogoutUseCase;
import org.mwolff.fbcrm.auth.application.SessionCookie;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.Role;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Die Uebersetzung zwischen Anwendungsfall und HTTP.
 *
 * <p>Der Schwerpunkt liegt auf dem Cookie: Genau hier entscheidet sich, ob es {@code HttpOnly},
 * {@code SameSite=Strict} und {@code Max-Age} traegt — und damit, ob Kriterium K8 und die
 * CSRF-Festigkeit aus {@code CLAUDE-security.md} halten.
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

  private static final String MAIL = "manne@example.org";
  private static final String PASSWORT = "richtiges-passwort";
  private static final String TOKEN = "token.signatur";
  private static final String IP = "203.0.113.7";
  private static final Instant JETZT = Instant.parse("2026-09-18T10:00:00Z");
  private static final long KONTO_ID = 42L;

  @Mock private LoginUseCase loginUseCase;
  @Mock private LogoutUseCase logoutUseCase;
  @Mock private GetCurrentAccountUseCase currentAccount;
  @Mock private ClientIpResolver clientIp;

  private AuthController controller;

  /*
   * Die Cookie-Fabrik ist ein eigener reiner Dienst ohne I/O und wird deshalb echt eingesetzt,
   * nicht gemockt (CLAUDE-java.md §4). Ein Mock an dieser Stelle pruefte nur, dass der Controller
   * irgendetwas delegiert — die Eigenschaften des Cookies weist SessionCookieFactoryTest nach,
   * und die Tests hier sehen sie durch die echte Fabrik hindurch.
   */
  @BeforeEach
  void baueDenController() {
    controller =
        new AuthController(
            loginUseCase, logoutUseCase, currentAccount, clientIp, new SessionCookieFactory());
  }

  private static Account konto() {
    return new Account(KONTO_ID, MAIL, "Manne", "hash", Role.ADMIN, 3, JETZT, JETZT);
  }

  private static LoginRequest anfrage() {
    return new LoginRequest(MAIL, PASSWORT);
  }

  private String setCookie(final ResponseEntity<?> antwort) {
    return String.valueOf(antwort.getHeaders().getFirst(HttpHeaders.SET_COOKIE));
  }

  private ResponseEntity<AccountResponse> meldeAn(final SessionCookie cookie) {
    when(clientIp.resolve(any(MockHttpServletRequest.class))).thenReturn(IP);
    when(loginUseCase.login(MAIL, PASSWORT, IP)).thenReturn(new LoginResult(konto(), cookie));
    return controller.login(anfrage(), new MockHttpServletRequest());
  }

  private static SessionCookie sitzungsCookie() {
    return new SessionCookie("fbcrm_session", TOKEN, Duration.ofDays(1), true);
  }

  @Test
  void login_thenAnswersWithTheAccountBehindTheSession() {
    // When
    final ResponseEntity<AccountResponse> antwort = meldeAn(sitzungsCookie());

    // Then — Id, Anzeigename und Adresse, sonst nichts.
    assertThat(antwort.getBody()).isEqualTo(new AccountResponse(KONTO_ID, "Manne", MAIL));
  }

  @Test
  void login_thenAnswersWithStatusOk() {
    // When
    final ResponseEntity<AccountResponse> antwort = meldeAn(sitzungsCookie());

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void login_thenSetsTheSessionCookieWithItsToken() {
    // When
    final ResponseEntity<AccountResponse> antwort = meldeAn(sitzungsCookie());

    // Then
    assertThat(setCookie(antwort)).startsWith("fbcrm_session=" + TOKEN);
  }

  @Test
  void login_thenSetsTheCookieHttpOnlySecureStrictAndForTheWholeApplication() {
    // When
    final ResponseEntity<AccountResponse> antwort = meldeAn(sitzungsCookie());

    // Then
    assertThat(setCookie(antwort))
        .contains("HttpOnly")
        .contains("Secure")
        .contains("SameSite=Strict")
        .contains("Path=/");
  }

  @Test
  void login_thenGivesTheCookieAMaxAgeOfTheTokenLifetime() {
    // When — K8, E4: ohne Max-Age endete die Anmeldung mit dem Schliessen des Browsers.
    final ResponseEntity<AccountResponse> antwort = meldeAn(sitzungsCookie());

    // Then
    assertThat(setCookie(antwort)).contains("Max-Age=86400");
  }

  @Test
  void login_givenAConfigurationWithoutSecure_thenOmitsTheSecureFlag() {
    // When — im lokalen Betrieb ohne TLS.
    final ResponseEntity<AccountResponse> antwort =
        meldeAn(new SessionCookie("fbcrm_session", TOKEN, Duration.ofDays(1), false));

    // Then
    assertThat(setCookie(antwort)).doesNotContain("Secure");
  }

  @Test
  void logout_thenAnswersWithoutContent() {
    // Given
    when(logoutUseCase.logout())
        .thenReturn(new SessionCookie("fbcrm_session", "", Duration.ZERO, true));

    // When
    final ResponseEntity<Void> antwort = controller.logout();

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  void logout_thenInvalidatesTheCookieImmediately() {
    // Given
    when(logoutUseCase.logout())
        .thenReturn(new SessionCookie("fbcrm_session", "", Duration.ZERO, true));

    // When
    final ResponseEntity<Void> antwort = controller.logout();

    // Then — K9: leerer Wert und Max-Age=0.
    assertThat(setCookie(antwort)).startsWith("fbcrm_session=;").contains("Max-Age=0");
  }

  @Test
  void me_thenAnswersWithTheAccountOfTheSession() {
    // Given
    when(currentAccount.byId(KONTO_ID)).thenReturn(konto());

    // When
    final AccountResponse antwort = controller.me(KONTO_ID);

    // Then
    assertThat(antwort).isEqualTo(new AccountResponse(KONTO_ID, "Manne", MAIL));
  }
}
