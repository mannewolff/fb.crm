package org.mwolff.fbcrm.auth.web;

import org.mwolff.fbcrm.auth.AuthProperties;
import org.mwolff.fbcrm.auth.domain.AccountRepository;
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
 * <p>Offen ohne Sitzung ist eine kurze, abschliessende Liste: die Anmeldung selbst, der
 * Passwort-Reset, die Einrichtung der frischen Instanz, {@code /actuator/health} — und alles, was
 * keine Schnittstelle ist, also die statischen Dateien und der SPA-Fallback (E20). Jeder andere
 * Pfad unter {@code /api} oder {@code /actuator} verlangt eine Sitzung.
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
 * <p>Reines Wiring ohne eigene Entscheidung im Code; dass die Regel greift, weisen {@code
 * AccessRuleIT} und {@code SecurityHeadersIT} gegen den laufenden Server nach. Deshalb ist die
 * Klasse vom Mutationstest ausgenommen (CLAUDE-java.md §5.2).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private final AuthProperties properties;
  private final SessionTokens tokens;
  private final AccountRepository accounts;

  public SecurityConfig(
      final AuthProperties properties,
      final SessionTokens tokens,
      final AccountRepository accounts) {
    this.properties = properties;
    this.tokens = tokens;
    this.accounts = accounts;
  }

  /*
   * PMD.SignatureDeclareThrowsException: HttpSecurity deklariert durchgaengig "throws Exception"
   * — build(), csrf(), authorizeHttpRequests(). Einen engeren Typ gibt es nicht, und ein
   * Umwandeln im Bean waere ein catch (Exception) und damit ein Verstoss gegen Checkstyle
   * IllegalCatch (CLAUDE-java.md §6.5). Die Ausnahme steht methodengenau, die Regel bleibt im
   * Regelsatz scharf.
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
                    .requestMatchers(HttpMethod.POST, "/api/auth/login")
                    .permitAll()
                    .requestMatchers("/api/auth/password-reset/**", "/api/setup/**")
                    .permitAll()
                    .requestMatchers("/actuator/health")
                    .permitAll()
                    .requestMatchers("/error")
                    .permitAll()
                    .requestMatchers("/api/**", "/actuator/info")
                    .authenticated()
                    .anyRequest()
                    .permitAll())
        .exceptionHandling(
            fehler ->
                fehler.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
        .addFilterBefore(
            new SessionAuthenticationFilter(properties, tokens, accounts),
            UsernamePasswordAuthenticationFilter.class)
        .build();
  }
}
