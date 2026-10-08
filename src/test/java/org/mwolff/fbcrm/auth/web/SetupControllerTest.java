package org.mwolff.fbcrm.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.auth.application.LoginResult;
import org.mwolff.fbcrm.auth.application.SessionCookie;
import org.mwolff.fbcrm.auth.application.SetupAccountUseCase;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.Role;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Die Uebersetzung zwischen Einrichtung und HTTP.
 *
 * <p>Der Controller entscheidet nichts: Ob der Schluessel stimmt und ob die Instanz noch frei ist,
 * beantwortet {@code SetupAccountUseCase}. Hier wird nur nachgewiesen, dass der Erfolg dasselbe
 * Session-Cookie setzt wie das Anmelden (K3) und dass die Statusauskunft genau ein Feld traegt
 * (E11).
 */
@ExtendWith(MockitoExtension.class)
class SetupControllerTest {

  private static final String MAIL = "manne@example.org";
  private static final String PASSWORT = "sicheres-passwort";
  private static final String SCHLUESSEL = "einmal-schluessel";
  private static final String TOKEN = "nutzlast.signatur";
  private static final Instant JETZT = Instant.parse("2026-09-18T10:00:00Z");
  private static final long KONTO_ID = 1L;

  @Mock private SetupAccountUseCase setup;

  private SetupController controller() {
    return new SetupController(setup, new SessionCookieFactory());
  }

  private static SetupRequest anfrage() {
    return new SetupRequest(MAIL, MAIL, "Manne", PASSWORT, SCHLUESSEL);
  }

  private static Account konto() {
    return new Account(KONTO_ID, MAIL, "Manne", "hash", Role.ADMIN, 0, JETZT, JETZT);
  }

  private ResponseEntity<AccountResponse> richteEin() {
    when(setup.initialize(MAIL, "Manne", PASSWORT, SCHLUESSEL))
        .thenReturn(
            new LoginResult(
                konto(), new SessionCookie("fbcrm_session", TOKEN, Duration.ofDays(1), true)));
    return controller().initialize(anfrage());
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
}
