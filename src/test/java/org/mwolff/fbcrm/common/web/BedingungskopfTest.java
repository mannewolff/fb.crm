package org.mwolff.fbcrm.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;

class BedingungskopfTest {

  @Test
  void aus_givenAQuotedVersion_thenReadsIt() {
    // Given — E2: die Oberflaeche legt den Stand als If-Match: "3" vor.

    // When
    final Bedingungskopf kopf = Bedingungskopf.aus("\"3\"");

    // Then
    assertThat(kopf.version()).isEqualTo(3L);
  }

  @Test
  void aus_givenTheLargestVersionThatFits_thenReadsIt() {
    // When
    final Bedingungskopf kopf = Bedingungskopf.aus("\"999999999999999999\"");

    // Then
    assertThat(kopf.version()).isEqualTo(999_999_999_999_999_999L);
  }

  @Test
  void aus_givenNoHeader_thenRefusesWith428() {
    // Given — E3: ein fehlender Kopf waere ein stilles Loch im Schutz.

    // When / Then
    assertThatThrownBy(() -> Bedingungskopf.aus(null))
        .isInstanceOf(Bedingungskopf.Fehlt.class)
        .satisfies(
            fehler -> assertThat(statusOf(fehler)).isEqualTo(HttpStatus.PRECONDITION_REQUIRED));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "3",
        "\"\"",
        "\"3",
        "3\"",
        "W/\"3\"",
        "*",
        "\"3\", \"4\"",
        "\"-3\"",
        "\"drei\"",
        " \"3\"",
        "\"1000000000000000000\""
      })
  void aus_givenAnUnreadableHeader_thenRefusesWith428(final String kopf) {
    // When / Then — nur ein starkes Etikett aus Ziffern ist ein Stand, den der Server vergeben hat.
    assertThatThrownBy(() -> Bedingungskopf.aus(kopf)).isInstanceOf(Bedingungskopf.Fehlt.class);
  }

  @Test
  void fehlt_thenSaysWhatIsMissingWithoutEchoingTheHeader() {
    // When
    final ResponseStatus status = Bedingungskopf.Fehlt.class.getAnnotation(ResponseStatus.class);

    // Then — der Kopf stammt vom Aufrufer und geht nicht in die Antwort.
    assertThat(status.reason()).isEqualTo(Bedingungskopf.MELDUNG);
    assertThatThrownBy(() -> Bedingungskopf.aus("\"<script>\""))
        .satisfies(fehler -> assertThat(fehler.getMessage()).isNull());
  }

  @Test
  void anfrage_givenIfMatch_thenTheControllerReceivesTheVersion() throws Exception {
    // Given — E12: je Controller ein zusaetzlicher Parameter, gelesen von Bedingungskopf.Leser.
    final Schreiber schreiber = new Schreiber();

    // When / Then
    mockMvc(schreiber)
        .perform(put("/schreiben").header(HttpHeaders.IF_MATCH, "\"3\""))
        .andExpect(status().isOk())
        .andExpect(content().string("3"));
    assertThat(schreiber.aufrufe.get()).isOne();
  }

  @Test
  void anfrage_givenNoIfMatch_thenAnswers428AndWritesNothing() throws Exception {
    // Given
    final Schreiber schreiber = new Schreiber();

    // When / Then — E3: abgewiesen, bevor der Weg zum Zuge kommt.
    mockMvc(schreiber).perform(put("/schreiben")).andExpect(status().isPreconditionRequired());
    assertThat(schreiber.aufrufe.get()).isZero();
  }

  @Test
  void anfrage_givenAnUnreadableIfMatch_thenAnswers428AndWritesNothing() throws Exception {
    // Given
    final Schreiber schreiber = new Schreiber();

    // When / Then
    mockMvc(schreiber)
        .perform(put("/schreiben").header(HttpHeaders.IF_MATCH, "*"))
        .andExpect(status().isPreconditionRequired());
    assertThat(schreiber.aufrufe.get()).isZero();
  }

  @Test
  void leser_givenAnotherParameterType_thenLeavesItAlone() throws NoSuchMethodException {
    // Given
    final Bedingungskopf.Leser leser = new Bedingungskopf.Leser();

    // When / Then
    assertThat(
            leser.supportsParameter(
                new MethodParameter(
                    Schreiber.class.getDeclaredMethod("andererParameter", String.class), 0)))
        .isFalse();
  }

  @Test
  void konfiguration_thenRegistersTheReader() {
    // Given
    final List<HandlerMethodArgumentResolver> leser = new ArrayList<>();

    // When
    new BedingungskopfKonfiguration().addArgumentResolvers(leser);

    // Then
    assertThat(leser).singleElement().isInstanceOf(Bedingungskopf.Leser.class);
  }

  private static MockMvc mockMvc(final Schreiber schreiber) {
    return MockMvcBuilders.standaloneSetup(schreiber)
        .setCustomArgumentResolvers(new Bedingungskopf.Leser())
        .setControllerAdvice(new GlobalExceptionHandler(konto -> Optional.empty()))
        .build();
  }

  private static HttpStatus statusOf(final Throwable fehler) {
    return fehler.getClass().getAnnotation(ResponseStatus.class).code();
  }

  /** Ein schreibender Weg, wie ihn jeder geschuetzte Bereich traegt. */
  @RestController
  static final class Schreiber {

    private final AtomicInteger aufrufe = new AtomicInteger();

    @PutMapping("/schreiben")
    long schreiben(final Bedingungskopf kopf) {
      aufrufe.incrementAndGet();
      return kopf.version();
    }

    void andererParameter(final String text) {
      // Nur fuer supportsParameter.
    }
  }
}
