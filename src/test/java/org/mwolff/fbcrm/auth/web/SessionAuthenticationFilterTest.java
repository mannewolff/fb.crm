package org.mwolff.fbcrm.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.auth.AuthProperties;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.Role;
import org.mwolff.fbcrm.auth.domain.SessionTokens;
import org.mwolff.fbcrm.auth.infrastructure.SessionTokenCodec;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Die Sitzungspruefung bei jedem Aufruf (E3, K8).
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
  private static final Duration LAUFZEIT = Duration.ofDays(1);
  private static final long KONTO_ID = 42L;
  private static final int GENERATION = 3;

  @Mock private AccountRepository accounts;

  private final MockFilterChain kette = new MockFilterChain();

  private static AuthProperties schalter(final String geheimnis) {
    return new AuthProperties(
        geheimnis, LAUFZEIT, COOKIE_NAME, true, 10, Duration.ofMinutes(15), List.of());
  }

  private static SessionTokens codec(final String geheimnis, final Instant jetzt) {
    return new SessionTokenCodec(schalter(geheimnis), Clock.fixed(jetzt, ZoneOffset.UTC));
  }

  private static Account konto(final int sessionGeneration) {
    return new Account(
        KONTO_ID,
        "manne@example.org",
        "Manne",
        "hash",
        Role.ADMIN,
        sessionGeneration,
        AUSGESTELLT,
        AUSGESTELLT);
  }

  private static String token(final String geheimnis, final long generation) {
    return codec(geheimnis, AUSGESTELLT).encode(KONTO_ID, generation, AUSGESTELLT, LAUFZEIT);
  }

  private SessionAuthenticationFilter filter(final Instant jetzt) {
    return new SessionAuthenticationFilter(schalter(GEHEIMNIS), codec(GEHEIMNIS, jetzt), accounts);
  }

  private static MockHttpServletRequest mitCookie(final String name, final String wert) {
    final MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(new Cookie(name, wert));
    return request;
  }

  private void laufe(final SessionAuthenticationFilter filter, final MockHttpServletRequest request)
      throws ServletException, IOException {
    filter.doFilter(request, new MockHttpServletResponse(), kette);
  }

  private static Optional<Authentication> angemeldet() {
    return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication());
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
}
