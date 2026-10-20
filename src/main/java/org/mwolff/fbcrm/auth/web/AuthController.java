package org.mwolff.fbcrm.auth.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.mwolff.fbcrm.auth.application.GetCurrentAccountUseCase;
import org.mwolff.fbcrm.auth.application.LoginResult;
import org.mwolff.fbcrm.auth.application.LoginUseCase;
import org.mwolff.fbcrm.auth.application.LogoutUseCase;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Anmelden, abmelden, einen Code anfordern und das eigene Konto lesen.
 *
 * <p>Der Controller entscheidet nichts: Er nimmt die Eingaben, holt die Absender-Adresse fuer die
 * Zaehlbremse, delegiert und uebersetzt das Ergebnis in Statuscode und Cookie (CLAUDE-java.md
 * §6.3).
 *
 * <p>Die Eigenschaften des Cookies stehen an genau einer Stelle, und die ist seit dem Paket
 * „Einrichtung" {@link SessionCookieFactory}: Auch die Einrichtung eroeffnet eine Sitzung (K3), und
 * zwei Bauplaene fuer dasselbe Cookie liefen frueher oder spaeter auseinander.
 *
 * <p><b>Die Anforderung des Codes</b> ist ohne Sitzung erreichbar: Der Code ist der Weg in die
 * Sitzung. Sie antwortet fuer jede Adresse gleich (E13) und sagt nicht, ob der Mailversand laeuft
 * (E17).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final LoginUseCase loginUseCase;
  private final LogoutUseCase logoutUseCase;
  private final GetCurrentAccountUseCase currentAccount;
  private final ClientIpResolver clientIp;
  private final SessionCookieFactory cookies;

  public AuthController(
      final LoginUseCase loginUseCase,
      final LogoutUseCase logoutUseCase,
      final GetCurrentAccountUseCase currentAccount,
      final ClientIpResolver clientIp,
      final SessionCookieFactory cookies) {
    this.loginUseCase = loginUseCase;
    this.logoutUseCase = logoutUseCase;
    this.currentAccount = currentAccount;
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

  /**
   * Fordert einen Code fuer die Anmeldung an — mit derselben Antwort fuer jede Adresse (E13, E17).
   *
   * <p>202 ohne Rumpf: Angenommen ist der Auftrag, zugestellt noch nicht. Ob es zu der Adresse ein
   * Konto gibt und ob ueberhaupt eine Mail hinausgeht, sagt die Antwort nicht.
   */
  @PostMapping("/code")
  public ResponseEntity<Void> requestCode(
      @Valid @RequestBody final LoginCodeRequest anfrage, final HttpServletRequest request) {
    loginUseCase.codeAnfordern(anfrage.email(), clientIp.resolve(request));
    return ResponseEntity.accepted().build();
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(@AuthenticationPrincipal final Long accountId) {
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, cookies.header(logoutUseCase.logout(accountId)))
        .build();
  }

  @GetMapping("/me")
  public AccountResponse me(@AuthenticationPrincipal final Long accountId) {
    return AccountResponse.of(currentAccount.byId(accountId));
  }
}
