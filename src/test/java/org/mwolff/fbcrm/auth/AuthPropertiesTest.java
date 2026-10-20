package org.mwolff.fbcrm.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
    "fbcrm.auth.login-code-per-address=5",
    "fbcrm.auth.login-code-address-window=PT1H",
    "fbcrm.auth.login-code-per-address-daily=10",
    "fbcrm.auth.login-code-daily-window=PT24H",
    "fbcrm.auth.login-code-ttl=PT10M",
    "fbcrm.auth.login-code-max-attempts=5",
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
                "fbcrm.auth.login-code-per-address=3",
                "fbcrm.auth.login-code-address-window=PT2H",
                "fbcrm.auth.login-code-per-address-daily=7",
                "fbcrm.auth.login-code-daily-window=PT48H",
                "fbcrm.auth.login-code-ttl=PT5M",
                "fbcrm.auth.login-code-max-attempts=3",
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
                            3,
                            Duration.ofHours(2),
                            7,
                            Duration.ofHours(48),
                            Duration.ofMinutes(5),
                            3,
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

  @ParameterizedTest
  @ValueSource(
      strings = {
        "fbcrm.auth.login-code-per-address=0",
        "fbcrm.auth.login-code-per-address-daily=0"
      })
  void binding_givenAnAddressLimitBelowOne_thenTheContextFails(final String schalter) {
    // When / Then — 0 wiese jede Anforderung sofort ab, auch die erste.
    runner.withPropertyValues(mit(schalter)).run(context -> assertThat(context).hasFailed());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"fbcrm.auth.login-code-address-window=", "fbcrm.auth.login-code-daily-window="})
  void binding_givenNoAddressWindow_thenTheContextFails(final String schalter) {
    // When / Then — ohne Fenster gaebe es keine Grenze, nur einen Zaehler.
    runner.withPropertyValues(mit(schalter)).run(context -> assertThat(context).hasFailed());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "fbcrm.auth.login-code-per-address=1",
        "fbcrm.auth.login-code-per-address-daily=1"
      })
  void binding_givenAnAddressLimitOfOne_thenTheContextStarts(final String schalter) {
    // When / Then — die untere Schranke ist 1, nicht 2.
    runner.withPropertyValues(mit(schalter)).run(context -> assertThat(context).hasNotFailed());
  }

  @Test
  void binding_givenNoLoginCodeTtl_thenTheContextFails() {
    // When / Then — Kriterium 4 verlangt eine begrenzte Zeit; ohne Angabe gaebe es keine.
    runner
        .withPropertyValues(mit("fbcrm.auth.login-code-ttl="))
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void binding_givenAMaximumOfZeroFailedAttempts_thenTheContextFails() {
    // When / Then — 0 liesse jeden Code schon vor der ersten Eingabe verfallen.
    runner
        .withPropertyValues(mit("fbcrm.auth.login-code-max-attempts=0"))
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void binding_givenAMaximumOfOneFailedAttempt_thenTheContextStarts() {
    // When / Then — die untere Schranke ist 1, nicht 2.
    runner
        .withPropertyValues(mit("fbcrm.auth.login-code-max-attempts=1"))
        .run(context -> assertThat(context).hasNotFailed());
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
            GEHEIMNIS,
            Duration.ofDays(1),
            "fbcrm_session",
            true,
            10,
            Duration.ofMinutes(15),
            5,
            Duration.ofHours(1),
            10,
            Duration.ofHours(24),
            Duration.ofMinutes(10),
            5,
            roh);

    // Then
    assertThat(schalter.trustedProxies()).containsExactly("10.0.0.1");
  }

  @Configuration
  @EnableConfigurationProperties(AuthProperties.class)
  static class AuthPropertiesKonfiguration {}
}
