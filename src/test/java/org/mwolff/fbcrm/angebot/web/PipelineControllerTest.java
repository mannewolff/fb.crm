package org.mwolff.fbcrm.angebot.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.application.Pipeline;
import org.mwolff.fbcrm.angebot.application.PipelineUseCase;
import org.mwolff.fbcrm.angebot.application.PipelineZeile;
import org.mwolff.fbcrm.common.web.GlobalExceptionHandler;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Die Uebersetzung zwischen Anwendungsfall und HTTP fuer die Pipeline (Kriterien 23, 24).
 *
 * <p>Zwei Blickwinkel in einer Klasse, wie bei {@code VorgangControllerTest}: Die Abbildung wird
 * direkt an der Methode geprueft, und die Faelle, bei denen die <b>Form</b> der Antwort die Aussage
 * ist, laufen durch eine schlanke MockMvc-Strecke. Der Gegenstand dort ist {@code
 * wahrscheinlichkeit = null}: Dieses Feld <b>ist</b> die Kennzeichnung „nicht eingeschaetzt" (F2),
 * und es muss im JSON stehen bleiben — verschwindet es, hat die Oberflaeche keine Kennzeichnung
 * mehr.
 */
@ExtendWith(MockitoExtension.class)
class PipelineControllerTest {

  private static final LocalDate ENTSCHEIDUNG = LocalDate.of(2026, 10, 15);
  private static final BigDecimal NULL = new BigDecimal("0.00");

  @Mock private PipelineUseCase useCase;

  private PipelineController controller;
  private MockMvc mockMvc;

  @BeforeEach
  void baueDenController() {
    controller = new PipelineController(useCase);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  /*
   * Die Summen kommen hier als Vorgabe und nicht aus den Zeilen gerechnet: Sie sind die Antwort des
   * Anwendungsfalls, und der Controller reicht sie weiter, statt sie ein zweites Mal zu bilden.
   */
  private static Pipeline pipeline(final List<PipelineZeile> zeilen) {
    return new Pipeline(zeilen, zeilen.getFirst().summe(), zeilen.getFirst().gewichteteSumme());
  }

  private static PipelineZeile zeile(
      final Integer wahrscheinlichkeit,
      final String gewichteteSumme,
      final LocalDate entscheidungErwartetAm) {
    return new PipelineZeile(
        11L,
        "A-2026-011",
        3L,
        13L,
        "Website-Relaunch",
        "Adler AG",
        new BigDecimal("1000.00"),
        wahrscheinlichkeit,
        new BigDecimal(gewichteteSumme),
        entscheidungErwartetAm);
  }

  @Test
  void pipeline_thenAnswersWithBothTotalsAndEveryFieldOfTheRow() {
    // Given — Kriterien 23, 24.
    when(useCase.pipeline())
        .thenReturn(pipeline(List.of(zeile(Integer.valueOf(60), "600.00", ENTSCHEIDUNG))));

    // When
    final PipelineResponse antwort = controller.pipeline();

    // Then
    assertThat(antwort)
        .isEqualTo(
            new PipelineResponse(
                List.of(
                    new PipelineZeileResponse(
                        11L,
                        "A-2026-011",
                        3L,
                        13L,
                        "Website-Relaunch",
                        "Adler AG",
                        new BigDecimal("1000.00"),
                        Integer.valueOf(60),
                        new BigDecimal("600.00"),
                        ENTSCHEIDUNG)),
                new BigDecimal("1000.00"),
                new BigDecimal("600.00")));
  }

  @Test
  void pipeline_thenCarriesEveryFieldUnderTheAgreedName() throws Exception {
    // Given — die Feldnamen sind im Plan festgelegt, damit Parser und Record zusammen entstehen.
    when(useCase.pipeline())
        .thenReturn(pipeline(List.of(zeile(Integer.valueOf(60), "600.00", ENTSCHEIDUNG))));

    // When / Then
    mockMvc
        .perform(get("/api/pipeline"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.summe").value(1000.00))
        .andExpect(jsonPath("$.gewichteteSumme").value(600.00))
        .andExpect(jsonPath("$.zeilen[0].angebotId").value(11))
        .andExpect(jsonPath("$.zeilen[0].nummer").value("A-2026-011"))
        .andExpect(jsonPath("$.zeilen[0].vorgangId").value(3))
        .andExpect(jsonPath("$.zeilen[0].vorgangNummer").value(13))
        .andExpect(jsonPath("$.zeilen[0].vorgangTitel").value("Website-Relaunch"))
        .andExpect(jsonPath("$.zeilen[0].firma").value("Adler AG"))
        .andExpect(jsonPath("$.zeilen[0].summe").value(1000.00))
        .andExpect(jsonPath("$.zeilen[0].wahrscheinlichkeit").value(60))
        .andExpect(jsonPath("$.zeilen[0].gewichteteSumme").value(600.00))
        // Nur die Anwesenheit: Die schlanke MockMvc-Strecke traegt nicht die Zeitmodule des
        // laufenden Servers und schreibt ein Datum als Feld-Array. Dass es als ISO-Datum
        // hinausgeht,
        // weist PipelineIT gegen den echten Kontext nach.
        .andExpect(jsonPath("$.zeilen[0].entscheidungErwartetAm").exists());
  }

  @Test
  void pipeline_givenAnUnestimatedVorgang_thenTheProbabilityStaysNullInTheJson() {
    // Given — F2: das Feld ist die Kennzeichnung, und es wird nicht weggelassen.
    when(useCase.pipeline()).thenReturn(pipeline(List.of(zeile(null, "500.00", null))));

    // When
    final PipelineResponse antwort = controller.pipeline();

    // Then
    assertThat(antwort.zeilen())
        .singleElement()
        .satisfies(
            zeile -> assertThat(zeile.wahrscheinlichkeit()).isNull(),
            zeile -> assertThat(zeile.entscheidungErwartetAm()).isNull());
  }

  @Test
  void pipeline_givenAnUnestimatedVorgang_thenTheJsonKeepsBothFieldsAsNull() throws Exception {
    // Given — verschwindet das Feld, hat die Oberflaeche keine Kennzeichnung mehr.
    when(useCase.pipeline()).thenReturn(pipeline(List.of(zeile(null, "500.00", null))));

    // When / Then
    mockMvc
        .perform(get("/api/pipeline"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.zeilen[0].wahrscheinlichkeit").isEmpty())
        .andExpect(jsonPath("$.zeilen[0]").value(hasKey("wahrscheinlichkeit")))
        .andExpect(jsonPath("$.zeilen[0].entscheidungErwartetAm").isEmpty());
  }

  @Test
  void pipeline_givenAnEmptyPipeline_thenTwoZeroTotalsAndNoRow() throws Exception {
    // Given — daran erkennt die Oberflaeche „keine offene Chance".
    when(useCase.pipeline()).thenReturn(new Pipeline(List.of(), NULL, NULL));

    // When / Then
    mockMvc
        .perform(get("/api/pipeline"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.zeilen").isEmpty())
        .andExpect(jsonPath("$.summe").value(0.00))
        .andExpect(jsonPath("$.gewichteteSumme").value(0.00));
  }
}
