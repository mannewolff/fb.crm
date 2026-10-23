package org.mwolff.fbcrm.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.common.Feldfehler;
import org.mwolff.fbcrm.common.Uploadgrenze;
import org.mwolff.fbcrm.common.ZuVieleVersuche;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
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
  void handleTypeMismatch_thenAnswers400WithoutEchoingTheValue() {
    // Given — ein Anfrageparameter, der sich nicht in seinen Typ wandeln laesst, etwa ein
    // unbekannter Status.
    final TypeMismatchException exception = new TypeMismatchException("<script>", Integer.class);

    // When
    final ProblemDetail problem = handler.handleTypeMismatch(exception);

    // Then
    assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(problem.getDetail()).isEqualTo(GlobalExceptionHandler.UNGUELTIGER_PARAMETER);
  }

  @Test
  void handleUnreadableBody_thenAnswers400WithoutEchoingJacksonsMessage() {
    // Given — fehlerhaftes JSON oder ein Wert, der nicht in sein Feld passt (Issue #253).
    final HttpMessageNotReadableException exception =
        new HttpMessageNotReadableException(
            "JSON parse error: not one of the values accepted for Enum class",
            new MockHttpInputMessage(new byte[0]));

    // When
    final ProblemDetail problem = handler.handleUnreadableBody(exception);

    // Then — 400 und nicht 500, und die Meldung von Jackson bleibt drinnen.
    assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(problem.getDetail()).isEqualTo(GlobalExceptionHandler.UNLESBARER_RUMPF);
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

  @Test
  void handleFeldfehler_thenTakesTheStatusOfTheAnnotatedException() {
    // Given — E22 des Angebot-Plans: ein fachlicher Fehler, der einzelne Felder benennt.

    // When
    final ProblemDetail problem = handler.handleFeldfehler(new AngabenFehlen());

    // Then
    assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
  }

  @Test
  void handleFeldfehler_thenCarriesTheFieldsAsFieldErrors() {
    // Given — E22: dieselbe Erweiterung wie bei der Bean Validation, damit die Oberflaeche die
    // Meldungen auf beiden Wegen gleich liest.

    // When
    final ProblemDetail problem = handler.handleFeldfehler(new AngabenFehlen());

    // Then
    assertThat(problem.getProperties())
        .containsEntry("fieldErrors", Map.of("positionen", List.of("fehlt")));
  }

  @Test
  void handleFeldfehler_thenDetailComesFromTheResponseStatusReason() {
    // Given — ohne eigene Meldung gilt der Grund der Annotation.

    // When
    final ProblemDetail problem = handler.handleFeldfehler(new AngabenFehlen());

    // Then
    assertThat(problem.getDetail()).isEqualTo("Es fehlen Angaben.");
  }

  @Test
  void handleMaxUploadSize_thenAnswersPayloadTooLarge() {
    // Given — Plan #150, E8: der Riegel des Containers greift, bevor ein Anwendungsfall zum Zuge
    // kommt. Ohne diesen Zweig faellt die Ausnahme in handleUnexpected und kaeme als 500 zurueck.

    // When
    final ProblemDetail problem =
        handler.handleMaxUploadSize(new MaxUploadSizeExceededException(Uploadgrenze.MAX_BYTE));

    // Then
    assertThat(problem.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE.value());
  }

  @Test
  void handleMaxUploadSize_thenDetailIsTheSameSentenceTheUseCaseWouldHaveGiven() {
    // Given — dieselbe Zahl und derselbe Satz wie am Feld datei (Uploadgrenze): Zwei Grenzen mit
    // zwei Meldungen liefen auseinander.

    // When
    final ProblemDetail problem =
        handler.handleMaxUploadSize(new MaxUploadSizeExceededException(Uploadgrenze.MAX_BYTE));

    // Then
    assertThat(problem.getDetail()).isEqualTo(Uploadgrenze.MELDUNG);
  }

  @Test
  void handleAccessDenied_thenAnswersForbidden() {
    // Given — Plan #414, A9: kommt die Ausnahme aus einem Anwendungsfall hoch, machte
    // handleUnexpected ohne diesen Zweig einen 500 daraus.

    // When
    final ProblemDetail problem =
        handler.handleAccessDenied(new AccessDeniedException("Zugriff verweigert"));

    // Then
    assertThat(problem.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
  }

  @Test
  void handleAccessDenied_thenDetailIsTheOneSentenceForAForbiddenStep() {
    // Given — derselbe Satz wie in VerbotenAntwort; die Meldung der Ausnahme bleibt drinnen.

    // When
    final ProblemDetail problem =
        handler.handleAccessDenied(new AccessDeniedException("interne Begruendung"));

    // Then
    assertThat(problem.getDetail()).isEqualTo(GlobalExceptionHandler.VERBOTEN_DETAIL);
  }

  @Test
  void verbotenDetail_thenDoesNotNameARole() {
    // Given — A9: der Satz nennt die Rolle nicht beim Namen.

    // When
    final String satz = GlobalExceptionHandler.VERBOTEN_DETAIL;

    // Then
    assertThat(satz)
        .isNotBlank()
        .doesNotContainIgnoringCase("C-Level")
        .doesNotContainIgnoringCase("C_LEVEL")
        .doesNotContainIgnoringCase("Employee")
        .doesNotContainIgnoringCase("ReadOnly")
        .doesNotContainIgnoringCase("READ_ONLY");
  }

  @Test
  void handleZuVieleVersuche_thenAnswersTooManyRequests() {
    // Given — E14: Status und Text kommen nicht mehr aus einem festen @ResponseStatus.

    // When
    final ResponseEntity<ProblemDetail> antwort =
        handler.handleZuVieleVersuche(new Gebremst(Duration.ofMinutes(12)));

    // Then
    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(antwort.getBody()).extracting(ProblemDetail::getStatus).isEqualTo(429);
  }

  @Test
  void handleZuVieleVersuche_thenRetryAfterCarriesTheWaitInSeconds() {
    // When
    final ResponseEntity<ProblemDetail> antwort =
        handler.handleZuVieleVersuche(new Gebremst(Duration.ofMinutes(12)));

    // Then
    assertThat(antwort.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("720");
  }

  @Test
  void handleZuVieleVersuche_thenTheMessageNamesTheWaitInWholeMinutes() {
    // When
    final ResponseEntity<ProblemDetail> antwort =
        handler.handleZuVieleVersuche(new Gebremst(Duration.ofMinutes(12)));

    // Then — Kriterium 5: die Meldung nennt die Wartezeit.
    assertThat(antwort.getBody())
        .extracting(ProblemDetail::getDetail)
        .isEqualTo("Zu viele Versuche. Bitte in 12 Minuten erneut versuchen.");
  }

  @Test
  void handleZuVieleVersuche_givenOneMinute_thenTheMessageSpeaksOfOneMinute() {
    // When
    final ResponseEntity<ProblemDetail> antwort =
        handler.handleZuVieleVersuche(new Gebremst(Duration.ofMinutes(1)));

    // Then
    assertThat(antwort.getBody())
        .extracting(ProblemDetail::getDetail)
        .isEqualTo("Zu viele Versuche. Bitte in einer Minute erneut versuchen.");
    assertThat(antwort.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("60");
  }

  @Test
  void handleZuVieleVersuche_givenTheSameWaitFromAnotherCounter_thenTheSameAnswer() {
    // Given — Kriterium 5: fuer jede Adresse und jede Zaehlart dieselbe Meldung. Die Ausnahme
    // traegt nichts als die Wartezeit, und darum kann die Antwort auch nichts anderes nennen.
    final Gebremst herkunft = new Gebremst(Duration.ofMinutes(15));
    final Gebremst adresse = new AdresseGebremst(Duration.ofMinutes(15));

    // When
    final ResponseEntity<ProblemDetail> ersteAntwort = handler.handleZuVieleVersuche(herkunft);
    final ResponseEntity<ProblemDetail> zweiteAntwort = handler.handleZuVieleVersuche(adresse);

    // Then
    assertThat(zweiteAntwort.getBody()).isEqualTo(ersteAntwort.getBody());
    assertThat(zweiteAntwort.getHeaders()).isEqualTo(ersteAntwort.getHeaders());
  }

  @Test
  void handleZuVieleVersuche_thenTheMessageNamesNeitherTheCounterNorTheAddress() {
    // When
    final ResponseEntity<ProblemDetail> antwort =
        handler.handleZuVieleVersuche(new AdresseGebremst(Duration.ofMinutes(60)));

    // Then
    assertThat(antwort.getBody())
        .extracting(ProblemDetail::getDetail)
        .asString()
        .contains("60 Minuten")
        .doesNotContainIgnoringCase("adresse")
        .doesNotContainIgnoringCase("herkunft")
        .doesNotContainIgnoringCase("stunde")
        .doesNotContain("@");
    assertThat(antwort.getBody().getProperties()).isNull();
  }

  private static MethodArgumentNotValidException validationException(final FieldError... errors)
      throws NoSuchMethodException {
    final BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "antrag");
    Arrays.stream(errors).forEach(bindingResult::addError);
    return new MethodArgumentNotValidException(
        new MethodParameter(String.class.getDeclaredMethod("length"), -1), bindingResult);
  }

  /*
   * Ein Feldfehler aus einem beliebigen Fachmodul — hier nachgebildet, damit dieser Test kein
   * Fachmodul importieren muss: Die Abbildung kennt nur den Vertrag aus common.Feldfehler, und
   * common darf kein Fachmodul kennen (ArchitectureTest, modules_thenFreeOfCycles).
   */
  @ResponseStatus(code = HttpStatus.CONFLICT, reason = "Es fehlen Angaben.")
  private static final class AngabenFehlen extends Feldfehler {
    @Override
    public Map<String, List<String>> felder() {
      return Map.of("positionen", List.of("fehlt"));
    }
  }

  @ResponseStatus(code = HttpStatus.NOT_FOUND, reason = "Konto nicht gefunden.")
  private static final class KontoNichtGefunden extends RuntimeException {
    KontoNichtGefunden(final String message) {
      super(message);
    }
  }

  /*
   * Eine gebremste Lage aus einem beliebigen Fachmodul — nachgebildet aus demselben Grund wie
   * AngabenFehlen: common darf kein Fachmodul kennen.
   */
  private static class Gebremst extends ZuVieleVersuche {
    Gebremst(final Duration restwartezeit) {
      super(restwartezeit);
    }
  }

  /** Eine zweite Zaehlart, die nichts als ihren Namen anders hat. */
  private static final class AdresseGebremst extends Gebremst {
    AdresseGebremst(final Duration restwartezeit) {
      super(restwartezeit);
    }

    @Override
    public String getMessage() {
      return "Adresse manne@example.org: Stundengrenze";
    }
  }
}
