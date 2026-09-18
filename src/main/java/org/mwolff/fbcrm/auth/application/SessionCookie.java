package org.mwolff.fbcrm.auth.application;

import java.time.Duration;

/**
 * Was nach einem Anwendungsfall im Session-Cookie stehen soll.
 *
 * <p>Der Typ traegt nur die Werte, die sich je Fall unterscheiden. {@code HttpOnly}, {@code
 * SameSite=Strict} und {@code Path=/} setzt die Weboberflaeche fuer jedes Cookie gleich — sie sind
 * keine Entscheidung eines Anwendungsfalls.
 *
 * @param name Name des Cookies aus {@code AuthProperties}
 * @param value Session-Token; beim Abmelden leer
 * @param maxAge Laufzeit; {@link Duration#ZERO} entwertet das Cookie im Browser (K9)
 * @param secure ob das Cookie nur ueber HTTPS gesendet wird
 */
public record SessionCookie(String name, String value, Duration maxAge, boolean secure) {}
