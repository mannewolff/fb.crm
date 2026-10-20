package org.mwolff.fbcrm.auth.application;

import static org.mwolff.fbcrm.auth.infrastructure.LoginAttemptLimiter.Zaehlart.HERKUNFT;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.mwolff.fbcrm.auth.AuthProperties;
import org.mwolff.fbcrm.auth.domain.LoginCode;
import org.mwolff.fbcrm.auth.domain.LoginCodeRepository;
import org.mwolff.fbcrm.auth.domain.SecretHasher;
import org.mwolff.fbcrm.auth.infrastructure.LoginAttemptLimiter;
import org.mwolff.fbcrm.common.SecureTokens;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Loest einen Einmalcode ein — fuer die Anmeldung wie fuer die Einrichtung (E13, E27).
 *
 * <p>Beide Wege pruefen den Code nach derselben Regel, und die steht darum an genau einer Stelle:
 * Eine zweite Fassung liefe frueher oder spaeter auseinander, und gerade hier waere jeder
 * Unterschied eine Luecke — etwa eine Einrichtung, deren Code nach der fuenften Fehleingabe nicht
 * verfaellt.
 *
 * <p><b>Derselbe Rechenaufwand auf jedem Weg</b> (E13). Gibt es zu der Adresse keinen Code, wird
 * gegen einen Wegwerf-Hash gerechnet; ohne diesen Argon2-Durchlauf antwortete die Instanz auf
 * unbekannte Adressen messbar frueher und verriete, welche Adressen sie kennt. Auch ein
 * abgelaufener Code wird erst verglichen und dann verworfen.
 *
 * <p><b>Was mit der Zeile geschieht</b> (Kriterium 4): Ein richtiger Code ist danach verbraucht,
 * ein abgelaufener ebenso — beide Zeilen fallen weg. Ein falscher Code zaehlt eine Fehleingabe;
 * erreicht sie die Grenze, faellt die Zeile, und eine Protokollzeile auf Stufe WARN nennt Zeitpunkt
 * und Herkunft (E27). Weder der Code noch die Adresse stehen darin: Der Betreiber soll den
 * Rateangriff sehen, nicht, gegen wen er lief.
 */
@Component
class CodeEinloesung {

  private static final Logger LOG = LoggerFactory.getLogger(CodeEinloesung.class);

  private final LoginCodeRepository loginCodes;
  private final SecretHasher hasher;
  private final LoginAttemptLimiter limiter;
  private final AuthProperties properties;
  private final Clock clock;

  /**
   * Gegen diesen Hash wird geprueft, wenn es zu der Adresse keinen Code gibt.
   *
   * <p>Er entsteht einmal beim Hochfahren aus einem frischen Zufallswert. Was dahintersteckt, ist
   * bedeutungslos und nirgends gueltig — gebraucht wird allein die Rechenzeit des Verfahrens. Darum
   * steht hier auch kein fester Wert: Der waere ein Code im Quellcode, und dieselbe Rechenzeit
   * kostet ein zufaelliger genauso.
   */
  private final String dummyHash;

  CodeEinloesung(
      final LoginCodeRepository loginCodes,
      final SecretHasher hasher,
      final LoginAttemptLimiter limiter,
      final AuthProperties properties,
      final Clock clock) {
    this.loginCodes = loginCodes;
    this.hasher = hasher;
    this.limiter = limiter;
    this.properties = properties;
    this.clock = clock;
    this.dummyHash = hasher.hash(new SecureTokens().newToken());
  }

  /**
   * Prueft und zaehlt eine Eingabe in der Zaehlart {@link LoginAttemptLimiter.Zaehlart#HERKUNFT}.
   *
   * <p>Gezaehlt wird jede Eingabe, nicht nur die gescheiterte: Anforderungen und Eingaben derselben
   * Herkunft werden zusammen gebremst (Kriterium 5). Eine gelungene Einloesung loescht den Zaehler
   * danach wieder. Ist das Fenster zwischen der Frage und der Restwartezeit abgelaufen, ist die
   * Restwartezeit null — und die Eingabe darf durch, weil die Sperre dann tatsaechlich vorbei ist.
   *
   * @param clientIp die Absender-Adresse der Eingabe
   * @throws TooManyLoginAttempts wenn die Herkunft gesperrt ist, mit der Restwartezeit
   */
  void bremse(final String clientIp) {
    final Duration warten =
        limiter.isAllowed(HERKUNFT, clientIp)
            ? Duration.ZERO
            : limiter.restwartezeit(HERKUNFT, clientIp);
    if (!warten.isZero()) {
      throw new TooManyLoginAttempts(warten);
    }
    limiter.record(HERKUNFT, clientIp);
  }

  /**
   * Loest den Code zu einer Adresse ein.
   *
   * @param email die eingegebene Adresse
   * @param code der eingegebene Code
   * @param clientIp die Absender-Adresse — fuer die Protokollzeile beim Verfall und den Zaehler
   * @return ob der Code stimmte und noch galt; danach ist er in jedem Fall nicht mehr einloesbar
   */
  boolean loese(final String email, final String code, final String clientIp) {
    final Optional<LoginCode> zeile = loginCodes.findByEmail(email);
    final boolean passt = hasher.matches(code, zeile.map(LoginCode::codeHash).orElse(dummyHash));
    if (zeile.isEmpty()) {
      return false;
    }
    final Instant jetzt = clock.instant();
    if (!zeile.get().istEinloesbar(jetzt)) {
      loginCodes.deleteByEmail(email);
      return false;
    }
    if (!passt) {
      zaehleFehleingabe(zeile.get(), jetzt, clientIp);
      return false;
    }
    loginCodes.deleteByEmail(email);
    limiter.recordSuccess(clientIp);
    return true;
  }

  private void zaehleFehleingabe(
      final LoginCode bisher, final Instant jetzt, final String clientIp) {
    final LoginCode nachher = bisher.mitFehlversuch();
    if (nachher.istVerfallen(properties.loginCodeMaxAttempts())) {
      loginCodes.deleteByEmail(bisher.email());
      LOG.warn(
          "Einmalcode nach {} Fehleingaben verfallen — {}, Herkunft {}.",
          nachher.failedAttempts(),
          jetzt,
          clientIp);
    } else {
      loginCodes.save(nachher);
    }
  }
}
