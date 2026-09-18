package org.mwolff.fbcrm.auth.application;

import java.time.Clock;
import java.util.Optional;
import org.mwolff.fbcrm.auth.AuthProperties;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.PasswordHasher;
import org.mwolff.fbcrm.auth.domain.SessionTokens;
import org.mwolff.fbcrm.auth.infrastructure.FailedLoginDelay;
import org.mwolff.fbcrm.auth.infrastructure.LoginAttemptLimiter;
import org.springframework.stereotype.Service;

/**
 * Anmelden mit E-Mail-Adresse und Passwort (K5).
 *
 * <p>Der Fehlerfall ist der interessante: Eine unbekannte Adresse und ein falsches Passwort fuehren
 * zur selben {@link LoginFailed} und dauern gleich lang. Dafuer wird auch bei unbekannter Adresse
 * ein Argon2-Durchlauf gegen einen Dummy-Hash gerechnet — ohne ihn antwortete die Instanz auf
 * unbekannte Adressen messbar frueher und verriete damit, welche Adressen sie kennt.
 *
 * <p><b>Bewusst ohne {@code @Transactional}:</b> Der Anwendungsfall liest genau einmal, und bei
 * einem Fehlschlag haelt er den Thread bis zu 800 ms an ({@link FailedLoginDelay}). Eine
 * Transaktion um das Ganze hielte waehrenddessen eine Datenbankverbindung fest, ohne etwas zu
 * schuetzen.
 */
@Service
public class LoginUseCase {

  /**
   * Gegen diesen Hash wird geprueft, wenn es die Adresse nicht gibt.
   *
   * <p>Das Passwort dahinter ist bedeutungslos und nirgends gueltig; gebraucht wird allein die
   * Rechenzeit des Verfahrens. Er entsteht einmal beim Hochfahren.
   */
  private static final String DUMMY_PASSWORD = "kein-konto-zu-dieser-adresse";

  private final AccountRepository accounts;
  private final PasswordHasher hasher;
  private final SessionTokens tokens;
  private final LoginAttemptLimiter limiter;
  private final FailedLoginDelay delay;
  private final AuthProperties properties;
  private final Clock clock;
  private final String dummyHash;

  public LoginUseCase(
      final AccountRepository accounts,
      final PasswordHasher hasher,
      final SessionTokens tokens,
      final LoginAttemptLimiter limiter,
      final FailedLoginDelay delay,
      final AuthProperties properties,
      final Clock clock) {
    this.accounts = accounts;
    this.hasher = hasher;
    this.tokens = tokens;
    this.limiter = limiter;
    this.delay = delay;
    this.properties = properties;
    this.clock = clock;
    this.dummyHash = hasher.hash(DUMMY_PASSWORD);
  }

  /**
   * Meldet ein Konto an.
   *
   * @param email die eingegebene Adresse
   * @param rawPassword das eingegebene Passwort
   * @param clientIp die Absender-Adresse fuer die Zaehlbremse (E9)
   * @throws TooManyLoginAttempts wenn im Fenster zu viele Fehlversuche stehen
   * @throws LoginFailed wenn Adresse oder Passwort nicht stimmen
   */
  public LoginResult login(final String email, final String rawPassword, final String clientIp) {
    if (!limiter.isAllowed(clientIp, email)) {
      throw new TooManyLoginAttempts();
    }

    final Optional<Account> konto = accounts.findByEmail(email);
    final boolean passt =
        hasher.matches(rawPassword, konto.map(Account::passwordHash).orElse(dummyHash));
    if (konto.isEmpty() || !passt) {
      limiter.recordFailure(clientIp, email);
      delay.apply();
      throw new LoginFailed();
    }

    final Account angemeldet = konto.get();
    limiter.recordSuccess(clientIp, email);
    return new LoginResult(angemeldet, cookie(angemeldet));
  }

  private SessionCookie cookie(final Account konto) {
    final String token =
        tokens.encode(
            konto.requireId(), konto.sessionGeneration(), clock.instant(), properties.sessionTtl());
    return new SessionCookie(
        properties.cookieName(), token, properties.sessionTtl(), properties.cookieSecure());
  }
}
