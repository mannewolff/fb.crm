package org.mwolff.fbcrm.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.auth.Konten;
import org.mwolff.fbcrm.auth.application.LoginResult;
import org.mwolff.fbcrm.auth.application.SessionCookie;
import org.mwolff.fbcrm.auth.application.SetupAccountUseCase;
import org.mwolff.fbcrm.auth.domain.Account;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Die Uebersetzung zwischen Einrichtung und HTTP.
 *
 * <p>Der Controller entscheidet nichts: Ob der Schluessel stimmt und ob die Instanz noch frei ist,
 * beantwortet {@code SetupAccountUseCase}. Hier wird nur nachgewiesen, dass der Erfolg dasselbe
 * Session-Cookie setzt wie das Anmelden (K3), dass die Statusauskunft genau ein Feld traegt (E11)
 * und dass die Anforderung des Codes fuer jede Adresse mit 202 ohne Rumpf antwortet (E13, E17).
 */
@ExtendWith(MockitoExtension.class)
class SetupControllerTest {

  private static final String MAIL = "manne@example.org";
  private static final String CODE = "042424";
  private static final String SCHLUESSEL = "einmal-schluessel";
  private static final String TOKEN = "nutzlast.signatur";
  private static final Instant JETZT = Instant.parse("2026-09-18T10:00:00Z");
  private static final long KONTO_ID = 1L;
  private static final String IP = "203.0.113.7";

  @Mock private SetupAccountUseCase setup;
  @Mock private ClientIpResolver clientIp;

  private SetupController controller() {
    return new SetupController(setup, clientIp, new SessionCookieFactory());
  }

  private static SetupRequest anfrage() {
    return new SetupRequest(SCHLUESSEL, "Manne", MAIL, CODE);
  }

  private static Account konto() {
    return Konten.gespeichert(KONTO_ID, MAIL, 0, JETZT);
  }

  private ResponseEntity<AccountResponse> richteEin() {
    when(clientIp.resolve(any(MockHttpServletRequest.class))).thenReturn(IP);
    when(setup.initialize(MAIL, "Manne", CODE, SCHLUESSEL, IP))
        .thenReturn(
            new LoginResult(
                konto(), new SessionCookie("fbcrm_session", TOKEN, Duration.ofDays(1), true)));
    return controller().initialize(anfrage(), new MockHttpServletRequest());
  }

  @Test
  void initialize_thenAnswersWithStatusOk() {
    // When
    final ResponseEntity<AccountResponse> antwort = richteEin();

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void initialize_thenAnswersWithTheNewAccount() {
    // When
    final ResponseEntity<AccountResponse> antwort = richteEin();

    // Then — Id, Anzeigename und Adresse, sonst nichts.
    assertThat(antwort.getBody()).isEqualTo(new AccountResponse(KONTO_ID, "Manne", MAIL));
  }

  @Test
  void initialize_thenSetsTheSameSessionCookieAsALogin() {
    // When — K3: der Betreiber ist nach der Einrichtung angemeldet.
    final ResponseEntity<AccountResponse> antwort = richteEin();

    // Then
    assertThat(antwort.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
        .startsWith("fbcrm_session=" + TOKEN)
        .contains("HttpOnly")
        .contains("SameSite=Strict");
  }

  @Test
  void status_givenAnInstanceThatIsSetUp_thenSaysSo() {
    // Given
    when(setup.isInitialized()).thenReturn(true);

    // When / Then
    assertThat(controller().status()).isEqualTo(new SetupStatusResponse(true));
  }

  @Test
  void status_givenAFreshInstance_thenSaysSo() {
    // Given
    when(setup.isInitialized()).thenReturn(false);

    // When / Then
    assertThat(controller().status()).isEqualTo(new SetupStatusResponse(false));
  }

  @ParameterizedTest(name = "POST /api/setup/code fuer {0} antwortet 202 ohne Rumpf")
  @ValueSource(strings = {MAIL, "fremd@example.org"})
  void requestCode_forAnyAddress_thenAnswersAcceptedWithoutBody(final String adresse) {
    // Given
    when(clientIp.resolve(any(MockHttpServletRequest.class))).thenReturn(IP);

    // When
    final ResponseEntity<Void> antwort =
        controller()
            .requestCode(
                new SetupCodeRequest(adresse, "Manne", SCHLUESSEL), new MockHttpServletRequest());

    // Then
    assertThat(antwort).isEqualTo(ResponseEntity.accepted().build());
    assertThat(antwort.hasBody()).isFalse();
  }

  @Test
  void requestCode_thenPassesAddressNameKeyAndOrigin() {
    // Given
    when(clientIp.resolve(any(MockHttpServletRequest.class))).thenReturn(IP);

    // When
    controller()
        .requestCode(new SetupCodeRequest(MAIL, "Manne", SCHLUESSEL), new MockHttpServletRequest());

    // Then — die Herkunft braucht die Zaehlbremse (Kriterium 6, letzter Satz zu Kriterium 5).
    verify(setup).requestCode(MAIL, "Manne", SCHLUESSEL, IP);
  }
}
