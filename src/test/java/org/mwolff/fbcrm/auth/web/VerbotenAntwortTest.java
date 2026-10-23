package org.mwolff.fbcrm.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.common.web.GlobalExceptionHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

/**
 * Die Antwort der Zugangsregel, wenn eine Sitzung da ist, ihre Rolle aber nicht reicht (Plan #414,
 * A9).
 *
 * <p>Gebaut wird der {@link ObjectMapper} wie in der Anwendung ueber den {@link
 * Jackson2ObjectMapperBuilder}: Erst er kennt die Abbildung von {@code ProblemDetail}, und genau
 * die soll hier mitgeprueft sein.
 */
class VerbotenAntwortTest {

  private final ObjectMapper json = Jackson2ObjectMapperBuilder.json().build();
  private final VerbotenAntwort antwort = new VerbotenAntwort(json);

  @Test
  void handle_thenAnswersForbidden() throws IOException {
    // Given
    final MockHttpServletResponse response = new MockHttpServletResponse();

    // When
    antwort.handle(anfrage(), response, new AccessDeniedException("Zugriff verweigert"));

    // Then
    assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
  }

  @Test
  void handle_thenTheBodyIsAProblemDetail() throws IOException {
    // Given — api/client.ts liest detail nur aus einem Problem Detail.
    final MockHttpServletResponse response = new MockHttpServletResponse();

    // When
    antwort.handle(anfrage(), response, new AccessDeniedException("Zugriff verweigert"));

    // Then
    assertThat(MediaType.parseMediaType(response.getContentType()))
        .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
  }

  @Test
  void handle_thenDetailIsTheOneSentenceOfTheExceptionHandler() throws IOException {
    // Given — ein Satz an einer Stelle, wie Uploadgrenze.MELDUNG.
    final MockHttpServletResponse response = new MockHttpServletResponse();

    // When
    antwort.handle(anfrage(), response, new AccessDeniedException("Zugriff verweigert"));

    // Then
    final JsonNode rumpf = json.readTree(response.getContentAsByteArray());
    assertThat(rumpf.path("detail").asText()).isEqualTo(GlobalExceptionHandler.VERBOTEN_DETAIL);
    assertThat(rumpf.path("status").asInt()).isEqualTo(HttpStatus.FORBIDDEN.value());
  }

  @Test
  void handle_thenTheMessageOfTheExceptionStaysInside() throws IOException {
    // Given — die Meldung von Spring Security sagt dem Aufrufer nichts (CLAUDE-security.md).
    final MockHttpServletResponse response = new MockHttpServletResponse();

    // When
    antwort.handle(anfrage(), response, new AccessDeniedException("interne Begruendung"));

    // Then
    assertThat(response.getContentAsString()).doesNotContain("interne Begruendung");
  }

  private static MockHttpServletRequest anfrage() {
    return new MockHttpServletRequest("PUT", "/api/rechnung/einstellungen");
  }
}
