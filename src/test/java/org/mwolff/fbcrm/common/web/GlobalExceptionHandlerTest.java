package org.mwolff.fbcrm.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void handleBeanValidation_givenFieldErrors_thenReturns400WithFieldErrors()
      throws NoSuchMethodException {
    // Given
    final MethodArgumentNotValidException exception =
        validationException(
            new FieldError("antrag", "email", "darf nicht leer sein"),
            new FieldError("antrag", "name", "ist zu kurz"));

    // When
    final ProblemDetail problem = handler.handleBeanValidation(exception);

    // Then
    assertThat(problem.getProperties())
        .containsEntry(
            "fieldErrors",
            Map.of(
                "email", List.of("darf nicht leer sein"),
                "name", List.of("ist zu kurz")));
  }

  @Test
  void handleBeanValidation_givenFieldErrors_thenStatusIsBadRequest() throws NoSuchMethodException {
    // Given
    final MethodArgumentNotValidException exception =
        validationException(new FieldError("antrag", "email", "darf nicht leer sein"));

    // When
    final ProblemDetail problem = handler.handleBeanValidation(exception);

    // Then
    assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
  }

  @Test
  void handleBeanValidation_givenFieldErrors_thenTitleNamesTheInvalidInput()
      throws NoSuchMethodException {
    // Given
    final MethodArgumentNotValidException exception =
        validationException(new FieldError("antrag", "email", "darf nicht leer sein"));

    // When
    final ProblemDetail problem = handler.handleBeanValidation(exception);

    // Then
    assertThat(problem.getTitle()).isEqualTo("Ungueltige Eingabe");
  }

  @Test
  void handleBeanValidation_givenTwoErrorsOnSameField_thenKeepsBothMessages()
      throws NoSuchMethodException {
    // Given
    final MethodArgumentNotValidException exception =
        validationException(
            new FieldError("antrag", "email", "darf nicht leer sein"),
            new FieldError("antrag", "email", "ist keine Adresse"));

    // When
    final ProblemDetail problem = handler.handleBeanValidation(exception);

    // Then
    assertThat(problem.getProperties())
        .containsEntry(
            "fieldErrors", Map.of("email", List.of("darf nicht leer sein", "ist keine Adresse")));
  }

  @Test
  void handleBeanValidation_givenFieldErrorWithoutMessage_thenUsesFallbackText()
      throws NoSuchMethodException {
    // Given
    final MethodArgumentNotValidException exception =
        validationException(new FieldError("antrag", "email", null));

    // When
    final ProblemDetail problem = handler.handleBeanValidation(exception);

    // Then
    assertThat(problem.getProperties())
        .containsEntry(
            "fieldErrors", Map.of("email", List.of(GlobalExceptionHandler.FALLBACK_FIELD_MESSAGE)));
  }

  @Test
  void handleUnexpected_givenAnnotatedDomainException_thenUsesStatusFromAnnotation() {
    // Given
    final Exception exception = new KontoNichtGefunden("Konto 7 gibt es nicht.");

    // When
    final ProblemDetail problem = handler.handleUnexpected(exception);

    // Then
    assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
  }

  @Test
  void handleUnexpected_givenAnnotatedDomainException_thenPassesItsMessageThrough() {
    // Given
    final Exception exception = new KontoNichtGefunden("Konto 7 gibt es nicht.");

    // When
    final ProblemDetail problem = handler.handleUnexpected(exception);

    // Then
    assertThat(problem.getDetail()).isEqualTo("Konto 7 gibt es nicht.");
  }

  @Test
  void handleUnexpected_givenAnnotatedDomainExceptionWithoutMessage_thenUsesAnnotationReason() {
    // Given
    final Exception exception = new KontoNichtGefunden(null);

    // When
    final ProblemDetail problem = handler.handleUnexpected(exception);

    // Then
    assertThat(problem.getDetail()).isEqualTo("Konto nicht gefunden.");
  }

  @Test
  void handleUnexpected_givenSpringErrorResponse_thenKeepsItsStatus() {
    // Given
    final Exception exception =
        new ResponseStatusException(HttpStatus.NOT_FOUND, "Diesen Pfad gibt es nicht.");

    // When
    final ProblemDetail problem = handler.handleUnexpected(exception);

    // Then
    assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
  }

  @Test
  void handleUnexpected_givenSpringErrorResponse_thenKeepsItsDetail() {
    // Given
    final Exception exception =
        new ResponseStatusException(HttpStatus.NOT_FOUND, "Diesen Pfad gibt es nicht.");

    // When
    final ProblemDetail problem = handler.handleUnexpected(exception);

    // Then
    assertThat(problem.getDetail()).isEqualTo("Diesen Pfad gibt es nicht.");
  }

  @Test
  void handleUnexpected_givenUnannotatedException_thenReturns500() {
    // Given
    final Exception exception = new IllegalStateException("jdbc://geheim:passwort@host");

    // When
    final ProblemDetail problem = handler.handleUnexpected(exception);

    // Then
    assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
  }

  @Test
  void handleUnexpected_givenUnannotatedException_thenHidesTheCause() {
    // Given
    final Exception exception = new IllegalStateException("jdbc://geheim:passwort@host");

    // When
    final ProblemDetail problem = handler.handleUnexpected(exception);

    // Then
    assertThat(problem.getDetail()).isEqualTo(GlobalExceptionHandler.GENERIC_DETAIL);
  }

  private static MethodArgumentNotValidException validationException(final FieldError... errors)
      throws NoSuchMethodException {
    final BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "antrag");
    Arrays.stream(errors).forEach(bindingResult::addError);
    return new MethodArgumentNotValidException(
        new MethodParameter(String.class.getDeclaredMethod("length"), -1), bindingResult);
  }

  @ResponseStatus(code = HttpStatus.NOT_FOUND, reason = "Konto nicht gefunden.")
  private static final class KontoNichtGefunden extends RuntimeException {
    KontoNichtGefunden(final String message) {
      super(message);
    }
  }
}
