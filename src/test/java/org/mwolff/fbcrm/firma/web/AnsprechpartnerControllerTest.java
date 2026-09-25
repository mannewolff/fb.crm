package org.mwolff.fbcrm.firma.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.common.web.GlobalExceptionHandler;
import org.mwolff.fbcrm.firma.application.AnsprechpartnerAendernUseCase;
import org.mwolff.fbcrm.firma.application.AnsprechpartnerAnlegenUseCase;
import org.mwolff.fbcrm.firma.application.AnsprechpartnerDaten;
import org.mwolff.fbcrm.firma.application.AnsprechpartnerNichtGefunden;
import org.mwolff.fbcrm.firma.application.AnsprechpartnerStilllegenUseCase;
import org.mwolff.fbcrm.firma.domain.Ansprechpartner;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Die Uebersetzung zwischen Anwendungsfall und HTTP fuer den Ansprechpartner.
 *
 * <p>Zwei Blickwinkel in einer Klasse wie bei {@link FirmaControllerTest}: die Abbildung direkt an
 * den Methoden, die Faelle mit aussagekraeftiger Antwort<b>form</b> ueber eine schlanke
 * MockMvc-Strecke mit dem echten {@link GlobalExceptionHandler}.
 */
@ExtendWith(MockitoExtension.class)
class AnsprechpartnerControllerTest {

  private static final long FIRMA = 7L;
  private static final long ID = 3L;
  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");
  private static final String PFAD = "/api/firmen/7/ansprechpartner";

  @Mock private AnsprechpartnerAnlegenUseCase anlegen;
  @Mock private AnsprechpartnerAendernUseCase aendern;
  @Mock private AnsprechpartnerStilllegenUseCase stilllegen;

  private AnsprechpartnerController controller;
  private MockMvc mockMvc;

  @BeforeEach
  void baueDenController() {
    controller = new AnsprechpartnerController(anlegen, aendern, stilllegen);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  private static Ansprechpartner maxMueller() {
    return new Ansprechpartner(
        ID,
        FIRMA,
        "Max",
        "Mueller",
        "Einkauf",
        "max@firma.de",
        "0421 1234",
        "0170 1234",
        true,
        ANGELEGT,
        ANGELEGT);
  }

  private static AnsprechpartnerRequest anfrage(final String nachname) {
    return new AnsprechpartnerRequest(
        "Max", nachname, "Einkauf", "max@firma.de", "0421 1234", "0170 1234");
  }

  private static AnsprechpartnerDaten erwarteteDaten(final String nachname) {
    return new AnsprechpartnerDaten(
        "Max", nachname, "Einkauf", "max@firma.de", "0421 1234", "0170 1234");
  }

  private static String rumpf(final String nachname, final String email) {
    return "{\"vorname\":\"Max\",\"nachname\":\""
        + nachname
        + "\",\"rolle\":\"Einkauf\",\"email\":\""
        + email
        + "\",\"telefonFestnetz\":\"0421 1234\",\"telefonMobil\":\"0170 1234\"}";
  }

  @Test
  void anlegen_thenPassesThePathFirmaAndTheRequestToTheUseCase() {
    // Given
    when(anlegen.anlegen(FIRMA, erwarteteDaten("Mueller"))).thenReturn(maxMueller());

    // When
    controller.anlegen(FIRMA, anfrage("Mueller"));

    // Then — E7: die Firma kommt aus dem Pfad.
    verify(anlegen).anlegen(FIRMA, erwarteteDaten("Mueller"));
  }

  @Test
  void anlegen_thenAnswersWithTheNewContact() {
    // Given
    when(anlegen.anlegen(FIRMA, erwarteteDaten("Mueller"))).thenReturn(maxMueller());

    // When
    final AnsprechpartnerResponse antwort = controller.anlegen(FIRMA, anfrage("Mueller"));

    // Then
    assertThat(antwort)
        .isEqualTo(
            new AnsprechpartnerResponse(
                ID, "Max", "Mueller", "Einkauf", "max@firma.de", "0421 1234", "0170 1234", true));
  }

  @Test
  void anlegen_thenAnswersWithStatusCreated() throws Exception {
    // Given
    when(anlegen.anlegen(FIRMA, erwarteteDaten("Mueller"))).thenReturn(maxMueller());

    // When / Then
    mockMvc
        .perform(
            post(PFAD)
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpf("Mueller", "max@firma.de")))
        .andExpect(status().isCreated());
  }

  @Test
  void aendern_thenPassesFirmaIdAndIdAndTheRequestToTheUseCase() {
    // When
    controller.aendern(FIRMA, ID, anfrage("Maier"));

    // Then
    verify(aendern).aendern(FIRMA, ID, erwarteteDaten("Maier"));
  }

  @Test
  void stilllegen_thenPassesFirmaIdAndIdToTheUseCase() {
    // When
    controller.stilllegen(FIRMA, ID);

    // Then
    verify(stilllegen).stilllegen(FIRMA, ID);
  }

  @Test
  void aktivieren_thenPassesFirmaIdAndIdToTheUseCase() {
    // When
    controller.aktivieren(FIRMA, ID);

    // Then
    verify(stilllegen).aktivieren(FIRMA, ID);
  }

  @Test
  void anlegen_givenABlankLastName_thenAnswersBadRequest() throws Exception {
    // When / Then
    mockMvc
        .perform(
            post(PFAD)
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpf("   ", "max@firma.de")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void anlegen_givenABlankLastName_thenNamesTheFieldInTheProblemDetail() throws Exception {
    // When / Then — das Format stammt aus GlobalExceptionHandler; die Maske liest es aus.
    mockMvc
        .perform(
            post(PFAD)
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpf("   ", "max@firma.de")))
        .andExpect(jsonPath("$.fieldErrors.nachname").isArray());
  }

  @Test
  void anlegen_givenAMalformedEmail_thenNamesTheEmailFieldInTheProblemDetail() throws Exception {
    // When / Then — E8: „max@firma" ist keine Adresse, die dieses Projekt annimmt.
    mockMvc
        .perform(
            post(PFAD)
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpf("Mueller", "max@firma")))
        .andExpect(jsonPath("$.fieldErrors.email").isArray());
  }

  @Test
  void aendern_givenAnUnknownId_thenAnswersNotFound() throws Exception {
    // Given
    doThrow(new AnsprechpartnerNichtGefunden())
        .when(aendern)
        .aendern(FIRMA, ID, erwarteteDaten("Maier"));

    // When / Then
    mockMvc
        .perform(
            put(PFAD + "/3")
                .contentType(MediaType.APPLICATION_JSON)
                .content(rumpf("Maier", "max@firma.de")))
        .andExpect(status().isNotFound());
  }
}
