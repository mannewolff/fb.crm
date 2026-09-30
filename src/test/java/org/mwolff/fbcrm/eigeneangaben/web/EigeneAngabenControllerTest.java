package org.mwolff.fbcrm.eigeneangaben.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.common.web.GlobalExceptionHandler;
import org.mwolff.fbcrm.eigeneangaben.application.EigeneAngabenLesenUseCase;
import org.mwolff.fbcrm.eigeneangaben.application.EigeneAngabenPflegenUseCase;
import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngaben;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Die Uebersetzung zwischen Anwendungsfall und HTTP fuer die beiden Wege der Selbstauskunft.
 *
 * <p>Zwei Blickwinkel in einer Klasse, wie bei {@code FirmaControllerTest}: die Abbildung direkt an
 * den Methoden, und die Faelle, bei denen die <b>Form</b> der Antwort die Aussage ist (Statuscodes,
 * Feldfehler), ueber eine schlanke MockMvc-Strecke mit dem echten {@link GlobalExceptionHandler}.
 */
@ExtendWith(MockitoExtension.class)
class EigeneAngabenControllerTest {

  private static final String BERUFSBEZEICHNUNG = "Freiberuflicher Softwareentwickler";
  private static final String WEBADRESSE = "https://mwolff.org";

  @Mock private EigeneAngabenLesenUseCase lesen;
  @Mock private EigeneAngabenPflegenUseCase pflegen;

  @Captor private ArgumentCaptor<EigeneAngaben> eingereichte;

  private EigeneAngabenController controller;
  private MockMvc mockMvc;

  @BeforeEach
  void baueDenController() {
    controller = new EigeneAngabenController(lesen, pflegen);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  private static EigeneAngaben gepflegt() {
    return new EigeneAngaben(
        "Manfred Wolff",
        BERUFSBEZEICHNUNG,
        new Anschrift("Am Wall 1", "28195", "Bremen", "Deutschland"),
        "manne@example.org",
        "0421 1234",
        WEBADRESSE,
        "75/123/45678",
        "DE123456789",
        "DE02120300000000202051");
  }

  private static EigeneAngaben leer() {
    return new EigeneAngaben(
        null, null, new Anschrift(null, null, null, null), null, null, null, null, null, null);
  }

  private static String rumpf(final String name) {
    return feldRumpf("name", name);
  }

  private static String feldRumpf(final String feld, final String wert) {
    return "{\"" + feld + "\":\"" + wert + "\"}";
  }

  @Test
  void lesen_thenAnswersWithEveryValue() {
    // Given
    when(lesen.lese()).thenReturn(gepflegt());

    // When
    final EigeneAngabenResponse antwort = controller.lesen();

    // Then — die Anschrift steht flach, wie bei der Firma.
    assertThat(antwort)
        .isEqualTo(
            new EigeneAngabenResponse(
                "Manfred Wolff",
                BERUFSBEZEICHNUNG,
                "Am Wall 1",
                "28195",
                "Bremen",
                "Deutschland",
                "manne@example.org",
                "0421 1234",
                WEBADRESSE,
                "75/123/45678",
                "DE123456789",
                "DE02120300000000202051"));
  }

  @Test
  void lesen_givenTheFreshInstance_thenAnswersWithNullInEveryField() throws Exception {
    // Given — Kriterium 1: der Bereich ist von Anfang an da, nur noch ohne Inhalt.
    when(lesen.lese()).thenReturn(leer());

    // When / Then
    mockMvc
        .perform(get("/api/eigene-angaben"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").isEmpty())
        .andExpect(jsonPath("$.strasse").isEmpty())
        .andExpect(jsonPath("$.berufsbezeichnung").isEmpty())
        .andExpect(jsonPath("$.webadresse").isEmpty());
  }

  @Test
  void pflegen_thenPassesTheValuesOn() {
    // When
    controller.pflegen(
        new EigeneAngabenRequest(
            "Manfred Wolff",
            BERUFSBEZEICHNUNG,
            "Am Wall 1",
            "28195",
            "Bremen",
            "Deutschland",
            "manne@example.org",
            "0421 1234",
            WEBADRESSE,
            "75/123/45678",
            "DE123456789",
            "DE02120300000000202051"));

    // Then
    verify(pflegen).pflege(eingereichte.capture());
    assertThat(eingereichte.getValue()).isEqualTo(gepflegt());
  }

  @Test
  void pflegen_givenAnEmptyBody_thenPassesAbsentValuesOn() {
    // When — jede Fachangabe darf fehlen.
    controller.pflegen(
        new EigeneAngabenRequest(
            null, null, null, null, null, null, null, null, null, null, null, null));

    // Then
    verify(pflegen).pflege(eingereichte.capture());
    assertThat(eingereichte.getValue()).isEqualTo(leer());
  }

  @Test
  void pflegen_thenAnswersNoContent() throws Exception {
    // When / Then — die Oberflaeche liest den neuen Stand ueber GET.
    mockMvc
        .perform(
            put("/api/eigene-angaben")
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpf("Manfred Wolff")))
        .andExpect(status().isNoContent());
  }

  @Test
  void pflegen_givenAnOverlongField_thenAnswersWithTheFieldError() throws Exception {
    // When / Then — die Laengengrenze ist dieselbe wie im Schema.
    mockMvc
        .perform(
            put("/api/eigene-angaben")
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpf("N".repeat(201))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.name").isArray());
  }

  @Test
  void pflegen_givenAnOverlongJobTitle_thenAnswersWithTheFieldError() throws Exception {
    // When / Then — 200 Zeichen wie die Spalte (#160, Kriterium 29).
    mockMvc
        .perform(
            put("/api/eigene-angaben")
                .contentType(MediaType.APPLICATION_JSON)
                .content(feldRumpf("berufsbezeichnung", "B".repeat(201))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.berufsbezeichnung").isArray());
  }

  @Test
  void pflegen_givenAnOverlongWebAddress_thenAnswersWithTheFieldError() throws Exception {
    // When / Then — 200 Zeichen wie die Spalte (#160, Kriterium 29).
    mockMvc
        .perform(
            put("/api/eigene-angaben")
                .contentType(MediaType.APPLICATION_JSON)
                .content(feldRumpf("webadresse", "W".repeat(201))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.webadresse").isArray());
  }
}
