package org.mwolff.fbcrm.auth.application;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import org.mwolff.fbcrm.auth.AuthProperties;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.SecretHasher;
import org.mwolff.fbcrm.auth.domain.SessionTokens;
import org.mwolff.fbcrm.auth.infrastructure.FailedLoginDelay;
import org.mwolff.fbcrm.auth.infrastructure.LoginAttemptLimiter;
import org.mwolff.fbcrm.common.NumericCodes;
import org.springframework.stereotype.Service;

/**
 * Anmelden mit E-Mail-Adresse und Code (Kriterium 2) und einen Code fuer die Anmeldung anfordern
 * (E8).
 *
 * <p>Der Fehlerfall ist der interessante: Eine unbekannte Adresse, ein falscher, ein abgelaufener
 * und ein verbrauchter Code fuehren zur selben {@link LoginFailed} und dauern gleich lang (E13,
 * Kriterium 3). Den gleichen Rechenaufwand stellt {@link CodeEinloesung} her, die Verzoegerung
 * danach {@link FailedLoginDelay}.
 *
 * <p><b>Bewusst ohne {@code @Transactional}:</b> Bei einem Fehlschlag haelt der Anwendungsfall den
 * Thread bis zu 800 ms an, und eine Transaktion um das Ganze hielte waehrenddessen eine
 * Datenbankverbindung fest. Vor allem aber muss die gezaehlte Fehleingabe bestehen bleiben: Eine
 * Transaktion, die mit {@link LoginFailed} zurueckrollt, naehme sie wieder weg, und der Code
 * verfiele nie. Jeder Schreibzugriff auf den Bestand bringt darum seine eigene Transaktion mit. Die
 * Anforderung des Codes braucht ebenfalls keine: Die Ausstellung bringt ihre eigene mit.
 */
@Service
public class LoginUseCase {

  private final AccountRepository accounts;
  private final SecretHasher hasher;
  private final SessionTokens tokens;
  private final LoginAttemptLimiter limiter;
  private final FailedLoginDelay delay;
  private final RequestLoginCodeUseCase ausstellung;
  private final CodeEinloesung einloesung;
  private final NumericCodes codes;
  private final AuthProperties properties;
  private final Clock clock;

  /*
   * PMD.ExcessiveParameterList: Zehn Mitspieler, weil Anmelden und Anfordern sich Bremse, Konten und
   * Rechenaufwand teilen. Getrennt stuende die Regel „erst zaehlen, dann nach dem Konto sehen" in
   * zwei Klassen, und genau ihre Gleichheit auf beiden Wegen ist die Zusage aus E13.
   */
  @SuppressWarnings("PMD.ExcessiveParameterList")
  public LoginUseCase(
      final AccountRepository accounts,
      final SecretHasher hasher,
      final SessionTokens tokens,
      final LoginAttemptLimiter limiter,
      final FailedLoginDelay delay,
      final RequestLoginCodeUseCase ausstellung,
      final CodeEinloesung einloesung,
      final NumericCodes codes,
      final AuthProperties properties,
      final Clock clock) {
    this.accounts = accounts;
    this.hasher = hasher;
    this.tokens = tokens;
    this.limiter = limiter;
    this.delay = delay;
    this.ausstellung = ausstellung;
    this.einloesung = einloesung;
    this.codes = codes;
    this.properties = properties;
    this.clock = clock;
  }

  /**
   * Meldet ein Konto mit dem Code aus der Mail oder dem Protokoll an.
   *
   * <p>Die Bremse je Herkunft greift vor allem anderen und zaehlt jede Eingabe (Kriterium 5). Erst
   * nach einem eingeloesten Code wird nach dem Konto gesehen: Ein Code ohne Konto — etwa der einer
   * noch laufenden Einrichtung — meldet niemanden an und endet in derselben Abweisung.
   *
   * @param email die eingegebene Adresse
   * @param code der eingegebene Code
   * @param clientIp die Absender-Adresse fuer die Zaehlbremse (E9)
   * @throws TooManyLoginAttempts wenn die Herkunft gesperrt ist, mit der Restwartezeit
   * @throws LoginFailed wenn der Code nicht stimmt, abgelaufen oder verbraucht ist oder es zu der
   *     Adresse kein Konto gibt — fuer jeden Fall dieselbe
   */
  public LoginResult login(final String email, final String code, final String clientIp) {
    einloesung.bremse(clientIp);
    final Optional<Account> konto =
        einloesung.loese(email, code, clientIp) ? accounts.findByEmail(email) : Optional.empty();
    if (konto.isEmpty()) {
      delay.apply();
      throw new LoginFailed();
    }
    final Account angemeldet = konto.get();
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

  private SessionCookie cookie(final Account konto) {
    final String token =
        tokens.encode(
            konto.requireId(), konto.sessionGeneration(), clock.instant(), properties.sessionTtl());
    return new SessionCookie(
        properties.cookieName(), token, properties.sessionTtl(), properties.cookieSecure());
  }
}
