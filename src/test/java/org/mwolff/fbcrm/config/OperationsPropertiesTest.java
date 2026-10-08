package org.mwolff.fbcrm.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Bindung des Betriebsschalters {@code fbcrm.dev-mode}.
 *
 * <p>Der Default ist {@code false} und damit der strengere der beiden Faelle: Wer den
 * Entwicklungsmodus will, sagt es ausdruecklich. Ein Default von {@code true} liesse eine Instanz
 * ohne gesetzte Variable mit den unsicheren Vorgaben laufen.
 */
class OperationsPropertiesTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(OperationsPropertiesKonfiguration.class);

  @Test
  void binding_givenDevModeTrue_thenTheSwitchIsOn() {
    // When / Then
    runner
        .withPropertyValues("fbcrm.dev-mode=true")
        .run(context -> assertThat(context.getBean(OperationsProperties.class).devMode()).isTrue());
  }

  @Test
  void binding_givenDevModeFalse_thenTheSwitchIsOff() {
    // When / Then
    runner
        .withPropertyValues("fbcrm.dev-mode=false")
        .run(
            context -> assertThat(context.getBean(OperationsProperties.class).devMode()).isFalse());
  }

  @Test
  void binding_givenNoValueAtAll_thenDefaultsToProduction() {
    // When / Then — ohne Angabe gilt der strengere Fall.
    runner.run(
        context -> assertThat(context.getBean(OperationsProperties.class).devMode()).isFalse());
  }

  @Configuration
  @EnableConfigurationProperties(OperationsProperties.class)
  static class OperationsPropertiesKonfiguration {}
}
