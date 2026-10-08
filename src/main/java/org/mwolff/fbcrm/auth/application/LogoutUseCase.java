package org.mwolff.fbcrm.auth.application;

import java.time.Duration;
import org.mwolff.fbcrm.auth.AuthProperties;
import org.springframework.stereotype.Service;

/**
 * Abmelden (K9).
 *
 * <p>Die Sitzung ist zustandslos: Es gibt serverseitig nichts zu loeschen, das Token traegt seine
 * Gueltigkeit selbst (E2). Abmelden heisst deshalb, das Cookie im Browser zu entwerten — {@code
 * Max-Age=0} und leerer Wert. Der Zurueck-Knopf bringt danach niemanden in die Anwendung zurueck,
 * weil jede Antwort {@code Cache-Control: no-store} traegt und der naechste API-Aufruf ohne Cookie
 * 401 bekommt.
 *
 * <p>Die Sitzungs-Generation wird <b>nicht</b> hochgezaehlt: Das beendete die Sitzungen auf allen
 * Geraeten. K9 verlangt nur, dass dieses Geraet abgemeldet ist; alle Sitzungen beendet allein das
 * Neusetzen des Passworts (CLAUDE-security.md).
 */
@Service
public class LogoutUseCase {

  private final AuthProperties properties;

  public LogoutUseCase(final AuthProperties properties) {
    this.properties = properties;
  }

  /** Das Cookie, das die Sitzung im Browser entwertet. */
  public SessionCookie logout() {
    return new SessionCookie(properties.cookieName(), "", Duration.ZERO, properties.cookieSecure());
  }
}
