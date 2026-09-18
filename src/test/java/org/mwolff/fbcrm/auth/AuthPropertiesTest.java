package org.mwolff.fbcrm.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Bindung und Validierung der Auth-Schalter.
 *
 * <p>Der wichtigste Fall ist der Fehlschlag: Ein zu kurzes Session-Geheimnis darf die Anwendung
 * nicht starten lassen, statt stillschweigend schwach zu signieren.
 */
class AuthPropertiesTest {

  private static final String GEHEIMNIS = "geheimnis-mit-mindestens-32-zeichen-laenge";

  private static final String[] VOLLSTAENDIG = {
    "fbcrm.auth.session-secret=" + GEHEIMNIS,
    "fbcrm.auth.session-ttl=P1D",
    "fbcrm.auth.cookie-name=fbcrm_session",
    "fbcrm.auth.cookie-secure=true",
    "fbcrm.auth.login-max-attempts=10",
    "fbcrm.auth.login-attempt-window=PT15M",
    "fbcrm.auth.trusted-proxies="
  };

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(AuthPropertiesKonfiguration.class);

  /** Die vollstaendige Konfiguration; spaetere Eintraege stechen fruehere aus. */
  private static String[] mit(final String... abweichungen) {
    return Stream.concat(Stream.of(VOLLSTAENDIG), Stream.of(abweichungen)).toArray(String[]::new);
  }

  @Test
  void binding_givenACompleteConfiguration_thenCarriesEveryValue() {
    // When / Then
    runner
        .withPropertyValues(
            mit(
                "fbcrm.auth.session-ttl=PT12H",
                "fbcrm.auth.cookie-secure=false",
                "fbcrm.auth.login-max-attempts=5",
                "fbcrm.auth.login-attempt-window=PT1M",
                "fbcrm.auth.trusted-proxies=10.0.0.1,10.0.0.2"))
        .run(
            context ->
                assertThat(context.getBean(AuthProperties.class))
                    .isEqualTo(
                        new AuthProperties(
                            GEHEIMNIS,
                            Duration.ofHours(12),
                            "fbcrm_session",
                            false,
                            5,
                            Duration.ofMinutes(1),
                            List.of("10.0.0.1", "10.0.0.2"))));
  }

  @Test
  void binding_givenASecretShorterThan32Characters_thenTheContextFails() {
    // When / Then
    runner
        .withPropertyValues(mit("fbcrm.auth.session-secret=zu-kurz"))
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void binding_givenNoSecretAtAll_thenTheContextFails() {
    // When / Then
    runner
        .withPropertyValues(mit("fbcrm.auth.session-secret="))
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void binding_givenAThresholdBelowOne_thenTheContextFails() {
    // When / Then — ohne untere Schranke wiese 0 jede Anmeldung sofort mit 429 ab.
    runner
        .withPropertyValues(mit("fbcrm.auth.login-max-attempts=0"))
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void binding_givenAnEmptyTrustedProxyList_thenCarriesNoProxyAtAll() {
    // When / Then — der leere Wert darf keine Adresse ergeben, die keine ist (E9).
    runner
        .withPropertyValues(mit())
        .run(
            context ->
                assertThat(context.getBean(AuthProperties.class).trustedProxies()).isEmpty());
  }

  @Test
  void constructor_givenBlankAndPaddedProxies_thenKeepsOnlyTheTrimmedAddresses() {
    // Given
    final List<String> roh = List.of("", "   ", " 10.0.0.1 ");

    // When
    final AuthProperties schalter =
        new AuthProperties(
            GEHEIMNIS, Duration.ofDays(1), "fbcrm_session", true, 10, Duration.ofMinutes(15), roh);

    // Then
    assertThat(schalter.trustedProxies()).containsExactly("10.0.0.1");
  }

  @Configuration
  @EnableConfigurationProperties(AuthProperties.class)
  static class AuthPropertiesKonfiguration {}
}
