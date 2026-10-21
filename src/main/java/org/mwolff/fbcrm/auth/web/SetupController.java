package org.mwolff.fbcrm.auth.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.mwolff.fbcrm.auth.application.LoginResult;
import org.mwolff.fbcrm.auth.application.SetupAccountUseCase;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Die Einrichtung der frischen Instanz (K3, E11).
 *
 * <p>Der Controller entscheidet nichts: Ob der Einmal-Schluessel stimmt und ob die Instanz
 * ueberhaupt noch frei ist, beantwortet {@code SetupAccountUseCase}; die Form der Eingaben prueft
 * Bean Validation (CLAUDE-java.md §6.3).
 *
 * <p>Alle Pfade sind ohne Sitzung erreichbar ({@link SecurityConfig}) — eine frische Instanz hat
 * kein Konto, also kann niemand eine vorweisen. Was sie schuetzt, ist der Einmal-Schluessel und die
 * Tatsache, dass es danach ein Konto gibt.
 */
@RestController
@RequestMapping("/api/setup")
public class SetupController {

  private final SetupAccountUseCase einrichtung;
  private final ClientIpResolver clientIp;
  private final SessionCookieFactory cookies;

  public SetupController(
      final SetupAccountUseCase einrichtung,
      final ClientIpResolver clientIp,
      final SessionCookieFactory cookies) {
    this.einrichtung = einrichtung;
    this.clientIp = clientIp;
    this.cookies = cookies;
  }

  /**
   * Bestaetigt die Einrichtung mit dem Code, legt das Konto an und meldet den Betreiber im selben
   * Zug an (K3, E9).
   */
  @PostMapping
  public ResponseEntity<AccountResponse> initialize(
      @Valid @RequestBody final SetupRequest anfrage, final HttpServletRequest request) {
    final LoginResult ergebnis =
        einrichtung.initialize(
            anfrage.email(),
            anfrage.displayName(),
            anfrage.code(),
            anfrage.bootstrapToken(),
            clientIp.resolve(request));
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, cookies.header(ergebnis.cookie()))
        .body(AccountResponse.of(ergebnis.account()));
  }

  /**
   * Fordert den Code der Einrichtung an (E8) — 202 ohne Rumpf, fuer jede Adresse gleich (E17).
   *
   * <p>Ein falscher Schluessel oder eine schon eingerichtete Instanz enden in derselben Abweisung
   * wie bisher; die Zaehlbremse greift vor beidem.
   */
  @PostMapping("/code")
  public ResponseEntity<Void> requestCode(
      @Valid @RequestBody final SetupCodeRequest anfrage, final HttpServletRequest request) {
    einrichtung.requestCode(
        anfrage.email(),
        anfrage.displayName(),
        anfrage.bootstrapToken(),
        clientIp.resolve(request));
    return ResponseEntity.accepted().build();
  }

  /** Sagt der noch unangemeldeten Oberflaeche, ob die Instanz schon eingerichtet ist (E11). */
  @GetMapping("/status")
  public SetupStatusResponse status() {
    return new SetupStatusResponse(einrichtung.isInitialized());
  }
}
