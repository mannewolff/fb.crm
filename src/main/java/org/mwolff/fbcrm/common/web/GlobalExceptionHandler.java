package org.mwolff.fbcrm.common.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Feldfehler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Die einzige Stelle, an der Fehler auf HTTP-Antworten abgebildet werden (CLAUDE-java.md §6.3).
 *
 * <p>Ausgegeben wird durchgaengig RFC-9457 Problem Details. Bean-Validation-Fehler und fachliche
 * {@link Feldfehler} bekommen die Erweiterung {@code fieldErrors}; alles Unerwartete wird als
 * generischer 500 beantwortet, damit weder Stacktrace noch interne Meldung nach aussen gelangt
 * (CLAUDE-security.md).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  /** Antworttext fuer alles, was nicht fachlich vorgesehen ist. */
  public static final String GENERIC_DETAIL = "Ein unerwarteter Fehler ist aufgetreten.";

  /** Ersatztext, wenn eine Feldverletzung keine eigene Meldung mitbringt. */
  public static final String FALLBACK_FIELD_MESSAGE = "ist ungueltig";

  private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleBeanValidation(final MethodArgumentNotValidException exception) {
    final Map<String, List<String>> fieldErrors =
        exception.getFieldErrors().stream()
            .collect(
                Collectors.groupingBy(
                    FieldError::getField,
                    LinkedHashMap::new,
                    Collectors.mapping(GlobalExceptionHandler::messageOf, Collectors.toList())));

    final ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Die Eingabe ist ungueltig.");
    problem.setTitle("Ungueltige Eingabe");
    problem.setProperty("fieldErrors", fieldErrors);
    return problem;
  }

  /**
   * Ein fachlicher Fehler, der die betroffenen Felder nennt (E22 des Angebot-Plans).
   *
   * <p>Bisher entstand {@code fieldErrors} allein aus der Bean Validation und damit immer mit 400,
   * waehrend ein {@code @ResponseStatus}-Fehler nur {@code detail} trug. Manche fachliche Pruefung
   * nennt aber ebenfalls Felder — die Versandpruefung des Angebots zaehlt alle fehlenden Angaben
   * auf einmal auf —, und ohne diesen Zweig muesste die Oberflaeche zwei Formen fuer dieselbe
   * Aussage lesen.
   *
   * <p>Statuscode und Text kommen aus demselben Weg wie bei jeder anderen fachlichen Ausnahme:
   * {@link #handleUnexpected} liest die {@code @ResponseStatus}-Annotation. Hier kommt nur die
   * Feldliste dazu — zwei Abbildungen fuer einen Statuscode liefen auseinander.
   *
   * <p>Der Zweig nennt bewusst {@link Feldfehler} und keine Ausnahme eines Fachmoduls: Sonst zeigte
   * {@code common} auf das Fachmodul und das Fachmodul auf {@code common} ({@code
   * ArchitectureTest}, {@code modules_thenFreeOfCycles}).
   */
  @ExceptionHandler(Feldfehler.class)
  public ProblemDetail handleFeldfehler(final Feldfehler exception) {
    final ProblemDetail problem = handleUnexpected(exception);
    problem.setProperty("fieldErrors", exception.felder());
    return problem;
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUnexpected(final Exception exception) {
    // Spring meldet eigene Lagen (404, 405, 415, …) ueber ErrorResponse und bringt den
    // passenden Statuscode mit. Ohne diesen Zweig machte der generische 500 unten aus jedem
    // unbekannten Pfad einen Serverfehler.
    if (exception instanceof final ErrorResponse springEigen) {
      return springEigen.getBody();
    }
    final ResponseStatus status =
        AnnotatedElementUtils.findMergedAnnotation(exception.getClass(), ResponseStatus.class);
    if (status == null) {
      LOG.error("Unerwarteter Fehler beim Bearbeiten einer Anfrage", exception);
      return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, GENERIC_DETAIL);
    }
    return ProblemDetail.forStatusAndDetail(status.code(), detailOf(exception, status));
  }

  private static String messageOf(final FieldError error) {
    final @Nullable String message = error.getDefaultMessage();
    return message == null ? FALLBACK_FIELD_MESSAGE : message;
  }

  private static String detailOf(final Exception exception, final ResponseStatus status) {
    final @Nullable String message = exception.getMessage();
    return message == null ? status.reason() : message;
  }
}
