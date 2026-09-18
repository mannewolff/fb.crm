package org.mwolff.fbcrm.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

/**
 * Der SPA-Fallback (E20): Was keine Datei ist, wird zur Oberflaeche — ausser unter {@code /api} und
 * {@code /actuator}.
 *
 * <p>Geprueft wird gegen echte Dateien auf dem Test-Klassenpfad ({@code spa/index.html}, {@code
 * spa/vorhanden.txt}) und nicht gegen eine nachgebaute {@code Resource}: Ob eine Datei gefunden
 * wird, entscheidet Spring, und genau das soll hier mitgeprueft sein.
 */
class SpaForwardingTest {

  private static final Resource ORT = new ClassPathResource("spa/");
  private static final Resource INDEX = new ClassPathResource("spa/index.html");

  private final SpaResourceResolver resolver = new SpaResourceResolver(INDEX);

  @Test
  void getResource_givenAnUnknownPath_thenForwardsToIndexHtml() throws IOException {
    // When
    final Resource aufgeloest = resolver.getResource("anfragen/17", ORT);

    // Then
    assertThat(aufgeloest).isEqualTo(INDEX);
  }

  @Test
  void getResource_givenAnExistingFile_thenServesTheFileItself() throws IOException {
    // When
    final Resource aufgeloest = resolver.getResource("vorhanden.txt", ORT);

    // Then
    assertThat(aufgeloest).isNotNull().isNotEqualTo(INDEX);
  }

  @ParameterizedTest
  @ValueSource(strings = {"api", "api/unknown", "actuator", "actuator/unknown"})
  void getResource_givenAnApiOrActuatorPath_thenResolvesToNothing(final String pfad)
      throws IOException {
    // When
    final Resource aufgeloest = resolver.getResource(pfad, ORT);

    // Then
    assertThat(aufgeloest).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api/unknown", "/actuator/unknown"})
  void getResource_givenALeadingSlash_thenStillResolvesToNothing(final String pfad)
      throws IOException {
    // When — je nach Spring-Fassung kommt der Pfad mit oder ohne fuehrenden Schraegstrich an.
    final Resource aufgeloest = resolver.getResource(pfad, ORT);

    // Then
    assertThat(aufgeloest).isNull();
  }

  @Test
  void getResource_givenAPathThatOnlyStartsLikeTheApi_thenForwardsToIndexHtml() throws IOException {
    // When — "apiary" faengt mit "api" an, gehoert aber zur Oberflaeche.
    final Resource aufgeloest = resolver.getResource("apiary", ORT);

    // Then
    assertThat(aufgeloest).isEqualTo(INDEX);
  }

  @Test
  void getResource_givenNoIndexHtmlAtAll_thenResolvesToNothing() throws IOException {
    // Given — vor dem ersten Frontend-Build gibt es kein index.html.
    final SpaResourceResolver ohneIndex =
        new SpaResourceResolver(new ClassPathResource("spa/gibt-es-nicht.html"));

    // When
    final Resource aufgeloest = ohneIndex.getResource("anfragen/17", ORT);

    // Then
    assertThat(aufgeloest).isNull();
  }
}
