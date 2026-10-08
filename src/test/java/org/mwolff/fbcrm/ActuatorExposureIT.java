package org.mwolff.fbcrm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Prueft, dass der Actuator nur health und info nach aussen zeigt (CLAUDE-security.md). */
class ActuatorExposureIT extends AbstractIntegrationTest {

  private final TestRestTemplate rest;

  @Autowired
  ActuatorExposureIT(final TestRestTemplate rest) {
    this.rest = rest;
  }

  @Test
  void health_thenAnswersUp() {
    // When
    final ResponseEntity<String> antwort = rest.getForEntity("/actuator/health", String.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void health_thenCarriesNoDetails() {
    // When
    final ResponseEntity<String> antwort = rest.getForEntity("/actuator/health", String.class);

    // Then
    assertThat(antwort.getBody()).doesNotContain("components").doesNotContain("details");
  }

  @Test
  void env_thenNotReachable() {
    // When
    final ResponseEntity<String> antwort = rest.getForEntity("/actuator/env", String.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void beans_thenNotReachable() {
    // When
    final ResponseEntity<String> antwort = rest.getForEntity("/actuator/beans", String.class);

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }
}
