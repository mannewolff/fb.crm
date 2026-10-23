package org.mwolff.fbcrm.auth.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.auth.AuthProperties;
import org.mwolff.fbcrm.auth.application.SessionCookie;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.SessionTokenDecoding;
import org.mwolff.fbcrm.auth.domain.SessionTokens;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Prueft bei jedem Aufruf das Session-Cookie (E3) und verlaengert die Sitzung (Kriterium 9).
 *
 * <p>Fuenf Dinge muessen stimmen, damit die Anfrage als angemeldet gilt: Das Cookie ist da, seine
 * Signatur passt, seine Laufzeit ist nicht abgelaufen, die Sitzungs-Generation im Token ist die,
 * die das Konto <b>jetzt</b> traegt — und das Konto ist <b>jetzt</b> aktiv. Deshalb wird das Konto
 * je Anfrage ueber den Primaerschluessel geladen und nicht zwischengespeichert (E3): Ein Cache
 * verzoegerte genau die Wirkung, um derentwillen es die Generation gibt — das Abmelden beendet jede
 * bestehende Sitzung sofort (K8, E7, CLAUDE-security.md), ebenso das Update auf die Anmeldung per
 * Code (E18). Der fuenfte Punkt haelt ein stillgelegtes Konto draussen, auch mit einem Token
 * passender Generation (fachliche Quelle #409, Kriterium 7); das Stilllegen zaehlt die Generation
 * zwar ohnehin hoch, die Sitzung haengt aber nicht allein daran. Weil das Konto je Anfrage frisch
 * geladen wird, wirkt auch eine geaenderte Rolle beim naechsten Schritt (Plan #414, E5).
 *
 * <p>Scheitert einer der fuenf Punkte, setzt der Filter schlicht keine Authentifizierung und laesst
 * die Kette weiterlaufen. Die Antwort erzeugt dann der {@code AuthenticationEntryPoint} der {@link
 * SecurityConfig}: fuer jeden Fehlschlag derselbe nackte 401, ohne Hinweis darauf, welcher der
 * fuenf Punkte nicht stimmte.
 *
 * <p><b>Jede Anfrage verlaengert die Sitzung</b> (Kriterium 9, E4, E5): Stimmen alle fuenf Punkte,
 * setzt der Filter ein frisches Cookie mit einem jetzt ausgestellten Token und der vollen Laufzeit.
 * Die Frist laeuft damit ab der letzten Nutzung, nicht ab der Anmeldung. „Jede Anfrage" ist genau
 * „jede Bedienung", weil das Frontend keine Anfrage ohne Anlass stellt — das haelt der Waechter
 * {@code keinHintergrundabruf.test.ts} fest. Gesetzt wird der Kopf <b>vor</b> dem Rest der Kette,
 * solange die Antwort noch nicht festgeschrieben ist.
 *
 * <p>Ausgenommen ist allein {@code POST /api/auth/logout} (E6): Das Abmelden setzt {@code
 * Max-Age=0}, und zwei {@code Set-Cookie}-Koepfe desselben Namens machten das Ergebnis von ihrer
 * Reihenfolge abhaengig. Beim Anmelden mit schon gueltigem Cookie entstehen ebenfalls zwei Koepfe;
 * beide tragen dann dieselbe Sitzung, die Reihenfolge ist dort gleichgueltig.
 *
 * <p>Bewusst <b>keine</b> Bean: Spring Boot registriert jede Filter-Bean zusaetzlich im
 * Servlet-Filterband. Der Filter gehoert in die Sicherheitskette und sonst nirgendwohin — deshalb
 * erzeugt ihn {@link SecurityConfig} selbst.
 */
public class SessionAuthenticationFilter extends OncePerRequestFilter {

  private static final String ROLE_PREFIX = "ROLE_";

  /** Der eine Aufruf, den der Filter nicht verlaengert (E6). */
  private static final RequestMatcher ABMELDEN =
      PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/auth/logout");

  private final AuthProperties properties;
  private final SessionTokens tokens;
  private final AccountRepository accounts;
  private final SessionCookieFactory cookies;
  private final Clock clock;

  public SessionAuthenticationFilter(
      final AuthProperties properties,
      final SessionTokens tokens,
      final AccountRepository accounts,
      final SessionCookieFactory cookies,
      final Clock clock) {
    super();
    this.properties = properties;
    this.tokens = tokens;
    this.accounts = accounts;
    this.cookies = cookies;
    this.clock = clock;
  }

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final FilterChain filterChain)
      throws ServletException, IOException {
    sitzung(request)
        .ifPresent(
            konto -> {
              melde(konto);
              if (!ABMELDEN.matches(request)) {
                verlaengere(konto, response);
              }
            });
    filterChain.doFilter(request, response);
  }

  private void verlaengere(final Account konto, final HttpServletResponse response) {
    final String token =
        tokens.encode(
            konto.requireId(), konto.sessionGeneration(), clock.instant(), properties.sessionTtl());
    response.addHeader(
        HttpHeaders.SET_COOKIE,
        cookies.header(
            new SessionCookie(
                properties.cookieName(),
                token,
                properties.sessionTtl(),
                properties.cookieSecure())));
  }

  private Optional<Account> sitzung(final HttpServletRequest request) {
    return cookie(request).flatMap(this::konto);
  }

  private Optional<String> cookie(final HttpServletRequest request) {
    // Nullbar ist die Referenz auf das Feld, nicht sein Inhalt: Cookie @Nullable [] statt
    // @Nullable Cookie[] — sonst haelt NullAway jeden einzelnen Eintrag fuer nullbar.
    final Cookie @Nullable [] vorhandene = request.getCookies();
    if (vorhandene == null) {
      return Optional.empty();
    }
    return Arrays.stream(vorhandene)
        .filter(cookie -> properties.cookieName().equals(cookie.getName()))
        .map(Cookie::getValue)
        .findFirst();
  }

  private Optional<Account> konto(final String token) {
    if (!(tokens.decode(token) instanceof final SessionTokenDecoding.Valid gueltig)) {
      return Optional.empty();
    }
    return accounts
        .findById(gueltig.accountId())
        .filter(Account::aktiv)
        .filter(konto -> konto.matchesGeneration(gueltig.sessionGeneration()));
  }

  private static void melde(final Account konto) {
    final UsernamePasswordAuthenticationToken authentifizierung =
        UsernamePasswordAuthenticationToken.authenticated(
            konto.requireId(),
            null,
            List.of(new SimpleGrantedAuthority(ROLE_PREFIX + konto.role().name())));
    final SecurityContext kontext = SecurityContextHolder.createEmptyContext();
    kontext.setAuthentication(authentifizierung);
    SecurityContextHolder.setContext(kontext);
  }
}
