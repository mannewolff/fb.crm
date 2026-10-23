package org.mwolff.fbcrm.auth.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.mwolff.fbcrm.auth.AuthProperties;
import org.mwolff.fbcrm.auth.SetupProperties;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.Role;
import org.mwolff.fbcrm.auth.domain.SessionTokens;
import org.mwolff.fbcrm.auth.infrastructure.FailedLoginDelay;
import org.mwolff.fbcrm.auth.infrastructure.LoginAttemptLimiter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Die Einrichtung der frischen Instanz per Einmal-Schluessel und Code (K3, E5, E9).
 *
 * <p>Eine frische Instanz hat kein Konto — und ohne Konto keinen Weg hinein. Der Einmal-Schluessel
 * aus {@code FBCRM_BOOTSTRAP_ADMIN_TOKEN} oeffnet diesen einen Weg, und zwar genau einmal. Er geht
 * in zwei Schritten: Mit {@link #requestCode} kommt ein Code an die eingegebene Adresse, mit {@link
 * #initialize} entstehen Konto und Sitzung, der Betreiber ist danach unmittelbar angemeldet. Das
 * erste Konto ist ein aktives C-Level-Konto; nach einer Rolle fragt die Einrichtung nicht.
 *
 * <p><b>Kein Zwischenzustand</b> (E9). Die Bestaetigung bringt Schluessel, Anzeigename und Adresse
 * erneut mit, und der Schluessel wird im Augenblick der Kontoanlage ein zweites Mal geprueft. In
 * der Datenbank steht zwischen den Schritten nur der Code — wer sich bei der Adresse vertippt,
 * bekommt bei eingeschaltetem Mailversand keinen und hinterlaesst kein Konto (Kriterium 6).
 *
 * <p><b>Drei Riegel, eine Antwort.</b> Falscher Schluessel, bereits eingerichtete Instanz und
 * verlorenes Wettrennen fuehren zur selben {@link SetupRefused}. Ein Unterschied — anderer Status,
 * anderer Text — verriete, ob der geratene Schluessel richtig war.
 *
 * <p><b>Der leere Schluessel.</b> {@code docker-compose.yml} Z. 47 f. sagt „leer = deaktiviert" zu.
 * Ein konstanter Vergleich gegen den Leerstring naehme aber genau die leere Eingabe an. Deshalb
 * wird bei leerem konfiguriertem Wert <b>gar nicht erst verglichen</b>, sondern jeder Aufruf
 * abgewiesen.
 *
 * <p><b>Das Wettrennen</b> (Plan #414, A5). Der Riegel ist „es existiert irgendein Konto" ({@link
 * AccountRepository#existsAnyAccount()}). Zwei gleichzeitige Aufrufe mit <b>derselben</b> Adresse
 * trennt der eindeutige Index {@code account_email_key} auf {@code lower(email)}; seine Verletzung
 * wird hier in dieselbe Abweisung uebersetzt wie „Konto existiert bereits". Zwei gleichzeitige
 * Aufrufe mit <b>verschiedenen</b> Adressen und richtigem Einmal-Schluessel kommen beide durch und
 * enden in zwei C-Level-Konten desselben Betreibers — fachlich erlaubt, seit mehrere Konten
 * dazugehoeren; der fruehere Index {@code account_single_admin}, der das schloss, trug die Zusage
 * „genau ein Konto" und ist mit V30 entfallen. Danach wirkt der Schluessel nicht mehr, und wer ihn
 * erraet, konnte schon vorher ein Konto anlegen.
 *
 * <p><b>Zuerst wird gezaehlt</b>, in beiden Schritten, und erst danach der Schluessel geprueft: Ein
 * vor dem Zaehlen gepruefter Schluessel liesse die Einrichtungsseite ungebremst (Kriterium 6,
 * letzter Satz zu Kriterium 5). Den Code prueft {@link CodeEinloesung} nach derselben Regel wie bei
 * der Anmeldung — fuenf Fehleingaben, und er verfaellt.
 */
@Service
public class SetupAccountUseCase {

  private final AccountRepository accounts;
  private final SessionTokens tokens;
  private final LoginAttemptLimiter limiter;
  private final FailedLoginDelay delay;
  private final RequestLoginCodeUseCase ausstellung;
  private final CodeEinloesung einloesung;
  private final AuthProperties authProperties;
  private final SetupProperties setupProperties;
  private final Clock clock;

  public SetupAccountUseCase(
      final AccountRepository accounts,
      final SessionTokens tokens,
      final LoginAttemptLimiter limiter,
      final FailedLoginDelay delay,
      final RequestLoginCodeUseCase ausstellung,
      final CodeEinloesung einloesung,
      final AuthProperties authProperties,
      final SetupProperties setupProperties,
      final Clock clock) {
    this.accounts = accounts;
    this.tokens = tokens;
    this.limiter = limiter;
    this.delay = delay;
    this.ausstellung = ausstellung;
    this.einloesung = einloesung;
    this.authProperties = authProperties;
    this.setupProperties = setupProperties;
    this.clock = clock;
  }

  /** Ob die Instanz bereits eingerichtet ist (E11). */
  @Transactional(readOnly = true)
  public boolean isInitialized() {
    return accounts.existsAnyAccount();
  }

  /**
   * Richtet die Instanz mit dem Code der Einrichtung ein und meldet den Betreiber an (E9).
   *
   * <p>Die Reihenfolge ist die Zusage: erst die Bremse je Herkunft, dann Schluessel und freie
   * Instanz, zuletzt der Code. Ein falscher Schluessel endet damit wie bisher in {@link
   * SetupRefused}, ohne den Code anzuruehren; ein falscher Code in derselben {@link LoginFailed}
   * wie bei der Anmeldung, weil fuer ihn derselbe Schritt hilft — ein neuer Code.
   *
   * <p><b>Bewusst ohne {@code @Transactional}</b>, aus demselben Grund wie bei {@link
   * LoginUseCase}: Eine Transaktion, die mit {@link LoginFailed} zurueckrollt, naehme die gezaehlte
   * Fehleingabe wieder weg. Das Konto entsteht in der Transaktion seines Speicherns; das verlorene
   * Wettrennen an {@code account_email_key} faengt {@link #speichere} wie bisher.
   *
   * @param email die Anmeldeadresse des ersten Kontos — dieselbe, an die der Code ging
   * @param displayName der Anzeigename
   * @param code der eingegebene Code
   * @param presentedKey der vorgelegte Einmal-Schluessel
   * @param clientIp die Absender-Adresse fuer die Zaehlbremse
   * @throws TooManyLoginAttempts wenn die Herkunft gesperrt ist — vor jeder Pruefung des
   *     Schluessels
   * @throws SetupRefused wenn der Schluessel nicht stimmt oder die Instanz schon eingerichtet ist
   * @throws LoginFailed wenn der Code nicht stimmt, abgelaufen oder verbraucht ist
   */
  public LoginResult initialize(
      final String email,
      final String displayName,
      final String code,
      final String presentedKey,
      final String clientIp) {
    einloesung.bremse(clientIp);
    if (!schluesselStimmt(presentedKey) || accounts.existsAnyAccount()) {
      throw new SetupRefused();
    }
    if (!einloesung.loese(email, code, clientIp)) {
      delay.apply();
      throw new LoginFailed();
    }

    final Instant jetzt = clock.instant();
    final Account neu = new Account(null, email, displayName, Role.C_LEVEL, true, 0, jetzt, jetzt);

    final Account angelegt = speichere(neu);
    return new LoginResult(angelegt, cookie(angelegt, jetzt));
  }

  /**
   * Fordert den Code der Einrichtung an (E8).
   *
   * <p>Ohne {@code @Transactional}: Die Ausstellung bringt ihre eigene Transaktion mit, und davor
   * wird nur gezaehlt und einmal gelesen.
   *
   * @param email die Adresse, an die der Code geht und unter der das Konto entstehen soll
   * @param displayName der Anzeigename, mit dem die Mail anredet
   * @param presentedKey der vorgelegte Einmal-Schluessel
   * @param clientIp die Absender-Adresse fuer die Zaehlbremse
   * @throws TooManyLoginAttempts wenn eine Zaehlart sperrt — vor jeder Pruefung des Schluessels
   * @throws SetupRefused wenn der Schluessel nicht stimmt oder die Instanz schon eingerichtet ist
   */
  public void requestCode(
      final String email,
      final String displayName,
      final String presentedKey,
      final String clientIp) {
    final Duration warten = limiter.zaehleAnforderung(clientIp, email);
    if (!warten.isZero()) {
      throw new TooManyLoginAttempts(warten);
    }
    if (!schluesselStimmt(presentedKey) || accounts.existsAnyAccount()) {
      throw new SetupRefused();
    }
    ausstellung.ausstellen(email, displayName);
  }

  /**
   * Vergleicht den vorgelegten Schluessel in konstanter Zeit.
   *
   * <p>Bei leerem konfiguriertem Wert wird nicht verglichen, sondern abgewiesen — sonst waere
   * „deaktiviert" eine Einladung mit leerer Eingabe.
   */
  private boolean schluesselStimmt(final String presentedKey) {
    final String konfiguriert = setupProperties.bootstrapToken();
    // Der Kurzschluss vor dem Vergleich ist die Zusage: Bei leerem Wert kommt MessageDigest.isEqual
    // gar nicht erst zum Zug, und eine leere Eingabe trifft auf keinen leeren Sollwert.
    return !konfiguriert.isEmpty()
        && MessageDigest.isEqual(
            konfiguriert.getBytes(StandardCharsets.UTF_8),
            presentedKey.getBytes(StandardCharsets.UTF_8));
  }

  private Account speichere(final Account neu) {
    try {
      return accounts.save(neu);
    } catch (final DataIntegrityViolationException wettrennenVerloren) {
      throw new SetupRefused(wettrennenVerloren);
    }
  }

  private SessionCookie cookie(final Account konto, final Instant jetzt) {
    final String token =
        tokens.encode(
            konto.requireId(), konto.sessionGeneration(), jetzt, authProperties.sessionTtl());
    return new SessionCookie(
        authProperties.cookieName(),
        token,
        authProperties.sessionTtl(),
        authProperties.cookieSecure());
  }
}
