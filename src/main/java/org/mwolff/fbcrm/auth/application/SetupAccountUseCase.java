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
import org.mwolff.fbcrm.auth.domain.SecretHasher;
import org.mwolff.fbcrm.auth.domain.SessionTokens;
import org.mwolff.fbcrm.auth.infrastructure.LoginAttemptLimiter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Die Einrichtung der frischen Instanz per Einmal-Schluessel (K3, E5).
 *
 * <p>Eine frische Instanz hat kein Konto — und ohne Konto keinen Weg hinein. Der Einmal-Schluessel
 * aus {@code FBCRM_BOOTSTRAP_ADMIN_TOKEN} oeffnet diesen einen Weg, und zwar genau einmal: Konto
 * und Sitzung entstehen in einer Transaktion, der Betreiber ist danach unmittelbar angemeldet.
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
 * <p><b>Das Wettrennen.</b> Zwei gleichzeitige Aufrufe mit <b>verschiedenen</b> Adressen kaemen
 * beide durch {@link AccountRepository#existsAnyAdmin()} und beide durch den eindeutigen Index auf
 * {@code lower(email)} — der sieht zwei verschiedene Schluessel. Was sie trennt, ist der partielle
 * eindeutige Index {@code account_single_admin} aus {@code V1__baseline.sql}: Er steht auf dem
 * konstanten Ausdruck {@code (true)} und traegt damit fuer jede ADMIN-Zeile denselben Schluessel.
 * Seine Verletzung wird hier in dieselbe Abweisung uebersetzt wie „Konto existiert bereits".
 *
 * <p><b>Der Code der Einrichtung</b> (E8, E1). Mit richtigem Schluessel auf freier Instanz geht
 * sofort ein Code an die eingegebene Adresse; das Konto entsteht erst mit dem richtigen Code. Auch
 * hier wird <b>zuerst gezaehlt</b> und erst danach der Schluessel geprueft: Ein vor dem Zaehlen
 * gepruefter Schluessel liesse die Einrichtungsseite ungebremst (Kriterium 6, letzter Satz zu
 * Kriterium 5).
 */
@Service
public class SetupAccountUseCase {

  private final AccountRepository accounts;
  private final SecretHasher hasher;
  private final SessionTokens tokens;
  private final LoginAttemptLimiter limiter;
  private final RequestLoginCodeUseCase ausstellung;
  private final AuthProperties authProperties;
  private final SetupProperties setupProperties;
  private final Clock clock;

  public SetupAccountUseCase(
      final AccountRepository accounts,
      final SecretHasher hasher,
      final SessionTokens tokens,
      final LoginAttemptLimiter limiter,
      final RequestLoginCodeUseCase ausstellung,
      final AuthProperties authProperties,
      final SetupProperties setupProperties,
      final Clock clock) {
    this.accounts = accounts;
    this.hasher = hasher;
    this.tokens = tokens;
    this.limiter = limiter;
    this.ausstellung = ausstellung;
    this.authProperties = authProperties;
    this.setupProperties = setupProperties;
    this.clock = clock;
  }

  /** Ob die Instanz bereits eingerichtet ist (E11). */
  @Transactional(readOnly = true)
  public boolean isInitialized() {
    return accounts.existsAnyAdmin();
  }

  /**
   * Richtet die Instanz ein und meldet den Betreiber an.
   *
   * @param email die Anmeldeadresse des ersten Kontos
   * @param displayName der Anzeigename
   * @param rawPassword das Passwort im Klartext; gespeichert wird nur sein Hash
   * @param presentedKey der vorgelegte Einmal-Schluessel
   * @throws SetupRefused wenn der Schluessel nicht stimmt oder die Instanz schon eingerichtet ist
   */
  @Transactional
  public LoginResult initialize(
      final String email,
      final String displayName,
      final String rawPassword,
      final String presentedKey) {
    if (!schluesselStimmt(presentedKey) || accounts.existsAnyAdmin()) {
      throw new SetupRefused();
    }

    final Instant jetzt = clock.instant();
    final Account neu =
        new Account(
            null, email, displayName, hasher.hash(rawPassword), Role.ADMIN, 0, jetzt, jetzt);

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
    if (!schluesselStimmt(presentedKey) || accounts.existsAnyAdmin()) {
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
