package org.mwolff.fbcrm.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.validation.FieldError;

/**
 * Bindung und Validierung des Objektspeicher-Zugangs {@code fbcrm.minio.*}.
 *
 * <p>Keiner der vier Werte hat einen brauchbaren Default: {@code application.yml} reicht die
 * Umgebungsvariablen leer durch, und genau dann soll die Bindung scheitern, statt die Anwendung mit
 * einem Zugang laufen zu lassen, den niemand gesetzt hat (CLAUDE-security.md). Geprueft wird
 * deshalb je Wert, dass sein Fehlen <b>eine</b> Verletzung unter seinem Namen meldet — nicht nur,
 * dass irgendetwas scheitert.
 */
class MinioPropertiesTest {

  private static final String ENDPOINT = "fbcrm.minio.endpoint=http://localhost:9000";
  private static final String ACCESS_KEY = "fbcrm.minio.access-key=fbcrm";
  private static final String SECRET_KEY = "fbcrm.minio.secret-key=fbcrm-minio";
  private static final String BUCKET = "fbcrm.minio.bucket=fbcrm-anhaenge";

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(MinioKonfiguration.class);

  /** Die Namen der Felder, zu denen die Bindung eine Verletzung gemeldet hat. */
  private static List<String> verletzteFelder(final AssertableApplicationContext context) {
    final Throwable wurzel = NestedExceptionUtils.getRootCause(context.getStartupFailure());
    return ((BindValidationException) wurzel)
        .getValidationErrors().getAllErrors().stream()
            .map(FieldError.class::cast)
            .map(FieldError::getField)
            .toList();
  }

  @Test
  void binding_givenEveryValue_thenEachOneArrivesUnchanged() {
    // When / Then
    runner
        .withPropertyValues(ENDPOINT, ACCESS_KEY, SECRET_KEY, BUCKET)
        .run(
            context ->
                assertThat(context.getBean(MinioProperties.class))
                    .extracting(
                        MinioProperties::endpoint,
                        MinioProperties::accessKey,
                        MinioProperties::secretKey,
                        MinioProperties::bucket)
                    .containsExactly(
                        "http://localhost:9000", "fbcrm", "fbcrm-minio", "fbcrm-anhaenge"));
  }

  @Test
  void binding_givenAnEmptyEndpoint_thenReportsExactlyThatOneViolation() {
    // When / Then
    runner
        .withPropertyValues("fbcrm.minio.endpoint=", ACCESS_KEY, SECRET_KEY, BUCKET)
        .run(context -> assertThat(verletzteFelder(context)).containsExactly("endpoint"));
  }

  @Test
  void binding_givenAnEmptyAccessKey_thenReportsExactlyThatOneViolation() {
    // When / Then
    runner
        .withPropertyValues(ENDPOINT, "fbcrm.minio.access-key=", SECRET_KEY, BUCKET)
        .run(context -> assertThat(verletzteFelder(context)).containsExactly("accessKey"));
  }

  @Test
  void binding_givenAnEmptySecretKey_thenReportsExactlyThatOneViolation() {
    // When / Then
    runner
        .withPropertyValues(ENDPOINT, ACCESS_KEY, "fbcrm.minio.secret-key=", BUCKET)
        .run(context -> assertThat(verletzteFelder(context)).containsExactly("secretKey"));
  }

  @Test
  void binding_givenAnEmptyBucket_thenReportsExactlyThatOneViolation() {
    // When / Then
    runner
        .withPropertyValues(ENDPOINT, ACCESS_KEY, SECRET_KEY, "fbcrm.minio.bucket=")
        .run(context -> assertThat(verletzteFelder(context)).containsExactly("bucket"));
  }

  @Configuration
  @EnableConfigurationProperties(MinioProperties.class)
  static class MinioKonfiguration {}
}
