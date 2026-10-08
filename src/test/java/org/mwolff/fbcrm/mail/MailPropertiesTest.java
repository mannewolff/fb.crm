package org.mwolff.fbcrm.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Bindung der Mail-Schalter ({@code fbcrm.mail.*}).
 *
 * <p>Der Gegenstand ist die oeffentliche Adresse: Aus ihr entsteht der Link in der Reset-Mail. Ein
 * abschliessender Schraegstrich in {@code FBCRM_BASE_URL} ergaebe sonst einen Link mit doppeltem
 * Trenner, und den nimmt nicht jeder Mailclient als eine Adresse.
 */
class MailPropertiesTest {

  private static final String[] VOLLSTAENDIG = {
    "fbcrm.mail.enabled=true",
    "fbcrm.mail.from=no-reply@fbcrm.local",
    "fbcrm.mail.base-url=https://crm.example.org"
  };

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(MailPropertiesKonfiguration.class);

  private static String[] mit(final String... abweichungen) {
    return Stream.concat(Stream.of(VOLLSTAENDIG), Stream.of(abweichungen)).toArray(String[]::new);
  }

  @Test
  void binding_givenACompleteConfiguration_thenCarriesEveryValue() {
    // When / Then
    runner
        .withPropertyValues(mit())
        .run(
            context ->
                assertThat(context.getBean(MailProperties.class))
                    .isEqualTo(
                        new MailProperties(
                            true, "no-reply@fbcrm.local", "https://crm.example.org")));
  }

  @Test
  void binding_givenNoValueAtAll_thenSendingIsOff() {
    // When / Then — ohne Angabe geht keine Mail hinaus.
    runner
        .withPropertyValues(
            "fbcrm.mail.from=no-reply@fbcrm.local", "fbcrm.mail.base-url=https://crm.example.org")
        .run(context -> assertThat(context.getBean(MailProperties.class).enabled()).isFalse());
  }

  @Test
  void binding_givenNoSenderAddress_thenTheContextFails() {
    // When / Then
    runner
        .withPropertyValues(mit("fbcrm.mail.from="))
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void binding_givenNoBaseUrl_thenTheContextFails() {
    // When / Then — ohne oeffentliche Adresse entstuende ein Link, der ins Leere zeigt.
    runner
        .withPropertyValues(mit("fbcrm.mail.base-url="))
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void constructor_givenABaseUrlWithATrailingSlash_thenDropsIt() {
    // When
    final MailProperties schalter =
        new MailProperties(true, "no-reply@fbcrm.local", "https://crm.example.org/");

    // Then
    assertThat(schalter.baseUrl()).isEqualTo("https://crm.example.org");
  }

  @Test
  void constructor_givenABaseUrlWithoutATrailingSlash_thenKeepsIt() {
    // When
    final MailProperties schalter =
        new MailProperties(true, "no-reply@fbcrm.local", "https://crm.example.org");

    // Then
    assertThat(schalter.baseUrl()).isEqualTo("https://crm.example.org");
  }

  @Configuration
  @EnableConfigurationProperties(MailProperties.class)
  static class MailPropertiesKonfiguration {}
}
