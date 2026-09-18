package org.mwolff.fbcrm.auth.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.mwolff.fbcrm.auth.application.GetCurrentAccountUseCase;
import org.mwolff.fbcrm.auth.application.LoginResult;
import org.mwolff.fbcrm.auth.application.LoginUseCase;
import org.mwolff.fbcrm.auth.application.LogoutUseCase;
import org.mwolff.fbcrm.auth.application.SessionCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Anmelden, abmelden und das eigene Konto lesen.
 *
 * <p>Der Controller entscheidet nichts: Er nimmt die Eingaben, holt die Absender-Adresse fuer die
 * Zaehlbremse, delegiert und uebersetzt das Ergebnis in Statuscode und Cookie (CLAUDE-java.md
 * §6.3).
 *
 * <p>Die Eigenschaften des Cookies stehen an genau dieser einen Stelle: {@code HttpOnly} haelt es
 * vor JavaScript verborgen, {@code SameSite=Strict} macht es CSRF-fest (siehe {@link
 * SecurityConfig}), {@code Path=/} laesst es fuer die ganze Anwendung gelten und {@code Max-Age}
 * ueberlebt das Schliessen des Browsers (K8, E4). Beim Abmelden ist {@code Max-Age} null — das
 * entwertet es sofort (K9).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private static final String SAME_SITE = "Strict";
  private static final String PATH = "/";

  private final LoginUseCase loginUseCase;
  private final LogoutUseCase logoutUseCase;
  private final GetCurrentAccountUseCase currentAccount;
  private final ClientIpResolver clientIp;

  public AuthController(
      final LoginUseCase loginUseCase,
      final LogoutUseCase logoutUseCase,
      final GetCurrentAccountUseCase currentAccount,
      final ClientIpResolver clientIp) {
    this.loginUseCase = loginUseCase;
    this.logoutUseCase = logoutUseCase;
    this.currentAccount = currentAccount;
    this.clientIp = clientIp;
  }

  @PostMapping("/login")
  public ResponseEntity<AccountResponse> login(
      @Valid @RequestBody final LoginRequest anfrage, final HttpServletRequest request) {
    final LoginResult ergebnis =
        loginUseCase.login(anfrage.email(), anfrage.password(), clientIp.resolve(request));
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, cookie(ergebnis.cookie()))
        .body(AccountResponse.of(ergebnis.account()));
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout() {
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, cookie(logoutUseCase.logout()))
        .build();
  }

  @GetMapping("/me")
  public AccountResponse me(@AuthenticationPrincipal final Long accountId) {
    return AccountResponse.of(currentAccount.byId(accountId));
  }

  private static String cookie(final SessionCookie beschreibung) {
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
