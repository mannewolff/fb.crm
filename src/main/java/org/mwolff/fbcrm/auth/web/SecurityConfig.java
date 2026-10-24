package org.mwolff.fbcrm.auth.web;

import java.time.Clock;
import org.mwolff.fbcrm.auth.AuthProperties;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
import org.mwolff.fbcrm.auth.domain.Role;
import org.mwolff.fbcrm.auth.domain.SessionTokens;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Die Zugangsregel der Anwendung (K1).
 *
 * <p>Offen ohne Sitzung ist eine kurze, abschliessende Liste: die Anmeldung selbst und die
 * Anforderung ihres Codes, die Einrichtung der frischen Instanz samt ihrem Code, {@code
 * /actuator/health} — und alles, was keine Schnittstelle ist, also die statischen Dateien und der
 * SPA-Fallback (E20). Jeder andere Pfad unter {@code /api} oder {@code /actuator} verlangt eine
 * Sitzung.
 *
 * <p><b>Kein CSRF-Synchronizer-Token.</b> Das Session-Cookie traegt {@code SameSite=Strict} und
 * wird deshalb nie bei einem Aufruf gesendet, der von einer fremden Seite ausgeht — damit faellt
 * die Voraussetzung eines CSRF-Angriffs weg (CLAUDE-security.md). Ein zusaetzliches Token braechte
 * keinen weiteren Schutz, aber einen zweiten Weg, auf dem eine Anmeldung scheitern kann. Faellt
 * {@code SameSite=Strict} jemals weg, muss das Token hier wieder hinein.
 *
 * <p><b>Warum {@code /actuator/env} und Konsorten nicht in der Liste stehen:</b> Sie existieren gar
 * nicht — {@code management.endpoints.web.exposure.include} laesst nur {@code health} und {@code
 * info} zu, alles andere antwortet 404 ({@code ActuatorExposureIT}). {@code /actuator/info} traegt
 * die Version der laufenden Instanz und verlangt deshalb ausdruecklich eine Sitzung.
 *
 * <p><b>Die Rollenstufen</b> (Plan #414, A1, A2) stehen zwischen den {@code permitAll}-Eintraegen
 * und der Zeile {@code requestMatchers("/api/**", "/actuator/info").authenticated()}, von
 * spezifisch nach allgemein — es gilt die erste passende Zeile:
 *
 * <ol>
 *   <li>{@code /api/mitarbeitende/**} nur C-Level, mit jeder Methode, auch lesend.
 *   <li>Die Einstellungen — {@code /api/eigene-angaben}, {@code /api/briefpapier/**} und {@code
 *       /api/rechnung/einstellungen}: {@code GET} fuer C-Level und ReadOnly, jede andere Methode
 *       nur fuer C-Level. Employee erreicht sie gar nicht.
 *   <li>{@code POST}, {@code PUT} und {@code DELETE} unter {@code /api/**} nur fuer C-Level und
 *       Employee. Kein solcher Aufruf ausserhalb von {@code /api/auth} und {@code /api/setup} liest
 *       nur; die Methode ist damit ein tragfaehiges Kennzeichen fuer „schreibt", und ein kuenftig
 *       neuer schreibender Endpunkt erbt die Regel, statt auf eine vergessene Annotation zu warten.
 *   <li>Alles uebrige unter {@code /api/**} jede Sitzung — die vorhandene {@code
 *       authenticated()}-Zeile.
 * </ol>
 *
 * <p>Die offenen Wege der Anmeldung und der Einrichtung stehen davor und bleiben offen. {@code POST
 * /api/auth/logout} hat eine eigene {@code authenticated()}-Zeile vor Stufe 3: Ohne sie kaeme
 * ReadOnly aus der Sitzung nicht mehr heraus. Reicht die Rolle nicht, antwortet {@link
 * VerbotenAntwort} mit 403 als Problem Detail (A9); fehlt die Sitzung, bleibt es beim nackten 401.
 *
 * <p>Reines Wiring ohne eigene Entscheidung im Code — den Handler mit Verhalten traegt {@link
 * VerbotenAntwort}. Dass die Regel greift, weisen {@code AccessRuleIT}, {@code RollenZugangIT} und
 * {@code SecurityHeadersIT} gegen den laufenden Server nach. Deshalb ist die Klasse vom
 * Mutationstest ausgenommen (CLAUDE-java.md §5.2).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  /** Die Einstellungen (A2, Stufe 2; E1 rechnet den Briefbogen dazu). */
  private static final String[] EINSTELLUNGEN = {
    "/api/eigene-angaben", "/api/briefpapier/**", "/api/rechnung/einstellungen"
  };

  private final AuthProperties properties;
  private final SessionTokens tokens;
  private final AccountRepository accounts;
  private final SessionCookieFactory cookies;
  private final Clock clock;
  private final VerbotenAntwort verbotenAntwort;

  public SecurityConfig(
      final AuthProperties properties,
      final SessionTokens tokens,
      final AccountRepository accounts,
      final SessionCookieFactory cookies,
      final Clock clock,
      final VerbotenAntwort verbotenAntwort) {
    this.properties = properties;
    this.tokens = tokens;
    this.accounts = accounts;
    this.cookies = cookies;
    this.clock = clock;
    this.verbotenAntwort = verbotenAntwort;
  }

  /*
   * PMD.SignatureDeclareThrowsException: HttpSecurity deklariert durchgaengig "throws Exception"
   * — so bei build, csrf und authorizeHttpRequests. Einen engeren Typ gibt es nicht, und ein
   * Umwandeln im Bean hiesse, Exception aufzufangen, und waere damit ein Verstoss gegen
   * Checkstyle IllegalCatch (CLAUDE-java.md §6.5). Die Ausnahme steht methodengenau, die Regel
   * bleibt im Regelsatz scharf.
   */
  @SuppressWarnings("PMD.SignatureDeclareThrowsException")
  @Bean
  public SecurityFilterChain filterChain(final HttpSecurity http) throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .requestCache(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(
            pfade ->
                pfade
                    .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/code")
                    .permitAll()
                    .requestMatchers("/api/setup/**")
                    .permitAll()
                    .requestMatchers("/actuator/health")
                    .permitAll()
                    .requestMatchers("/error")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/auth/logout")
                    .authenticated()
                    .requestMatchers("/api/mitarbeitende/**")
                    .hasRole(Role.C_LEVEL.name())
                    .requestMatchers(HttpMethod.GET, EINSTELLUNGEN)
                    .hasAnyRole(Role.C_LEVEL.name(), Role.READ_ONLY.name())
                    .requestMatchers(EINSTELLUNGEN)
                    .hasRole(Role.C_LEVEL.name())
                    .requestMatchers(HttpMethod.POST, "/api/**")
                    .hasAnyRole(Role.C_LEVEL.name(), Role.EMPLOYEE.name())
                    .requestMatchers(HttpMethod.PUT, "/api/**")
                    .hasAnyRole(Role.C_LEVEL.name(), Role.EMPLOYEE.name())
                    .requestMatchers(HttpMethod.DELETE, "/api/**")
                    .hasAnyRole(Role.C_LEVEL.name(), Role.EMPLOYEE.name())
                    .requestMatchers("/api/**", "/actuator/info")
                    .authenticated()
                    .anyRequest()
                    .permitAll())
        .exceptionHandling(
            fehler ->
                fehler
                    .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                    .accessDeniedHandler(verbotenAntwort))
        .addFilterBefore(
            new SessionAuthenticationFilter(properties, tokens, accounts, cookies, clock),
            UsernamePasswordAuthenticationFilter.class)
        .build();
  }
}
