package org.mwolff.fbcrm.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.net.HttpCookie;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.auth.AuthProperties;
import org.mwolff.fbcrm.auth.Konten;
import org.mwolff.fbcrm.auth.application.SessionCookie;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.SessionTokenDecoding;
import org.mwolff.fbcrm.auth.domain.SessionTokens;
import org.mwolff.fbcrm.auth.infrastructure.SessionTokenCodec;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Die Sitzungspruefung bei jedem Aufruf (E3, K8) und die Verlaengerung der Sitzung (Kriterium 9,
 * E4, E5, E6).
 *
 * <p>Geprueft wird gegen das echte Token-Format und nicht gegen einen nachgebauten Codec: Ob eine
 * veraenderte Signatur auffaellt, entscheidet {@link SessionTokenCodec}, und genau das soll hier
 * mitgeprueft sein. Was der Filter tut, wenn etwas nicht stimmt, ist immer dasselbe — er meldet
 * niemanden an. Den nackten 401 daraus macht die {@code SecurityConfig}; dass das zusammenspielt,
 * weist {@code AccessRuleIT} nach.
 */
@ExtendWith(MockitoExtension.class)
class SessionAuthenticationFilterTest {

  private static final String GEHEIMNIS = "geheimnis-mit-mindestens-32-zeichen-laenge";
  private static final String FREMDES_GEHEIMNIS = "ein-anderes-geheimnis-mit-genug-zeichen-drin";
  private static final String COOKIE_NAME = "fbcrm_session";
  private static final Instant AUSGESTELLT = Instant.parse("2026-09-18T10:00:00Z");
  private static final Duration LAUFZEIT = Duration.ofDays(30);
  private static final Instant SPAETER = AUSGESTELLT.plus(Duration.ofDays(12));
  private static final long KONTO_ID = 42L;
  private static final int GENERATION = 3;

  @Mock private AccountRepository accounts;

  private final MockFilterChain kette = new MockFilterChain();

  private static AuthProperties schalter(final String geheimnis) {
    return new AuthProperties(
        geheimnis,
        LAUFZEIT,
        Duration.ofHours(1),
        COOKIE_NAME,
        true,
        10,
        Duration.ofMinutes(15),
        5,
        Duration.ofHours(1),
        10,
        Duration.ofHours(24),
        Duration.ofMinutes(10),
        5,
        List.of());
  }

  private static SessionTokens codec(final String geheimnis, final Instant jetzt) {
    return new SessionTokenCodec(schalter(geheimnis), Clock.fixed(jetzt, ZoneOffset.UTC));
  }

  private static Account konto(final int sessionGeneration) {
    return Konten.gespeichert(
        KONTO_ID, "manne@example.org", "hash", sessionGeneration, AUSGESTELLT);
  }

  private static String token(final String geheimnis, final long generation) {
    return codec(geheimnis, AUSGESTELLT).encode(KONTO_ID, generation, AUSGESTELLT, LAUFZEIT);
  }

  private SessionAuthenticationFilter filter(final Instant jetzt) {
    return new SessionAuthenticationFilter(
        schalter(GEHEIMNIS),
        codec(GEHEIMNIS, jetzt),
        accounts,
        new SessionCookieFactory(),
        Clock.fixed(jetzt, ZoneOffset.UTC));
  }

  private static MockHttpServletRequest mitCookie(final String name, final String wert) {
    final MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(new Cookie(name, wert));
    return request;
  }

  private static MockHttpServletRequest anfrage(
      final String methode, final String pfad, final String token) {
    final MockHttpServletRequest request = new MockHttpServletRequest(methode, pfad);
    request.setCookies(new Cookie(COOKIE_NAME, token));
    return request;
  }

  private MockHttpServletResponse laufe(
      final SessionAuthenticationFilter filter, final MockHttpServletRequest request)
      throws ServletException, IOException {
    return laufe(filter, request, kette);
  }

  private static MockHttpServletResponse laufe(
      final SessionAuthenticationFilter filter,
      final MockHttpServletRequest request,
      final FilterChain weiter)
      throws ServletException, IOException {
    final MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, weiter);
    return response;
  }

  private static List<HttpCookie> gesetzteCookies(final MockHttpServletResponse response) {
    return response.getHeaders(HttpHeaders.SET_COOKIE).stream()
        .map(kopf -> HttpCookie.parse(kopf).getFirst())
        .toList();
  }

  private static SessionTokenDecoding.Valid gueltig(final HttpCookie cookie) {
    return (SessionTokenDecoding.Valid) codec(GEHEIMNIS, SPAETER).decode(cookie.getValue());
  }

  private static Optional<Authentication> angemeldet() {
    return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication());
  }

  private void kontoMitAktuellerGeneration() {
    when(accounts.findById(KONTO_ID)).thenReturn(Optional.of(konto(GENERATION)));
  }

  @AfterEach
  void raeumeDenSicherheitskontextAuf() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void doFilter_givenAValidCookie_thenAuthenticatesTheAccount() throws Exception {
    // Given
    when(accounts.findById(KONTO_ID)).thenReturn(Optional.of(konto(GENERATION)));

    // When
    laufe(filter(AUSGESTELLT), mitCookie(COOKIE_NAME, token(GEHEIMNIS, GENERATION)));

    // Then
    assertThat(angemeldet())
        .hasValueSatisfying(a -> assertThat(a.getPrincipal()).isEqualTo(KONTO_ID));
  }

  @Test
  void doFilter_givenAValidCookie_thenCarriesTheRoleOfTheAccount() throws Exception {
    // Given
    when(accounts.findById(KONTO_ID)).thenReturn(Optional.of(konto(GENERATION)));

    // When
    laufe(filter(AUSGESTELLT), mitCookie(COOKIE_NAME, token(GEHEIMNIS, GENERATION)));

    // Then
    assertThat(angemeldet())
        .hasValueSatisfying(
            a ->
                assertThat(a.getAuthorities())
                    .extracting(GrantedAuthority::getAuthority)
                    .containsExactly("ROLE_ADMIN"));
  }

  @Test
  void doFilter_givenAValidCookie_thenLetsTheChainContinue() throws Exception {
    // Given
    when(accounts.findById(KONTO_ID)).thenReturn(Optional.of(konto(GENERATION)));

    // When
    laufe(filter(AUSGESTELLT), mitCookie(COOKIE_NAME, token(GEHEIMNIS, GENERATION)));

    // Then
    assertThat(kette.getRequest()).isNotNull();
  }

  @Test
  void doFilter_givenAnExpiredCookie_thenAuthenticatesNobody() throws Exception {
    // When
    laufe(
        filter(AUSGESTELLT.plus(LAUFZEIT).plusSeconds(1)),
        mitCookie(COOKIE_NAME, token(GEHEIMNIS, GENERATION)));

    // Then
    assertThat(angemeldet()).isEmpty();
  }

  @Test
  void doFilter_givenACookieSignedWithAnotherSecret_thenAuthenticatesNobody() throws Exception {
    // When
    laufe(filter(AUSGESTELLT), mitCookie(COOKIE_NAME, token(FREMDES_GEHEIMNIS, GENERATION)));

    // Then
    assertThat(angemeldet()).isEmpty();
  }

  @Test
  void doFilter_givenACookieWithAnOutdatedGeneration_thenAuthenticatesNobody() throws Exception {
    // Given — K8: nach einem neuen Passwort zaehlt das Konto eine Generation hoeher.
    when(accounts.findById(KONTO_ID)).thenReturn(Optional.of(konto(GENERATION + 1)));

    // When
    laufe(filter(AUSGESTELLT), mitCookie(COOKIE_NAME, token(GEHEIMNIS, GENERATION)));

    // Then
    assertThat(angemeldet()).isEmpty();
  }

  @Test
  void doFilter_givenACookieForAnAccountThatIsGone_thenAuthenticatesNobody() throws Exception {
    // Given
    when(accounts.findById(KONTO_ID)).thenReturn(Optional.empty());

    // When
    laufe(filter(AUSGESTELLT), mitCookie(COOKIE_NAME, token(GEHEIMNIS, GENERATION)));

    // Then
    assertThat(angemeldet()).isEmpty();
  }

  @Test
  void doFilter_givenAMalformedCookie_thenAuthenticatesNobody() throws Exception {
    // When
    laufe(filter(AUSGESTELLT), mitCookie(COOKIE_NAME, "kein-token"));

    // Then
    assertThat(angemeldet()).isEmpty();
  }

  @Test
  void doFilter_givenNoCookieAtAll_thenAuthenticatesNobody() throws Exception {
    // When
    laufe(filter(AUSGESTELLT), new MockHttpServletRequest());

    // Then
    assertThat(angemeldet()).isEmpty();
  }

  @Test
  void doFilter_givenOnlyAnotherCookie_thenAuthenticatesNobody() throws Exception {
    // When
    laufe(filter(AUSGESTELLT), mitCookie("anderes", token(GEHEIMNIS, GENERATION)));

    // Then
    assertThat(angemeldet()).isEmpty();
  }

  @Test
  void doFilter_givenOnlyAnotherCookie_thenNeverEvenLooksUpAnAccount() throws Exception {
    // When — der Filter darf nur sein eigenes Cookie lesen, nicht irgendeines.
    laufe(filter(AUSGESTELLT), mitCookie("anderes", token(GEHEIMNIS, GENERATION)));

    // Then
    verify(accounts, never()).findById(anyLong());
  }

  @Test
  void doFilter_givenAnInvalidCookie_thenStillLetsTheChainContinue() throws Exception {
    // When
    laufe(filter(AUSGESTELLT), mitCookie(COOKIE_NAME, "kein-token"));

    // Then
    assertThat(kette.getRequest()).isNotNull();
  }

  @Test
  void doFilter_givenAValidCookie_thenSetsAFreshCookieWithTheFullLifetime() throws Exception {
    // Given — Kriterium 9, E5: jede Nutzung verlaengert auf die volle Laufzeit ab jetzt.
    kontoMitAktuellerGeneration();

    // When
    final MockHttpServletResponse antwort =
        laufe(filter(SPAETER), anfrage("GET", "/api/firmen", token(GEHEIMNIS, GENERATION)));

    // Then
    assertThat(gesetzteCookies(antwort))
        .singleElement()
        .satisfies(
            cookie -> assertThat(cookie.getName()).isEqualTo(COOKIE_NAME),
            cookie -> assertThat(cookie.getMaxAge()).isEqualTo(LAUFZEIT.toSeconds()),
            cookie -> assertThat(cookie.isHttpOnly()).isTrue(),
            cookie -> assertThat(cookie.getSecure()).isTrue(),
            cookie -> assertThat(cookie.getPath()).isEqualTo("/"));
  }

  @Test
  void doFilter_givenAValidCookie_thenTheFreshTokenIsIssuedNow() throws Exception {
    // Given
    kontoMitAktuellerGeneration();

    // When
    final MockHttpServletResponse antwort =
        laufe(filter(SPAETER), anfrage("GET", "/api/firmen", token(GEHEIMNIS, GENERATION)));

    // Then — die Frist laeuft ab der letzten Nutzung, nicht ab der ersten Anmeldung.
    assertThat(gueltig(gesetzteCookies(antwort).getFirst()))
        .satisfies(
            token -> assertThat(token.issuedAt()).isEqualTo(SPAETER),
            token -> assertThat(token.expiresAt()).isEqualTo(SPAETER.plus(LAUFZEIT)));
  }

  @Test
  void doFilter_givenAValidCookie_thenTheFreshTokenCarriesTheSameSession() throws Exception {
    // Given
    kontoMitAktuellerGeneration();

    // When
    final MockHttpServletResponse antwort =
        laufe(filter(SPAETER), anfrage("GET", "/api/firmen", token(GEHEIMNIS, GENERATION)));

    // Then
    assertThat(gueltig(gesetzteCookies(antwort).getFirst()))
        .satisfies(
            token -> assertThat(token.accountId()).isEqualTo(KONTO_ID),
            token -> assertThat(token.sessionGeneration()).isEqualTo(GENERATION));
  }

  @Test
  void doFilter_givenAValidCookie_thenSetsTheCookieBeforeTheChainRuns() throws Exception {
    // Given — danach koennte die Antwort schon festgeschrieben sein.
    kontoMitAktuellerGeneration();
    final List<String> vorDerKette = new ArrayList<>();
    final FilterChain weiter =
        (request, response) ->
            vorDerKette.addAll(
                ((MockHttpServletResponse) response).getHeaders(HttpHeaders.SET_COOKIE));

    // When
    laufe(filter(SPAETER), anfrage("GET", "/api/firmen", token(GEHEIMNIS, GENERATION)), weiter);

    // Then
    assertThat(vorDerKette).hasSize(1);
  }

  @Test
  void doFilter_givenTheLogout_thenSetsNoCookie() throws Exception {
    // Given — E6: das Abmelden setzt Max-Age=0, ein zweiter Kopf machte das Ergebnis zufaellig.
    kontoMitAktuellerGeneration();

    // When
    final MockHttpServletResponse antwort =
        laufe(filter(SPAETER), anfrage("POST", "/api/auth/logout", token(GEHEIMNIS, GENERATION)));

    // Then
    assertThat(antwort.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
  }

  @Test
  void doFilter_givenTheLogout_thenStillAuthenticatesTheAccount() throws Exception {
    // Given — abmelden darf nur, wer angemeldet ist.
    kontoMitAktuellerGeneration();

    // When
    laufe(filter(SPAETER), anfrage("POST", "/api/auth/logout", token(GEHEIMNIS, GENERATION)));

    // Then
    assertThat(angemeldet())
        .hasValueSatisfying(a -> assertThat(a.getPrincipal()).isEqualTo(KONTO_ID));
  }

  @Test
  void doFilter_givenAnotherMethodOnTheLogoutPath_thenSetsAFreshCookie() throws Exception {
    // Given — ausgenommen ist genau POST /api/auth/logout, kein anderer Aufruf dieses Pfads.
    kontoMitAktuellerGeneration();

    // When
    final MockHttpServletResponse antwort =
        laufe(filter(SPAETER), anfrage("GET", "/api/auth/logout", token(GEHEIMNIS, GENERATION)));

    // Then
    assertThat(gesetzteCookies(antwort)).hasSize(1);
  }

  @Test
  void doFilter_givenTheLoginWithAValidCookie_thenBothCookiesCarryTheSameSession()
      throws Exception {
    // Given — E6: AuthController setzt bei der Anmeldung selbst ein Cookie; traegt die Anfrage
    // schon ein gueltiges, entstehen zwei Koepfe. Die Reihenfolge ist gleichgueltig, weil beide
    // dieselbe Sitzung tragen.
    kontoMitAktuellerGeneration();
    final String vomController =
        new SessionCookieFactory()
            .header(
                new SessionCookie(
                    COOKIE_NAME,
                    codec(GEHEIMNIS, SPAETER).encode(KONTO_ID, GENERATION, SPAETER, LAUFZEIT),
                    LAUFZEIT,
                    true));
    final FilterChain anmelden =
        (request, response) ->
            ((MockHttpServletResponse) response).addHeader(HttpHeaders.SET_COOKIE, vomController);

    // When
    final MockHttpServletResponse antwort =
        laufe(
            filter(SPAETER),
            anfrage("POST", "/api/auth/login", token(GEHEIMNIS, GENERATION)),
            anmelden);

    // Then
    assertThat(gesetzteCookies(antwort))
        .hasSize(2)
        .map(SessionAuthenticationFilterTest::gueltig)
        .allSatisfy(
            token -> {
              assertThat(token.accountId()).isEqualTo(KONTO_ID);
              assertThat(token.sessionGeneration()).isEqualTo(GENERATION);
            });
  }

  @Test
  void doFilter_givenAnInvalidCookie_thenSetsNoCookie() throws Exception {
    // When
    final MockHttpServletResponse antwort =
        laufe(filter(SPAETER), anfrage("GET", "/api/firmen", "kein-token"));

    // Then
    assertThat(antwort.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
  }

  @Test
  void doFilter_givenAnOutdatedGeneration_thenSetsNoCookie() throws Exception {
    // Given — nach dem Abmelden zaehlt das Konto eine Generation hoeher (E7).
    when(accounts.findById(KONTO_ID)).thenReturn(Optional.of(konto(GENERATION + 1)));

    // When
    final MockHttpServletResponse antwort =
        laufe(filter(SPAETER), anfrage("GET", "/api/firmen", token(GEHEIMNIS, GENERATION)));

    // Then
    assertThat(antwort.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
  }
}
