package org.mwolff.fbcrm.auth.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.mwolff.fbcrm.common.web.GlobalExceptionHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Die Antwort der Zugangsregel, wenn eine Sitzung da ist, ihre Rolle den Weg aber nicht erlaubt
 * (Plan #414, A9).
 *
 * <p>Status 403 als RFC-9457 Problem Detail mit dem einen Satz aus {@link
 * GlobalExceptionHandler#VERBOTEN_DETAIL}: {@code api/client.ts} liest {@code detail}, und ohne
 * Rumpf saehe der Nutzer nur den allgemeinen Fehlertext. Der Satz steht an einer Stelle, weil zwei
 * Meldungen fuer dieselbe Sache auseinanderliefen. Die Meldung der Ausnahme geht nicht in die
 * Antwort (CLAUDE-security.md).
 *
 * <p>Eine eigene Klasse und kein Lambda in {@link SecurityConfig}: Wer Status, Medientyp und einen
 * Satz schreibt, hat Verhalten und gehoert in Abdeckung und Mutationstest, waehrend die
 * Konfiguration als reines Wiring davon ausgenommen ist.
 */
@Component
public class VerbotenAntwort implements AccessDeniedHandler {

  private final ObjectMapper json;

  public VerbotenAntwort(final ObjectMapper json) {
    this.json = json;
  }

  @Override
  public void handle(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final AccessDeniedException accessDeniedException)
      throws IOException {
    response.setStatus(HttpStatus.FORBIDDEN.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    json.writeValue(
        response.getOutputStream(),
        ProblemDetail.forStatusAndDetail(
            HttpStatus.FORBIDDEN, GlobalExceptionHandler.VERBOTEN_DETAIL));
  }
}
