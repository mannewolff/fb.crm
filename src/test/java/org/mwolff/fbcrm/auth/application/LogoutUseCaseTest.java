package org.mwolff.fbcrm.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.auth.AuthProperties;

/** Abmelden (K9): das Cookie wird entwertet, nicht erneuert. */
class LogoutUseCaseTest {

  private static final Duration LAUFZEIT = Duration.ofDays(1);

  private static LogoutUseCase useCase(final boolean secure) {
    return new LogoutUseCase(
        new AuthProperties(
            "geheimnis-mit-mindestens-32-zeichen-laenge",
            LAUFZEIT,
            Duration.ofHours(1),
            "fbcrm_session",
            secure,
            10,
            Duration.ofMinutes(15),
            List.of()));
  }

  @Test
  void logout_thenReturnsAnEmptyCookieThatExpiresImmediately() {
    // When
    final SessionCookie cookie = useCase(true).logout();

    // Then
    assertThat(cookie).isEqualTo(new SessionCookie("fbcrm_session", "", Duration.ZERO, true));
  }

  @Test
  void logout_thenKeepsTheSecureFlagOfTheConfiguration() {
    // When — sonst ersetzte das Abmelden ein Secure-Cookie durch ein unsicheres gleichen Namens.
    final SessionCookie cookie = useCase(false).logout();

    // Then
    assertThat(cookie.secure()).isFalse();
  }
}
