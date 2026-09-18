package org.mwolff.fbcrm.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.auth.AuthProperties;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.PasswordHasher;
import org.mwolff.fbcrm.auth.domain.Role;
import org.mwolff.fbcrm.auth.domain.SessionTokens;
import org.mwolff.fbcrm.auth.infrastructure.FailedLoginDelay;
import org.mwolff.fbcrm.auth.infrastructure.LoginAttemptLimiter;

/**
 * Anmelden (K5) — und vor allem, was beim Fehlschlag <b>nicht</b> nach aussen dringt.
 *
 * <p>Die Mocks sind hier von Hand gesetzt statt ueber {@code @InjectMocks}: Der Anwendungsfall
 * rechnet seinen Dummy-Hash schon im Konstruktor, und der muss feststehen, bevor er entsteht.
 */
class LoginUseCaseTest {

  private static final String MAIL = "manne@example.org";
  private static final String PASSWORT = "richtiges-passwort";
  private static final String HASH = "argon2-hash-des-kontos";
  private static final String DUMMY_HASH = "argon2-hash-ohne-konto";
  private static final String IP = "203.0.113.7";
  private static final Instant JETZT = Instant.parse("2026-09-18T10:00:00Z");
  private static final Duration LAUFZEIT = Duration.ofDays(1);
  private static final long KONTO_ID = 42L;
  private static final int GENERATION = 3;
  private static final String TOKEN = "token.signatur";

  private final AccountRepository accounts = mock(AccountRepository.class);
  private final PasswordHasher hasher = mock(PasswordHasher.class);
  private final SessionTokens tokens = mock(SessionTokens.class);
  private final LoginAttemptLimiter limiter = mock(LoginAttemptLimiter.class);
  private final FailedLoginDelay delay = mock(FailedLoginDelay.class);

  private LoginUseCase useCase;

  private static AuthProperties schalter() {
    return new AuthProperties(
        "geheimnis-mit-mindestens-32-zeichen-laenge",
        LAUFZEIT,
        "fbcrm_session",
        true,
        10,
        Duration.ofMinutes(15),
        List.of());
  }

  private static Account konto() {
    return new Account(KONTO_ID, MAIL, "Manne", HASH, Role.ADMIN, GENERATION, JETZT, JETZT);
  }

  @BeforeEach
  void baueDenAnwendungsfall() {
    when(hasher.hash(anyString())).thenReturn(DUMMY_HASH);
    useCase =
        new LoginUseCase(
            accounts,
            hasher,
            tokens,
            limiter,
            delay,
            schalter(),
            Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  private void erlaubeVersuch() {
    when(limiter.isAllowed(IP, MAIL)).thenReturn(true);
  }

  private void kontoVorhanden() {
    when(accounts.findByEmail(MAIL)).thenReturn(Optional.of(konto()));
  }

  private void kontoUnbekannt() {
    when(accounts.findByEmail(MAIL)).thenReturn(Optional.empty());
  }

  @Test
  void login_givenTheRightCredentials_thenReturnsTheAccount() {
    // Given
    erlaubeVersuch();
    kontoVorhanden();
    when(hasher.matches(PASSWORT, HASH)).thenReturn(true);
    when(tokens.encode(KONTO_ID, GENERATION, JETZT, LAUFZEIT)).thenReturn(TOKEN);

    // When
    final LoginResult ergebnis = useCase.login(MAIL, PASSWORT, IP);

    // Then
    assertThat(ergebnis.account()).isEqualTo(konto());
  }

  @Test
  void login_givenTheRightCredentials_thenPutsTheFreshTokenIntoTheCookie() {
    // Given
    erlaubeVersuch();
    kontoVorhanden();
    when(hasher.matches(PASSWORT, HASH)).thenReturn(true);
    when(tokens.encode(KONTO_ID, GENERATION, JETZT, LAUFZEIT)).thenReturn(TOKEN);

    // When
    final LoginResult ergebnis = useCase.login(MAIL, PASSWORT, IP);

    // Then — Name, Laufzeit und Secure-Merkmal kommen aus den Schaltern (E4).
    assertThat(ergebnis.cookie())
        .isEqualTo(new SessionCookie("fbcrm_session", TOKEN, LAUFZEIT, true));
  }

  @Test
  void login_givenTheRightCredentials_thenClearsTheAttemptCounters() {
    // Given
    erlaubeVersuch();
    kontoVorhanden();
    when(hasher.matches(PASSWORT, HASH)).thenReturn(true);
    when(tokens.encode(anyLong(), anyLong(), any(), any())).thenReturn(TOKEN);

    // When
    useCase.login(MAIL, PASSWORT, IP);

    // Then
    verify(limiter).recordSuccess(IP, MAIL);
  }

  @Test
  void login_givenTheRightCredentials_thenDoesNotDelayTheAnswer() {
    // Given
    erlaubeVersuch();
    kontoVorhanden();
    when(hasher.matches(PASSWORT, HASH)).thenReturn(true);
    when(tokens.encode(anyLong(), anyLong(), any(), any())).thenReturn(TOKEN);

    // When
    useCase.login(MAIL, PASSWORT, IP);

    // Then
    verify(delay, never()).apply();
  }

  @Test
  void login_givenAWrongPassword_thenFails() {
    // Given
    erlaubeVersuch();
    kontoVorhanden();
    when(hasher.matches(PASSWORT, HASH)).thenReturn(false);

    // When / Then
    assertThatThrownBy(() -> useCase.login(MAIL, PASSWORT, IP)).isInstanceOf(LoginFailed.class);
  }

  @Test
  void login_givenAnUnknownAddress_thenFailsWithTheSameException() {
    // Given — K5: kein anderer Typ, keine andere Meldung als beim falschen Passwort.
    erlaubeVersuch();
    kontoUnbekannt();
    when(hasher.matches(PASSWORT, DUMMY_HASH)).thenReturn(false);

    // When / Then
    assertThatThrownBy(() -> useCase.login(MAIL, PASSWORT, IP)).isInstanceOf(LoginFailed.class);
  }

  @Test
  void login_givenAnUnknownAddress_thenStillSpendsTheTimeOfAHashCheck() {
    // Given — ohne den Dummy-Hash antwortete die Instanz auf unbekannte Adressen frueher (K5).
    erlaubeVersuch();
    kontoUnbekannt();
    when(hasher.matches(PASSWORT, DUMMY_HASH)).thenReturn(false);

    // When
    assertThatThrownBy(() -> useCase.login(MAIL, PASSWORT, IP)).isInstanceOf(LoginFailed.class);

    // Then
    verify(hasher).matches(PASSWORT, DUMMY_HASH);
  }

  @Test
  void login_givenAnUnknownAddressWhoseDummyHashMatches_thenStillFails() {
    // Given — selbst wenn der Dummy-Hash je passte, entsteht daraus keine Sitzung.
    erlaubeVersuch();
    kontoUnbekannt();
    when(hasher.matches(PASSWORT, DUMMY_HASH)).thenReturn(true);

    // When / Then
    assertThatThrownBy(() -> useCase.login(MAIL, PASSWORT, IP)).isInstanceOf(LoginFailed.class);
  }

  @Test
  void login_givenAWrongPassword_thenCountsTheFailure() {
    // Given
    erlaubeVersuch();
    kontoVorhanden();
    when(hasher.matches(PASSWORT, HASH)).thenReturn(false);

    // When
    assertThatThrownBy(() -> useCase.login(MAIL, PASSWORT, IP)).isInstanceOf(LoginFailed.class);

    // Then
    verify(limiter).recordFailure(IP, MAIL);
  }

  @Test
  void login_givenAWrongPassword_thenDelaysTheAnswer() {
    // Given
    erlaubeVersuch();
    kontoVorhanden();
    when(hasher.matches(PASSWORT, HASH)).thenReturn(false);

    // When
    assertThatThrownBy(() -> useCase.login(MAIL, PASSWORT, IP)).isInstanceOf(LoginFailed.class);

    // Then
    verify(delay).apply();
  }

  @Test
  void login_givenTooManyFailuresInTheWindow_thenRefusesBeforeLookingUpTheAccount() {
    // Given
    when(limiter.isAllowed(IP, MAIL)).thenReturn(false);

    // When / Then
    assertThatThrownBy(() -> useCase.login(MAIL, PASSWORT, IP))
        .isInstanceOf(TooManyLoginAttempts.class);
  }

  @Test
  void login_givenTooManyFailuresInTheWindow_thenAsksTheRepositoryForNothing() {
    // Given — die Bremse soll die Last abwehren, nicht nur die Antwort aendern.
    when(limiter.isAllowed(IP, MAIL)).thenReturn(false);

    // When
    assertThatThrownBy(() -> useCase.login(MAIL, PASSWORT, IP))
        .isInstanceOf(TooManyLoginAttempts.class);

    // Then
    verify(accounts, never()).findByEmail(eq(MAIL));
  }
}
