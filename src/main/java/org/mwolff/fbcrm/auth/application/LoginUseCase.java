package org.mwolff.fbcrm.auth.application;

import static org.mwolff.fbcrm.auth.infrastructure.LoginAttemptLimiter.Zaehlart.ADRESSE_STUNDE;
import static org.mwolff.fbcrm.auth.infrastructure.LoginAttemptLimiter.Zaehlart.HERKUNFT;

import java.time.Clock;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.mwolff.fbcrm.auth.AuthProperties;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.SecretHasher;
import org.mwolff.fbcrm.auth.domain.SessionTokens;
import org.mwolff.fbcrm.auth.infrastructure.FailedLoginDelay;
import org.mwolff.fbcrm.auth.infrastructure.LoginAttemptLimiter;
import org.mwolff.fbcrm.auth.infrastructure.LoginAttemptLimiter.Zaehlart;
import org.mwolff.fbcrm.common.NumericCodes;
import org.mwolff.fbcrm.common.SecureTokens;
import org.springframework.stereotype.Service;

/**
 * Anmelden mit E-Mail-Adresse und Passwort (K5) und einen Code fuer die Anmeldung anfordern (E8).
 *
 * <p>Der Fehlerfall ist der interessante: Eine unbekannte Adresse und ein falsches Passwort fuehren
 * zur selben {@link LoginFailed} und dauern gleich lang. Dafuer wird auch bei unbekannter Adresse
 * ein Argon2-Durchlauf gegen einen Dummy-Hash gerechnet — ohne ihn antwortete die Instanz auf
 * unbekannte Adressen messbar frueher und verriete damit, welche Adressen sie kennt.
 *
 * <p><b>Bewusst ohne {@code @Transactional}:</b> Der Anwendungsfall liest genau einmal, und bei
 * einem Fehlschlag haelt er den Thread bis zu 800 ms an ({@link FailedLoginDelay}). Eine
 * Transaktion um das Ganze hielte waehrenddessen eine Datenbankverbindung fest, ohne etwas zu
 * schuetzen. Die Anforderung des Codes braucht ebenfalls keine: Die Ausstellung bringt ihre eigene
 * mit.
 */
@Service
public class LoginUseCase {

  private final AccountRepository accounts;
  private final SecretHasher hasher;
  private final SessionTokens tokens;
  private final LoginAttemptLimiter limiter;
  private final FailedLoginDelay delay;
  private final RequestLoginCodeUseCase ausstellung;
  private final NumericCodes codes;
  private final AuthProperties properties;
  private final Clock clock;

  /**
   * Gegen diesen Hash wird geprueft, wenn es die Adresse nicht gibt.
   *
   * <p>Er entsteht einmal beim Hochfahren aus einem frischen Zufallswert. Was dahintersteckt, ist
   * bedeutungslos und nirgends gueltig — gebraucht wird allein die Rechenzeit des Verfahrens. Darum
   * steht hier auch kein fester Wert: Der waere ein Passwort im Quellcode, und dieselbe Rechenzeit
   * kostet ein zufaelliger genauso.
   */
  private final String dummyHash;

  public LoginUseCase(
      final AccountRepository accounts,
      final SecretHasher hasher,
      final SessionTokens tokens,
      final LoginAttemptLimiter limiter,
      final FailedLoginDelay delay,
      final RequestLoginCodeUseCase ausstellung,
      final NumericCodes codes,
      final AuthProperties properties,
      final Clock clock) {
    this.accounts = accounts;
    this.hasher = hasher;
    this.tokens = tokens;
    this.limiter = limiter;
    this.delay = delay;
    this.ausstellung = ausstellung;
    this.codes = codes;
    this.properties = properties;
    this.clock = clock;
    this.dummyHash = hasher.hash(new SecureTokens().newToken());
  }

  /**
   * Meldet ein Konto an.
   *
   * @param email die eingegebene Adresse
   * @param rawPassword das eingegebene Passwort
   * @param clientIp die Absender-Adresse fuer die Zaehlbremse (E9)
   * @throws TooManyLoginAttempts wenn im Fenster zu viele Fehlversuche stehen, mit der
   *     Restwartezeit
   * @throws LoginFailed wenn Adresse oder Passwort nicht stimmen
   */
  public LoginResult login(final String email, final String rawPassword, final String clientIp) {
    final Duration warten = wartezeit(clientIp, email);
    if (!warten.isZero()) {
      throw new TooManyLoginAttempts(warten);
    }

    final Optional<Account> konto = accounts.findByEmail(email);
    final boolean passt =
        hasher.matches(rawPassword, konto.map(Account::passwordHash).orElse(dummyHash));
    if (konto.isEmpty() || !passt) {
      limiter.record(HERKUNFT, clientIp);
      limiter.record(ADRESSE_STUNDE, email);
      delay.apply();
      throw new LoginFailed();
    }

    final Account angemeldet = konto.get();
    limiter.recordSuccess(clientIp);
    return new LoginResult(angemeldet, cookie(angemeldet));
  }

  /**
   * Fordert einen Code fuer die Anmeldung an — mit derselben Antwort fuer jede Adresse (E13).
   *
   * <p><b>Zuerst wird gezaehlt</b>, erst danach nach dem Konto gesehen (E8). Andersherum waere die
   * sechste Anforderung in der Stunde die Auskunft darueber, ob es das Konto gibt: Nur die Adresse
   * des Kontos liefe in die Bremse. Eine abgewiesene Anforderung stellt nichts aus, der bisherige
   * Code gilt weiter (E12).
   *
   * <p><b>Ohne Konto</b> entsteht weder eine Zeile noch eine Mail (Kriterium 3), aber derselbe
   * Rechenaufwand: Die Ausstellung rechnet einen Argon2-Durchlauf, also rechnet ihn auch die
   * unbekannte Adresse — gegen einen erzeugten und sofort verworfenen Code. Ohne ihn waere die
   * Laufzeit der Antwort ein von aussen messbares Orakel auf das Konto.
   *
   * <p>Der Code geht an die Adresse, die das Konto traegt, nicht an die eingegebene: Gefunden wird
   * ohne Ruecksicht auf die Schreibweise, zugestellt wird an die eine bekannte Adresse.
   *
   * @param email die eingegebene Adresse
   * @param clientIp die Absender-Adresse fuer die Zaehlbremse (E9)
   * @throws TooManyLoginAttempts wenn eine Zaehlart sperrt, mit der Restwartezeit — fuer jede
   *     Adresse gleich
   */
  public void codeAnfordern(final String email, final String clientIp) {
    final Duration warten = limiter.zaehleAnforderung(clientIp, email);
    if (!warten.isZero()) {
      throw new TooManyLoginAttempts(warten);
    }
    accounts
        .findByEmail(email)
        .ifPresentOrElse(
            konto -> ausstellung.ausstellen(konto.email(), konto.displayName()),
            this::rechneWieEineAusstellung);
  }

  /** Ein Argon2-Durchlauf, dessen Ergebnis niemand braucht — allein die Rechenzeit zaehlt (E13). */
  private void rechneWieEineAusstellung() {
    hasher.hash(codes.newCode());
  }

  /**
   * Die Wartezeit der Bremse, null, wenn keine Zaehlart sperrt.
   *
   * <p>Bis zur Umstellung auf den Code zaehlt die Passwortanmeldung ihre Fehlversuche je Herkunft
   * und je Adresse in der Stunde. Sperren beide, gilt die laengere Wartezeit — wer nach der
   * kuerzeren wiederkaeme, liefe in die zweite Bremse.
   */
  private Duration wartezeit(final String clientIp, final String email) {
    return Collections.max(List.of(sperre(HERKUNFT, clientIp), sperre(ADRESSE_STUNDE, email)));
  }

  /**
   * Die Restwartezeit einer Zaehlart, null, wenn sie zulaesst.
   *
   * <p>Ist das Fenster zwischen der Frage und der Restwartezeit abgelaufen, ist die Restwartezeit
   * ebenfalls null — und der Versuch darf durch, weil die Sperre dann tatsaechlich vorbei ist.
   */
  private Duration sperre(final Zaehlart art, final String schluessel) {
    return limiter.isAllowed(art, schluessel)
        ? Duration.ZERO
        : limiter.restwartezeit(art, schluessel);
  }

  private SessionCookie cookie(final Account konto) {
    final String token =
        tokens.encode(
            konto.requireId(), konto.sessionGeneration(), clock.instant(), properties.sessionTtl());
    return new SessionCookie(
        properties.cookieName(), token, properties.sessionTtl(), properties.cookieSecure());
  }
}
