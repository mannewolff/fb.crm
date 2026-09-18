package org.mwolff.fbcrm.auth.web;

import org.mwolff.fbcrm.auth.application.SessionCookie;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Die eine Stelle, an der das Session-Cookie seine Eigenschaften bekommt.
 *
 * <p>{@code HttpOnly} haelt es vor JavaScript verborgen, {@code SameSite=Strict} macht es CSRF-fest
 * (siehe {@link SecurityConfig}), {@code Path=/} laesst es fuer die ganze Anwendung gelten und
 * {@code Max-Age} ueberlebt das Schliessen des Browsers (K8, E4). Beim Abmelden ist {@code Max-Age}
 * null — das entwertet es sofort (K9).
 *
 * <p>Sie ist eine eigene Bean und keine Methode im Controller, weil <b>zwei</b> Wege eine Sitzung
 * eroeffnen: das Anmelden ({@link AuthController}) und die Einrichtung ({@link SetupController}).
 * Zwei Bauplaene wuerden frueher oder spaeter auseinanderlaufen, und die Abweichung faende niemand
 * — beide waeren fuer sich gruen.
 */
@Component
public class SessionCookieFactory {

  private static final String SAME_SITE = "Strict";
  private static final String PATH = "/";

  /** Der Wert der Kopfzeile {@code Set-Cookie} zu einer Cookie-Beschreibung. */
  public String header(final SessionCookie beschreibung) {
    return ResponseCookie.from(beschreibung.name(), beschreibung.value())
        .httpOnly(true)
        .secure(beschreibung.secure())
        .sameSite(SAME_SITE)
        .path(PATH)
        .maxAge(beschreibung.maxAge())
        .build()
        .toString();
  }
}
