package org.mwolff.fbcrm.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Bindung des Einmal-Schluessels aus {@code fbcrm.setup.*}.
 *
 * <p>Der interessante Fall ist der fehlende Wert: {@code docker-compose.yml} sagt „leer =
 * deaktiviert" zu. Damit {@code SetupAccountUseCase} diese Zusage einloesen kann, muss die Bindung
 * einen Leerstring liefern und nicht {@code null} — sonst entschiede eine NullPointerException
 * darueber, ob die Einrichtung offen steht.
 */
class SetupPropertiesTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(SetupPropertiesKonfiguration.class);

  @Test
  void binding_givenAConfiguredToken_thenCarriesIt() {
    // When / Then
    runner
        .withPropertyValues("fbcrm.setup.bootstrap-token=einmal-schluessel")
        .run(
            context ->
                assertThat(context.getBean(SetupProperties.class).bootstrapToken())
                    .isEqualTo("einmal-schluessel"));
  }

  @Test
  void binding_givenNoTokenAtAll_thenIsEmptyAndNotNull() {
    // When / Then — leer heisst deaktiviert, nicht "nicht gesetzt".
    runner.run(
        context -> assertThat(context.getBean(SetupProperties.class).bootstrapToken()).isEmpty());
  }

  @Test
  void binding_givenAnEmptyToken_thenStaysEmpty() {
    // When / Then — so steht es in docker-compose.yml Z. 48.
    runner
        .withPropertyValues("fbcrm.setup.bootstrap-token=")
        .run(
            context ->
                assertThat(context.getBean(SetupProperties.class).bootstrapToken()).isEmpty());
  }

  @Configuration
  @EnableConfigurationProperties(SetupProperties.class)
  static class SetupPropertiesKonfiguration {}
}
