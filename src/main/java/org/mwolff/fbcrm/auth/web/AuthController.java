package org.mwolff.fbcrm.auth.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.mwolff.fbcrm.auth.application.ConfirmPasswordResetUseCase;
import org.mwolff.fbcrm.auth.application.GetCurrentAccountUseCase;
import org.mwolff.fbcrm.auth.application.LoginResult;
import org.mwolff.fbcrm.auth.application.LoginUseCase;
import org.mwolff.fbcrm.auth.application.LogoutUseCase;
import org.mwolff.fbcrm.auth.application.RequestPasswordResetUseCase;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Anmelden, abmelden, das eigene Konto lesen und den Weg zurueck ins Konto (K7).
 *
 * <p>Der Controller entscheidet nichts: Er nimmt die Eingaben, holt die Absender-Adresse fuer die
 * Zaehlbremse, delegiert und uebersetzt das Ergebnis in Statuscode und Cookie (CLAUDE-java.md
 * §6.3).
 *
 * <p>Die Eigenschaften des Cookies stehen an genau einer Stelle, und die ist seit dem Paket
 * „Einrichtung" {@link SessionCookieFactory}: Auch die Einrichtung eroeffnet eine Sitzung (K3), und
 * zwei Bauplaene fuer dasselbe Cookie liefen frueher oder spaeter auseinander.
 *
 * <p><b>Die drei Wege des Passwort-Resets</b> sind ohne Sitzung erreichbar ({@link
 * SecurityConfig}); wer sein Passwort vergessen hat, kann keine vorweisen. Was sie schuetzt, ist
 * der Einmal-Token, den ausschliesslich der Besitzer des Postfachs bekommt.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final LoginUseCase loginUseCase;
  private final LogoutUseCase logoutUseCase;
  private final GetCurrentAccountUseCase currentAccount;
  private final RequestPasswordResetUseCase requestReset;
  private final ConfirmPasswordResetUseCase confirmReset;
  private final ClientIpResolver clientIp;
  private final SessionCookieFactory cookies;

  public AuthController(
      final LoginUseCase loginUseCase,
      final LogoutUseCase logoutUseCase,
      final GetCurrentAccountUseCase currentAccount,
      final RequestPasswordResetUseCase requestReset,
      final ConfirmPasswordResetUseCase confirmReset,
      final ClientIpResolver clientIp,
      final SessionCookieFactory cookies) {
    this.loginUseCase = loginUseCase;
    this.logoutUseCase = logoutUseCase;
    this.currentAccount = currentAccount;
    this.requestReset = requestReset;
    this.confirmReset = confirmReset;
    this.clientIp = clientIp;
    this.cookies = cookies;
  }

  @PostMapping("/login")
  public ResponseEntity<AccountResponse> login(
      @Valid @RequestBody final LoginRequest anfrage, final HttpServletRequest request) {
    final LoginResult ergebnis =
        loginUseCase.login(anfrage.email(), anfrage.password(), clientIp.resolve(request));
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, cookies.header(ergebnis.cookie()))
        .body(AccountResponse.of(ergebnis.account()));
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout() {
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, cookies.header(logoutUseCase.logout()))
        .build();
  }

  @GetMapping("/me")
  public AccountResponse me(@AuthenticationPrincipal final Long accountId) {
    return AccountResponse.of(currentAccount.byId(accountId));
  }

  /**
   * Fordert einen Reset-Link an — mit derselben Antwort fuer jede formal gueltige Adresse (K7).
   *
   * <p>202 statt 200: Angenommen ist der Auftrag, zugestellt noch nicht. Ob ueberhaupt etwas
   * hinausgeht, sagt die Antwort nicht; genau das ist die Zusage.
   */
  @PostMapping("/password-reset")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void requestPasswordReset(@Valid @RequestBody final PasswordResetRequest anfrage) {
    requestReset.request(anfrage.email());
  }

  /**
   * Sagt der Seite, ob sie ein Formular zeigen darf (K7, E24).
   *
   * <p>Ohne Nebenwirkung: Der Token bleibt danach einloesbar. Ein gueltiger Link ergibt 204, ein
   * verbrauchter, abgelaufener oder erfundener 410 — und die Seite zeigt eine Meldung.
   */
  @GetMapping("/password-reset/{token}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void checkPasswordResetToken(@PathVariable final String token) {
    confirmReset.ensureRedeemable(token);
  }

  /** Loest den Link ein und setzt das neue Passwort (K6, K7). */
  @PostMapping("/password-reset/confirm")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void confirmPasswordReset(@Valid @RequestBody final PasswordResetConfirmRequest anfrage) {
    confirmReset.confirm(anfrage.token(), anfrage.password());
  }
}
