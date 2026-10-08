package org.mwolff.fbcrm.auth.web;

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
 * <p>Beide Pfade sind ohne Sitzung erreichbar ({@link SecurityConfig}) — eine frische Instanz hat
 * kein Konto, also kann niemand eine vorweisen. Was sie schuetzt, ist der Einmal-Schluessel und die
 * Tatsache, dass es danach ein Konto gibt.
 */
@RestController
@RequestMapping("/api/setup")
public class SetupController {

  private final SetupAccountUseCase einrichtung;
  private final SessionCookieFactory cookies;

  public SetupController(
      final SetupAccountUseCase einrichtung, final SessionCookieFactory cookies) {
    this.einrichtung = einrichtung;
    this.cookies = cookies;
  }

  /** Richtet ein und meldet den Betreiber im selben Zug an (K3). */
  @PostMapping
  public ResponseEntity<AccountResponse> initialize(
      @Valid @RequestBody final SetupRequest anfrage) {
    final LoginResult ergebnis =
        einrichtung.initialize(
            anfrage.email(), anfrage.displayName(), anfrage.password(), anfrage.bootstrapToken());
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, cookies.header(ergebnis.cookie()))
        .body(AccountResponse.of(ergebnis.account()));
  }

  /** Sagt der noch unangemeldeten Oberflaeche, ob die Instanz schon eingerichtet ist (E11). */
  @GetMapping("/status")
  public SetupStatusResponse status() {
    return new SetupStatusResponse(einrichtung.isInitialized());
  }
}
