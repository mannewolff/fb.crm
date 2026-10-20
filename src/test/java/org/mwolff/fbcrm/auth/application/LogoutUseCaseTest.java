package org.mwolff.fbcrm.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.auth.AuthProperties;
import org.mwolff.fbcrm.auth.Konten;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.AccountRepository;

/**
 * Abmelden (Kriterium 9, E7): die Generation des Kontos zaehlt hoch, das Cookie wird entwertet,
 * nicht erneuert.
 */
@ExtendWith(MockitoExtension.class)
class LogoutUseCaseTest {

  private static final Duration LAUFZEIT = Duration.ofDays(30);
  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");
  private static final Instant JETZT = Instant.parse("2026-10-20T12:00:00Z");
  private static final long KONTO_ID = 7L;

  @Mock private AccountRepository accounts;

  private LogoutUseCase useCase(final boolean secure) {
    return new LogoutUseCase(
        new AuthProperties(
            "geheimnis-mit-mindestens-32-zeichen-laenge",
            LAUFZEIT,
            Duration.ofHours(1),
            "fbcrm_session",
            secure,
            10,
            Duration.ofMinutes(15),
            5,
            Duration.ofHours(1),
            10,
            Duration.ofHours(24),
            Duration.ofMinutes(10),
            5,
            List.of()),
        accounts,
        Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  private void kontoMitGeneration(final int generation) {
    when(accounts.findById(KONTO_ID))
        .thenReturn(
            Optional.of(
                Konten.gespeichert(KONTO_ID, "manne@example.org", "hash", generation, ANGELEGT)));
  }

  private Account gespeichertes() {
    final ArgumentCaptor<Account> gespeichert = ArgumentCaptor.forClass(Account.class);
    verify(accounts).save(gespeichert.capture());
    return gespeichert.getValue();
  }

  @Test
  void logout_thenReturnsAnEmptyCookieThatExpiresImmediately() {
    // Given
    kontoMitGeneration(3);

    // When
    final SessionCookie cookie = useCase(true).logout(KONTO_ID);

    // Then
    assertThat(cookie).isEqualTo(new SessionCookie("fbcrm_session", "", Duration.ZERO, true));
  }

  @Test
  void logout_thenKeepsTheSecureFlagOfTheConfiguration() {
    // Given
    kontoMitGeneration(3);

    // When — sonst ersetzte das Abmelden ein Secure-Cookie durch ein unsicheres gleichen Namens.
    final SessionCookie cookie = useCase(false).logout(KONTO_ID);

    // Then
    assertThat(cookie.secure()).isFalse();
  }

  @Test
  void logout_thenIncrementsTheSessionGenerationOfTheAccount() {
    // Given — E7: jedes vorher ausgestellte Token traegt danach eine veraltete Generation.
    kontoMitGeneration(3);

    // When
    useCase(true).logout(KONTO_ID);

    // Then
    assertThat(gespeichertes().sessionGeneration()).isEqualTo(4);
  }

  @Test
  void logout_thenStampsTheChangeTimeFromTheClock() {
    // Given
    kontoMitGeneration(3);

    // When
    useCase(true).logout(KONTO_ID);

    // Then
    assertThat(gespeichertes().updatedAt()).isEqualTo(JETZT);
  }

  @Test
  void logout_givenAnAccountThatIsGone_thenStillInvalidatesTheCookie() {
    // Given
    when(accounts.findById(KONTO_ID)).thenReturn(Optional.empty());

    // When
    final SessionCookie cookie = useCase(true).logout(KONTO_ID);

    // Then
    assertThat(cookie.maxAge()).isEqualTo(Duration.ZERO);
  }

  @Test
  void logout_givenAnAccountThatIsGone_thenSavesNothing() {
    // Given
    when(accounts.findById(KONTO_ID)).thenReturn(Optional.empty());

    // When
    useCase(true).logout(KONTO_ID);

    // Then
    verify(accounts, never()).save(any());
  }
}
