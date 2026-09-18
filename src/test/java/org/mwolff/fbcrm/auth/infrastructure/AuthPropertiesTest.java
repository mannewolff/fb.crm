package org.mwolff.fbcrm.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
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

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(AuthPropertiesKonfiguration.class);

  @Test
  void binding_givenACompleteConfiguration_thenCarriesEveryValue() {
    // When / Then
    runner
        .withPropertyValues(
            "fbcrm.auth.session-secret=" + GEHEIMNIS,
            "fbcrm.auth.session-ttl=PT12H",
            "fbcrm.auth.cookie-name=fbcrm_session",
            "fbcrm.auth.cookie-secure=false")
        .run(
            context ->
                assertThat(context.getBean(AuthProperties.class))
                    .isEqualTo(
                        new AuthProperties(
                            GEHEIMNIS, Duration.ofHours(12), "fbcrm_session", false)));
  }

  @Test
  void binding_givenASecretShorterThan32Characters_thenTheContextFails() {
    // When / Then
    runner
        .withPropertyValues(
            "fbcrm.auth.session-secret=zu-kurz",
            "fbcrm.auth.session-ttl=P1D",
            "fbcrm.auth.cookie-name=fbcrm_session",
            "fbcrm.auth.cookie-secure=true")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void binding_givenNoSecretAtAll_thenTheContextFails() {
    // When / Then
    runner
        .withPropertyValues(
            "fbcrm.auth.session-secret=",
            "fbcrm.auth.session-ttl=P1D",
            "fbcrm.auth.cookie-name=fbcrm_session",
            "fbcrm.auth.cookie-secure=true")
        .run(context -> assertThat(context).hasFailed());
  }

  @Configuration
  @EnableConfigurationProperties(AuthProperties.class)
  static class AuthPropertiesKonfiguration {}
}
