package org.mwolff.fbcrm.firma.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.common.web.GlobalExceptionHandler;
import org.mwolff.fbcrm.firma.application.FirmaAendernUseCase;
import org.mwolff.fbcrm.firma.application.FirmaAnlegenUseCase;
import org.mwolff.fbcrm.firma.application.FirmaDaten;
import org.mwolff.fbcrm.firma.application.FirmaLesenUseCase;
import org.mwolff.fbcrm.firma.application.FirmaMitAnsprechpartnern;
import org.mwolff.fbcrm.firma.application.FirmaNichtGefunden;
import org.mwolff.fbcrm.firma.application.FirmaStilllegenUseCase;
import org.mwolff.fbcrm.firma.application.FirmaZeile;
import org.mwolff.fbcrm.firma.application.FirmenUebersicht;
import org.mwolff.fbcrm.firma.application.FirmenUebersichtUseCase;
import org.mwolff.fbcrm.firma.domain.Anschrift;
import org.mwolff.fbcrm.firma.domain.Ansprechpartner;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Die Uebersetzung zwischen Anwendungsfall und HTTP fuer die Firma.
 *
 * <p>Zwei Blickwinkel in einer Klasse: Die Abbildung von Anfrage und Antwort wird direkt an den
 * Methoden geprueft — schnell und ohne Rahmen. Die beiden Faelle, bei denen die <b>Form</b> der
 * Antwort die Aussage ist (Feldfehler und unbekannte Kennung), laufen durch eine schlanke
 * MockMvc-Strecke mit dem echten {@link GlobalExceptionHandler}: Nur dort zeigt sich, dass ein
 * Validierungsfehler wirklich als {@code fieldErrors} beim Feld {@code name} ankommt.
 */
@ExtendWith(MockitoExtension.class)
class FirmaControllerTest {

  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");

  @Mock private FirmenUebersichtUseCase uebersicht;
  @Mock private FirmaLesenUseCase lesen;
  @Mock private FirmaAnlegenUseCase anlegen;
  @Mock private FirmaAendernUseCase aendern;
  @Mock private FirmaStilllegenUseCase stilllegen;

  private FirmaController controller;
  private MockMvc mockMvc;

  @BeforeEach
  void baueDenController() {
    controller = new FirmaController(uebersicht, lesen, anlegen, aendern, stilllegen);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  private static Firma adlerAg(final boolean aktiv) {
    return new Firma(
        7L,
        "Adler AG",
        new Anschrift("Am Wall 1", "28195", "Bremen", "Deutschland"),
        "75/123/45678",
        "DE123456789",
        aktiv,
        ANGELEGT,
        ANGELEGT);
  }

  private static Ansprechpartner maxMueller(final boolean aktiv) {
    return new Ansprechpartner(
        3L,
        7L,
        "Max",
        "Mueller",
        "Einkauf",
        "max@firma.de",
        "0421 1234",
        "0170 1234",
        aktiv,
        ANGELEGT,
        ANGELEGT);
  }

  private static FirmaRequest anfrage(final String name) {
    return new FirmaRequest(
        name, "Am Wall 1", "28195", "Bremen", "Deutschland", "75/123/45678", "DE123456789");
  }

  private static FirmaDaten erwarteteDaten(final String name) {
    return new FirmaDaten(
        name,
        new Anschrift("Am Wall 1", "28195", "Bremen", "Deutschland"),
        "75/123/45678",
        "DE123456789");
  }

  @Test
  void uebersicht_thenAnswersWithRowsAndTheTotal() {
    // Given
    when(uebersicht.uebersicht("adler", true))
        .thenReturn(
            new FirmenUebersicht(List.of(new FirmaZeile(7L, "Adler AG", "Bremen", 2L, false)), 9L));

    // When
    final FirmenUebersichtResponse antwort = controller.uebersicht("adler", true);

    // Then
    assertThat(antwort)
        .isEqualTo(
            new FirmenUebersichtResponse(
                List.of(new FirmaZeileResponse(7L, "Adler AG", "Bremen", 2L, false)), 9L));
  }

  @Test
  void uebersicht_givenNoParameters_thenSearchesForEverythingWithoutRetiredFirmen()
      throws Exception {
    // Given — die Standardwerte stehen an der Schnittstelle, nicht im Anwendungsfall.
    when(uebersicht.uebersicht("", false)).thenReturn(new FirmenUebersicht(List.of(), 0L));

    // When
    mockMvc.perform(get("/api/firmen")).andExpect(status().isOk());

    // Then
    verify(uebersicht).uebersicht("", false);
  }

  @Test
  void lesen_thenAnswersWithTheFirmaAndItsContacts() {
    // Given
    when(lesen.lese(7L))
        .thenReturn(new FirmaMitAnsprechpartnern(adlerAg(true), List.of(maxMueller(false))));

    // When
    final FirmaResponse antwort = controller.lesen(7L);

    // Then
    assertThat(antwort)
        .isEqualTo(
            new FirmaResponse(
                7L,
                "Adler AG",
                "Am Wall 1",
                "28195",
                "Bremen",
                "Deutschland",
                "75/123/45678",
                "DE123456789",
                true,
                List.of(
                    new AnsprechpartnerResponse(
                        3L,
                        "Max",
                        "Mueller",
                        "Einkauf",
                        "max@firma.de",
                        "0421 1234",
                        "0170 1234",
                        false))));
  }

  @Test
  void anlegen_thenPassesTheTrimmedRequestToTheUseCase() {
    // Given
    when(anlegen.anlegen(erwarteteDaten("Adler AG"))).thenReturn(adlerAg(true));

    // When
    controller.anlegen(anfrage("Adler AG"));

    // Then
    verify(anlegen).anlegen(erwarteteDaten("Adler AG"));
  }

  @Test
  void anlegen_thenAnswersWithTheNewFirmaAndNoContactsYet() {
    // Given
    when(anlegen.anlegen(erwarteteDaten("Adler AG"))).thenReturn(adlerAg(true));

    // When
    final FirmaResponse antwort = controller.anlegen(anfrage("Adler AG"));

    // Then — Kriterium 7: die Kennung fuehrt die Oberflaeche zur Detailansicht.
    assertThat(antwort)
        .extracting(FirmaResponse::id, FirmaResponse::ansprechpartner)
        .containsExactly(7L, List.of());
  }

  @Test
  void anlegen_thenAnswersWithStatusCreated() throws Exception {
    // Given
    when(anlegen.anlegen(erwarteteDaten("Adler AG"))).thenReturn(adlerAg(true));

    // When / Then
    mockMvc
        .perform(
            post("/api/firmen").contentType(MediaType.APPLICATION_JSON).content(rumpf("Adler AG")))
        .andExpect(status().isCreated());
  }

  @Test
  void aendern_thenPassesIdAndRequestToTheUseCase() {
    // When
    controller.aendern(7L, anfrage("Adler GmbH"));

    // Then
    verify(aendern).aendern(7L, erwarteteDaten("Adler GmbH"));
  }

  @Test
  void stilllegen_thenPassesTheIdToTheUseCase() {
    // When
    controller.stilllegen(7L);

    // Then
    verify(stilllegen).stilllegen(7L);
  }

  @Test
  void aktivieren_thenPassesTheIdToTheUseCase() {
    // When
    controller.aktivieren(7L);

    // Then
    verify(stilllegen).aktivieren(7L);
  }

  @Test
  void anlegen_givenABlankName_thenAnswersBadRequest() throws Exception {
    // When / Then
    mockMvc
        .perform(post("/api/firmen").contentType(MediaType.APPLICATION_JSON).content(rumpf("   ")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void anlegen_givenABlankName_thenNamesTheFieldInTheProblemDetail() throws Exception {
    // When / Then — das Format stammt aus GlobalExceptionHandler; die Maske liest es aus.
    mockMvc
        .perform(post("/api/firmen").contentType(MediaType.APPLICATION_JSON).content(rumpf("   ")))
        .andExpect(jsonPath("$.fieldErrors.name").isArray());
  }

  @Test
  void lesen_givenAnUnknownId_thenAnswersNotFound() throws Exception {
    // Given
    when(lesen.lese(4711L)).thenThrow(new FirmaNichtGefunden());

    // When / Then
    mockMvc.perform(get("/api/firmen/4711")).andExpect(status().isNotFound());
  }

  private static String rumpf(final String name) {
    return "{\"name\":\""
        + name
        + "\",\"strasse\":\"Am Wall 1\",\"plz\":\"28195\","
        + "\"ort\":\"Bremen\",\"land\":\"Deutschland\",\"steuernummer\":\"75/123/45678\","
        + "\"umsatzsteuerId\":\"DE123456789\"}";
  }
}
