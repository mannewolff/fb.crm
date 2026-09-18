package org.mwolff.fbcrm.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Bindung und Validierung der Schalter des Postausgangsfachs ({@code fbcrm.outbox.*}).
 *
 * <p>Die untere Schranke ist der eigentliche Gegenstand: Ein Poll-Intervall von 0 liesse den
 * Hintergrundjob ohne Pause laufen, eine Versuchszahl von 0 gaebe jeden Auftrag sofort auf, und
 * eine Aufbewahrung von 0 Tagen loeschte zugestellte Auftraege noch in derselben Stunde.
 */
class OutboxPropertiesTest {

  private static final String[] VOLLSTAENDIG = {
    "fbcrm.outbox.enabled=true",
    "fbcrm.outbox.poll-interval-ms=5000",
    "fbcrm.outbox.max-attempts=8",
    "fbcrm.outbox.retention-days=7"
  };

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(OutboxPropertiesKonfiguration.class);

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
                assertThat(context.getBean(OutboxProperties.class))
                    .isEqualTo(new OutboxProperties(true, 5000L, 8, 7)));
  }

  @Test
  void binding_givenNoValueAtAll_thenTheDispatcherIsOff() {
    // When / Then — ohne Angabe laeuft kein Hintergrundjob.
    runner
        .withPropertyValues(
            "fbcrm.outbox.poll-interval-ms=5000",
            "fbcrm.outbox.max-attempts=8",
            "fbcrm.outbox.retention-days=7")
        .run(context -> assertThat(context.getBean(OutboxProperties.class).enabled()).isFalse());
  }

  @Test
  void binding_givenAPollIntervalOfZero_thenTheContextFails() {
    // When / Then
    runner
        .withPropertyValues(mit("fbcrm.outbox.poll-interval-ms=0"))
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void binding_givenNoAttemptAtAll_thenTheContextFails() {
    // When / Then
    runner
        .withPropertyValues(mit("fbcrm.outbox.max-attempts=0"))
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void binding_givenARetentionOfZeroDays_thenTheContextFails() {
    // When / Then
    runner
        .withPropertyValues(mit("fbcrm.outbox.retention-days=0"))
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void retryBackoffBase_thenIsThePollInterval() {
    // When / Then — der erste Wiederholungsabstand ist der naechste Durchgang.
    assertThat(new OutboxProperties(true, 5000L, 8, 7).retryBackoffBase())
        .isEqualTo(Duration.ofSeconds(5));
  }

  @Test
  void retention_thenIsTheConfiguredNumberOfDays() {
    // When / Then
    assertThat(new OutboxProperties(true, 5000L, 8, 7).retention()).isEqualTo(Duration.ofDays(7));
  }

  @Configuration
  @EnableConfigurationProperties(OutboxProperties.class)
  static class OutboxPropertiesKonfiguration {}
}
