package org.mwolff.fbcrm.auth.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.auth.AuthProperties;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.SessionTokenDecoding;
import org.mwolff.fbcrm.auth.domain.SessionTokens;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Prueft bei jedem Aufruf das Session-Cookie (E3).
 *
 * <p>Vier Dinge muessen stimmen, damit die Anfrage als angemeldet gilt: Das Cookie ist da, seine
 * Signatur passt, seine Laufzeit ist nicht abgelaufen — und die Sitzungs-Generation im Token ist
 * die, die das Konto <b>jetzt</b> traegt. Deshalb wird das Konto je Anfrage ueber den
 * Primaerschluessel geladen und nicht zwischengespeichert (E3): Ein Cache verzoegerte genau die
 * Wirkung, um derentwillen es die Generation gibt — ein neu gesetztes Passwort beendet jede
 * bestehende Sitzung sofort (K8, CLAUDE-security.md).
 *
 * <p>Scheitert einer der vier Punkte, setzt der Filter schlicht keine Authentifizierung und laesst
 * die Kette weiterlaufen. Die Antwort erzeugt dann der {@code AuthenticationEntryPoint} der {@link
 * SecurityConfig}: fuer jeden Fehlschlag derselbe nackte 401, ohne Hinweis darauf, welcher der vier
 * Punkte nicht stimmte.
 *
 * <p>Bewusst <b>keine</b> Bean: Spring Boot registriert jede Filter-Bean zusaetzlich im
 * Servlet-Filterband. Der Filter gehoert in die Sicherheitskette und sonst nirgendwohin — deshalb
 * erzeugt ihn {@link SecurityConfig} selbst.
 */
public class SessionAuthenticationFilter extends OncePerRequestFilter {

  private static final String ROLE_PREFIX = "ROLE_";

  private final AuthProperties properties;
  private final SessionTokens tokens;
  private final AccountRepository accounts;

  public SessionAuthenticationFilter(
      final AuthProperties properties,
      final SessionTokens tokens,
      final AccountRepository accounts) {
    super();
    this.properties = properties;
    this.tokens = tokens;
    this.accounts = accounts;
  }

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final FilterChain filterChain)
      throws ServletException, IOException {
    sitzung(request).ifPresent(SessionAuthenticationFilter::melde);
    filterChain.doFilter(request, response);
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
