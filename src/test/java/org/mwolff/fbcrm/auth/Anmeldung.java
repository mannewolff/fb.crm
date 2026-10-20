package org.mwolff.fbcrm.auth;

import java.util.List;
import java.util.Map;
import org.mwolff.fbcrm.auth.domain.SecretHasher;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Meldet ein Konto in einem {@code *IT} per Code an — fuer die Tests, deren Gegenstand nicht die
 * Anmeldung ist.
 *
 * <p>Zweck: eine Stelle statt zwanzig. Jeder fachliche {@code *IT} braucht eine Sitzung, und bis
 * zur Umstellung auf den Code hatte jeder seinen eigenen Weg ueber das Passwort. Der Code wird hier
 * nicht angefordert, sondern als Zeile hinterlegt: Die Anforderung zaehlte in der Bremse je
 * Adresse, und ihren Weg ueber Mail und Protokoll weisen {@code AnmeldungPerCodeIT} und {@code
 * NoTokenInLogTest} nach. Angemeldet wird dagegen ueber den echten Weg {@code POST /api/auth/login}
 * — so traegt die Sitzung dasselbe Cookie wie im Betrieb.
 */
public final class Anmeldung {

  /** Der hinterlegte Code; er gilt nur in der Testdatenbank und nur einmal. */
  private static final String CODE = "424242";

  private Anmeldung() {}

  /**
   * Hinterlegt einen frischen Code zu der Adresse, meldet sich damit an und liefert den Kopf mit
   * dem Session-Cookie.
   *
   * @param rest der Client der laufenden Anwendung
   * @param jdbc der Zugang zur Testdatenbank
   * @param hasher der Hasher der Anwendung — der Code liegt wie im Betrieb nur als Hash vor
   * @param email die Adresse eines vorhandenen Kontos
   * @throws IllegalStateException wenn die Anmeldung nicht gelingt
   */
  public static HttpHeaders perCode(
      final TestRestTemplate rest,
      final JdbcTemplate jdbc,
      final SecretHasher hasher,
      final String email) {
    final ResponseEntity<String> antwort =
        rest.postForEntity(
            "/api/auth/login",
            Map.of("email", email, "code", hinterlegeCode(jdbc, hasher, email)),
            String.class);
    if (antwort.getStatusCode() != HttpStatus.OK) {
      throw new IllegalStateException("Anmeldung im Test gescheitert: " + antwort);
    }
    return mitCookie(antwort);
  }

  /**
   * Hinterlegt einen frischen Code zu der Adresse, wie ihn die Ausstellung anlegte, und liefert ihn
   * im Klartext. Ein bisheriger Code zu der Adresse faellt dabei weg.
   *
   * @param jdbc der Zugang zur Testdatenbank
   * @param hasher der Hasher der Anwendung
   * @param email die Adresse, zu der der Code gilt
   * @return der Code, wie ihn der Mensch eintippte
   */
  public static String hinterlegeCode(
      final JdbcTemplate jdbc, final SecretHasher hasher, final String email) {
    jdbc.update("DELETE FROM login_code WHERE lower(email) = lower(?)", email);
    jdbc.update(
        "INSERT INTO login_code (email, code_hash, expires_at)"
            + " VALUES (?, ?, now() + interval '10 minutes')",
        email,
        hasher.hash(CODE));
    return CODE;
  }

  /**
   * Der Code aus dem juengsten Zustellauftrag an die Adresse — so, wie ihn der Mensch aus der Mail
   * abliest. Gilt nur bei eingeschaltetem Mailversand; sonst gibt es keinen Auftrag.
   *
   * @param jdbc der Zugang zur Testdatenbank
   * @param email die Adresse, an die der Auftrag ging
   * @throws IllegalStateException wenn es keinen Auftrag mit einem Code gibt
   */
  public static String codeAusDemPostausgang(final JdbcTemplate jdbc, final String email) {
    final List<String> rumpf =
        jdbc.queryForList(
            "SELECT body FROM outbox_message WHERE recipient = ? ORDER BY id DESC LIMIT 1",
            String.class,
            email);
    return rumpf.stream()
        .flatMap(String::lines)
        .map(String::strip)
        .filter(zeile -> zeile.matches("\\d{6}"))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Kein Code im Postausgang an " + email));
  }

  /**
   * Der Kopf einer Folgeanfrage mit dem Session-Cookie, das eine Antwort gesetzt hat.
   *
   * @param antwort die Antwort einer gelungenen Anmeldung oder Einrichtung
   */
  public static HttpHeaders mitCookie(final ResponseEntity<?> antwort) {
    final String gesetzt = String.valueOf(antwort.getHeaders().getFirst(HttpHeaders.SET_COOKIE));
    final HttpHeaders kopf = new HttpHeaders();
    kopf.add(HttpHeaders.COOKIE, gesetzt.substring(0, gesetzt.indexOf(';')));
    return kopf;
  }
}
