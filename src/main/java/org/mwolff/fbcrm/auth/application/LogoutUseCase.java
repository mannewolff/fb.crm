package org.mwolff.fbcrm.auth.application;

import java.time.Clock;
import java.time.Duration;
import org.mwolff.fbcrm.auth.AuthProperties;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Abmelden (K9, Kriterium 9).
 *
 * <p>Die Sitzung ist zustandslos: Das Token traegt seine Gueltigkeit selbst (E2). Abmelden heisst
 * deshalb zweierlei. Das Cookie im Browser wird entwertet — {@code Max-Age=0} und leerer Wert. Der
 * Zurueck-Knopf bringt danach niemanden in die Anwendung zurueck, weil jede Antwort {@code
 * Cache-Control: no-store} traegt und der naechste API-Aufruf ohne Cookie 401 bekommt.
 *
 * <p>Und die Sitzungs-Generation des Kontos zaehlt hoch (E7): Ein Token, das jemand vorher
 * abgegriffen hat, gilt damit sofort nicht mehr, auch wenn seine Laufzeit noch nicht um ist. Bei
 * einem Konto und einer Person ist „alle Sitzungen" dasselbe wie „diese Sitzung" (CLAUDE.md,
 * Betriebsform), und Kriterium 9 verlangt das sofortige Ende.
 *
 * <p>Gibt es das Konto nicht mehr, gibt es nichts hochzuzaehlen; das Cookie wird trotzdem
 * entwertet.
 */
@Service
public class LogoutUseCase {

  private final AuthProperties properties;
  private final AccountRepository accounts;
  private final Clock clock;

  public LogoutUseCase(
      final AuthProperties properties, final AccountRepository accounts, final Clock clock) {
    this.properties = properties;
    this.accounts = accounts;
    this.clock = clock;
  }

  /**
   * Beendet jede Sitzung des Kontos.
   *
   * @param accountId Konto aus dem geprueften Session-Token
   * @return das Cookie, das die Sitzung im Browser entwertet
   */
  @Transactional
  public SessionCookie logout(final long accountId) {
    accounts
        .findById(accountId)
        .ifPresent(konto -> accounts.save(konto.mitNaechsterGeneration(clock.instant())));
    return new SessionCookie(properties.cookieName(), "", Duration.ZERO, properties.cookieSecure());
  }
}
